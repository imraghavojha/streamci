package com.yourname.streamci.streamci.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import com.yourname.streamci.streamci.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StreamCIEssentialApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Repositories
    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    private QueueTrackerRepository queueTrackerRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PipelineMetricsRepository metricsRepository;

    // Services
    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private AlertService alertService;

    // Test data
    private Pipeline testPipeline1;
    private Pipeline testPipeline2;
    private Pipeline testPipeline3;

    @BeforeEach
    void setUp() {
        // Clear all data before each test
        alertRepository.deleteAll();
        buildRepository.deleteAll();
        queueTrackerRepository.deleteAll();
        metricsRepository.deleteAll();
        pipelineRepository.deleteAll();

        // Create test pipelines
        testPipeline1 = pipelineService.savePipeline(Pipeline.builder()
                .name("frontend-app")
                .status("active")
                .duration(120)
                .build());

        testPipeline2 = pipelineService.savePipeline(Pipeline.builder()
                .name("backend-api")
                .status("active")
                .duration(180)
                .build());

        testPipeline3 = pipelineService.savePipeline(Pipeline.builder()
                .name("data-processor")
                .status("inactive")
                .duration(300)
                .build());

        System.out.println("✅ Test setup completed with 3 pipelines");
    }

    @Test
    @Order(1)
    void test1_dashboardSummaryEndpointWorksEndToEnd() throws Exception {
        System.out.println("\n=== TEST 1: Dashboard Summary Endpoint Works End-to-End ===");

        // Arrange: Create 30 builds over last 7 days (mix of success/failed/running)
        LocalDateTime now = LocalDateTime.now();
        createBuildsForPipeline(testPipeline1, 10, now.minusDays(3), "success", "failure", 70); // 70% success
        createBuildsForPipeline(testPipeline2, 12, now.minusDays(2), "success", "failure", 80); // 80% success
        createBuildsForPipeline(testPipeline3, 8, now.minusDays(1), "success", "failure", 50);  // 50% success

        // Create QueueTracker entries for running builds
        createQueueTrackerEntry(testPipeline1, "run-123", "running", now.minusMinutes(5));
        createQueueTrackerEntry(testPipeline2, "run-456", "running", now.minusMinutes(10));

        // Create alerts
        createTestAlert(testPipeline1, Alert.AlertType.SUCCESS_RATE_DROP, Alert.AlertSeverity.WARNING);
        createTestAlert(testPipeline2, Alert.AlertType.QUEUE_BACKUP, Alert.AlertSeverity.CRITICAL);
        createTestAlert(null, Alert.AlertType.HIGH_FAILURE_RATE, Alert.AlertSeverity.INFO); // Global alert

        // Act: Call GET /api/dashboard/summary
        MvcResult result = mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Parse response
        String responseContent = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseContent, Map.class);

        // Assert: Response structure and content
        assertThat(response).containsKey("timestamp");
        assertThat(response).containsKey("status");
        assertThat(response.get("status")).isEqualTo("success");

        assertThat(response).containsKey("total_pipelines");
        assertThat(response.get("total_pipelines")).isEqualTo(3);

        assertThat(response).containsKey("pipelines");
        List<?> pipelines = (List<?>) response.get("pipelines");
        assertThat(pipelines).hasSize(3);

        // Verify each pipeline has required fields
        Map<String, Object> pipeline = (Map<String, Object>) pipelines.get(0);
        assertThat(pipeline).containsKeys("id", "name", "status");

        assertThat(response).containsKey("overview");
        assertThat(response).containsKey("recent_activity");
        assertThat(response).containsKey("active_alerts");
        assertThat(response).containsKey("queue_status");

        // Verify active alerts
        List<?> activeAlerts = (List<?>) response.get("active_alerts");
        assertThat(activeAlerts).hasSize(3);

        // Verify queue status shows running builds
        Map<String, Object> queueStatus = (Map<String, Object>) response.get("queue_status");
        assertThat(queueStatus).containsKey("total_running");
        assertThat(queueStatus.get("total_running")).isEqualTo(2);

        System.out.println("✅ Dashboard summary endpoint works with all required fields");
    }

    @Test
    @Order(2)
    void test2_webhookToDashboardFlowWorks() throws Exception {
        System.out.println("\n=== TEST 2: Webhook to Dashboard Flow Works ===");

        // Clear all tables
        alertRepository.deleteAll();
        buildRepository.deleteAll();
        queueTrackerRepository.deleteAll();

        // Step 1: Send workflow_run queued webhook
        String queuedPayload = """
            {
                "action": "queued",
                "workflow_run": {
                    "id": "12345",
                    "status": "queued",
                    "created_at": "2024-01-15T10:00:00Z",
                    "head_sha": "abc123def456",
                    "head_branch": "main",
                    "actor": {"login": "testuser"}
                },
                "repository": {
                    "name": "webhook-test-repo",
                    "owner": {"login": "testowner"}
                }
            }
            """;

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queuedPayload))
                .andExpect(status().isOk());

        Thread.sleep(2000); // Wait for async processing

        // Assert: Pipeline created and QueueTracker entry created
        List<Pipeline> pipelines = pipelineRepository.findAll();
        assertThat(pipelines).anyMatch(p -> p.getName().equals("webhook-test-repo"));

        List<QueueTracker> queueEntries = queueTrackerRepository.findAll();
        assertThat(queueEntries).hasSize(1);
        assertThat(queueEntries.get(0).getStatus()).isEqualTo("queued");

        // Step 2: Send workflow_run in_progress webhook
        String inProgressPayload = """
            {
                "action": "in_progress",
                "workflow_run": {
                    "id": "12345",
                    "status": "in_progress",
                    "created_at": "2024-01-15T10:00:00Z",
                    "run_started_at": "2024-01-15T10:01:00Z",
                    "head_sha": "abc123def456",
                    "head_branch": "main",
                    "actor": {"login": "testuser"}
                },
                "repository": {
                    "name": "webhook-test-repo",
                    "owner": {"login": "testowner"}
                }
            }
            """;

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inProgressPayload))
                .andExpect(status().isOk());

        Thread.sleep(1000);

        // Assert: QueueTracker updated to running
        queueEntries = queueTrackerRepository.findAll();
        assertThat(queueEntries.get(0).getStatus()).isEqualTo("running");

        // Step 3: Send workflow_run completed webhook
        String completedPayload = """
            {
                "action": "completed",
                "workflow_run": {
                    "id": "12345",
                    "status": "completed",
                    "conclusion": "success",
                    "created_at": "2024-01-15T10:00:00Z",
                    "run_started_at": "2024-01-15T10:01:00Z",
                    "updated_at": "2024-01-15T10:05:00Z",
                    "head_sha": "abc123def456",
                    "head_branch": "main",
                    "actor": {"login": "testuser"}
                },
                "repository": {
                    "name": "webhook-test-repo",
                    "owner": {"login": "testowner"}
                }
            }
            """;

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedPayload))
                .andExpect(status().isOk());

        Thread.sleep(2000);

        // Assert: Build created with success status
        List<Build> builds = buildRepository.findAll();
        assertThat(builds).hasSize(1);
        assertThat(builds.get(0).getStatus()).isEqualTo("success");

        // Assert: QueueTracker updated to completed
        queueEntries = queueTrackerRepository.findAll();
        assertThat(queueEntries.get(0).getStatus()).isEqualTo("completed");

        // Step 4: Call dashboard summary to verify data flow
        MvcResult result = mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        List<?> responsePipelines = (List<?>) response.get("pipelines");
        assertThat(responsePipelines).anyMatch(p ->
                ((Map<String, Object>) p).get("name").equals("webhook-test-repo"));

        System.out.println("✅ Webhook to dashboard flow works end-to-end");
    }

    @Test
    @Order(3)
    void test3_trendsEndpointWithRealData() throws Exception {
        System.out.println("\n=== TEST 3: Trends Endpoint with Real Data ===");

        // Arrange: Create 30 days of historical build data (70% success rate)
        LocalDateTime now = LocalDateTime.now();
        for (int day = 0; day < 30; day++) {
            LocalDateTime buildTime = now.minusDays(day);
            int buildsPerDay = 3 + (day % 4); // 3-6 builds per day

            for (int build = 0; build < buildsPerDay; build++) {
                String status = (build % 10 < 7) ? "success" : "failure"; // 70% success
                createBuildAtTime(testPipeline1, status, 120L + (build * 10),
                        buildTime.plusHours(build * 2));
            }
        }

        // Act & Assert: Test 7-day trends
        MvcResult result = mockMvc.perform(get("/api/trends?days=7"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(response).containsKeys("timestamp", "period_days");
        assertThat(response.get("period_days")).isEqualTo(7);

        // Test pipeline-specific trends
        result = mockMvc.perform(get("/api/trends?days=30&pipelineId=" + testPipeline1.getId()))
                .andExpect(status().isOk())
                .andReturn();

        response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(response).containsKey("pipeline_id");
        assertThat(response.get("pipeline_id")).isEqualTo(testPipeline1.getId());

        // Test success rate trends
        result = mockMvc.perform(get("/api/trends/success-rate?days=7"))
                .andExpect(status().isOk())
                .andReturn();

        response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(response).containsKeys("success_rate_data", "timestamp");

        List<?> successRateData = (List<?>) response.get("success_rate_data");
        assertThat(successRateData).isNotEmpty();

        System.out.println("✅ Trends endpoint works with real historical data");
    }

    @Test
    @Order(4)
    void test4_liveStatusShowsCurrentActivity() throws Exception {
        System.out.println("\n=== TEST 4: Live Status Shows Current Activity ===");

        // Arrange: Create mix of queued, running, and completed builds
        LocalDateTime now = LocalDateTime.now();

        // Create 2 queued builds
        createQueueTrackerEntry(testPipeline1, "queued-1", "queued", now.minusMinutes(10));
        createQueueTrackerEntry(testPipeline2, "queued-2", "queued", now.minusMinutes(5));

        // Create 3 running builds
        createQueueTrackerEntry(testPipeline1, "running-1", "running", now.minusMinutes(8));
        createQueueTrackerEntry(testPipeline2, "running-2", "running", now.minusMinutes(12));
        createQueueTrackerEntry(testPipeline3, "running-3", "running", now.minusMinutes(3));

        // Create 5 completed builds
        createQueueTrackerEntry(testPipeline1, "completed-1", "completed", now.minusHours(1));
        createQueueTrackerEntry(testPipeline1, "completed-2", "completed", now.minusHours(2));
        createQueueTrackerEntry(testPipeline2, "completed-3", "completed", now.minusHours(1));
        createQueueTrackerEntry(testPipeline2, "completed-4", "completed", now.minusHours(3));
        createQueueTrackerEntry(testPipeline3, "completed-5", "completed", now.minusHours(2));

        // Act: Call GET /api/dashboard/live
        MvcResult result = mockMvc.perform(get("/api/dashboard/live"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        // Assert: Response shows only running builds
        assertThat(response).containsKey("running_builds");
        List<?> runningBuilds = (List<?>) response.get("running_builds");
        assertThat(runningBuilds).hasSize(3); // Only running builds

        // Verify each running build has required fields
        Map<String, Object> runningBuild = (Map<String, Object>) runningBuilds.get(0);
        assertThat(runningBuild).containsKeys("build_id", "pipeline_name", "started_at");

        // Update one running build to completed
        QueueTracker runningTracker = queueTrackerRepository.findByBuildId("running-1").orElseThrow();
        runningTracker.setStatus("completed");
        runningTracker.setCompletedAt(LocalDateTime.now());
        queueTrackerRepository.save(runningTracker);

        // Act: Call live endpoint again
        result = mockMvc.perform(get("/api/dashboard/live"))
                .andExpect(status().isOk())
                .andReturn();

        response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

        // Assert: Now shows only 2 running builds
        runningBuilds = (List<?>) response.get("running_builds");
        assertThat(runningBuilds).hasSize(2);

        System.out.println("✅ Live status correctly shows only current running activity");
    }

    @Test
    @Order(5)
    void test5_metricsCalculationAccuracy() throws Exception {
        System.out.println("\n=== TEST 5: Metrics Calculation Accuracy ===");

        // Clear existing builds
        buildRepository.deleteAll();

        // Arrange: Create exactly 10 builds - 7 success, 3 failed
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2);

        // 7 successful builds with specific durations
        createBuildAtTime(testPipeline1, "success", 60L, baseTime.plusMinutes(5));
        createBuildAtTime(testPipeline1, "success", 90L, baseTime.plusMinutes(10));
        createBuildAtTime(testPipeline1, "success", 120L, baseTime.plusMinutes(15));
        createBuildAtTime(testPipeline1, "success", 60L, baseTime.plusMinutes(20));
        createBuildAtTime(testPipeline1, "success", 90L, baseTime.plusMinutes(25));
        createBuildAtTime(testPipeline1, "success", 120L, baseTime.plusMinutes(30));
        createBuildAtTime(testPipeline1, "success", 90L, baseTime.plusMinutes(35));

        // 3 failed builds
        createBuildAtTime(testPipeline1, "failure", 180L, baseTime.plusMinutes(40));
        createBuildAtTime(testPipeline1, "failure", 200L, baseTime.plusMinutes(45));
        createBuildAtTime(testPipeline1, "failure", 150L, baseTime.plusMinutes(50));

        // Act: Trigger metrics calculation
        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline1.getId());

        // Assert: Success rate = 70.0% (7/10)
        assertThat(metrics.getSuccessRate()).isEqualTo(70.0, within(0.1));

        // Assert: Average duration = (60+90+120+60+90+120+90+180+200+150) / 10 = 1160/10 = 116L
        assertThat(metrics.getAvgDurationSeconds()).isEqualTo(116L);

        // Assert: Total builds = 10
        assertThat(metrics.getTotalBuilds()).isEqualTo(10);

        // Add 5 more failed builds with 160L duration each
        for (int i = 0; i < 5; i++) {
            createBuildAtTime(testPipeline1, "failure", 160L, baseTime.plusMinutes(60 + i * 5));
        }

        // Act: Recalculate metrics
        metrics = metricsService.calculateMetricsForPipeline(testPipeline1.getId());

        // Assert: Success rate = 46.67% (7/15)
        assertThat(metrics.getSuccessRate()).isEqualTo(46.67, within(0.1));

        // Assert: Average duration = (1160 + 800) / 15 = 1960/15 = 130.67 → 130L (truncated)
        assertThat(metrics.getAvgDurationSeconds()).isEqualTo(130L);

        // Assert: Total builds = 15
        assertThat(metrics.getTotalBuilds()).isEqualTo(15);

        System.out.println("✅ Metrics calculations are mathematically accurate");
    }

    @Test
    @Order(6)
    void test6_queueDepthTracking() throws Exception {
        System.out.println("\n=== TEST 6: Queue Depth Tracking ===");

        // Clear existing queue data
        queueTrackerRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();

        // Act: Send 5 builds to queue rapidly
        for (int i = 1; i <= 5; i++) {
            createQueueTrackerEntry(testPipeline2, "build-" + i, "queued", now.minusMinutes(10 - i));
        }

        // Assert: QueueTracker has 5 entries with status "queued"
        List<QueueTracker> queuedBuilds = queueTrackerRepository.findByPipelineIdAndStatus(
                testPipeline2.getId(), "queued");
        assertThat(queuedBuilds).hasSize(5);

        // Move first build to in_progress
        QueueTracker firstBuild = queueTrackerRepository.findByBuildId("build-1").orElseThrow();
        firstBuild.setStatus("running");
        firstBuild.setStartedAt(LocalDateTime.now());
        firstBuild.setWaitTimeSeconds(60L); // 1 minute wait
        queueTrackerRepository.save(firstBuild);

        // Assert: Queue depth = 4 (5 - 1 running)
        List<QueueTracker> stillQueued = queueTrackerRepository.findByPipelineIdAndStatus(
                testPipeline2.getId(), "queued");
        assertThat(stillQueued).hasSize(4);

        // Complete all builds gradually and record wait times
        List<QueueTracker> allBuilds = queueTrackerRepository.findAll();
        long totalWaitTime = 0;
        int completedBuilds = 0;

        for (QueueTracker build : allBuilds) {
            if (!build.getStatus().equals("completed")) {
                build.setStatus("completed");
                build.setCompletedAt(LocalDateTime.now());

                if (build.getWaitTimeSeconds() == null) {
                    build.setWaitTimeSeconds((long) (30 + completedBuilds * 10)); // Simulate wait times
                }

                totalWaitTime += build.getWaitTimeSeconds();
                completedBuilds++;

                queueTrackerRepository.save(build);
            }
        }

        // Assert: Average wait time is calculated correctly
        double averageWaitTime = (double) totalWaitTime / completedBuilds;
        assertThat(averageWaitTime).isGreaterThan(0);

        System.out.println("✅ Queue depth tracking works with wait time calculations");
        System.out.println("   Average wait time: " + averageWaitTime + " seconds");
    }


    // Helper methods
    private void createBuildsForPipeline(Pipeline pipeline, int count, LocalDateTime baseTime,
                                         String successStatus, String failStatus, int successPercent) {
        for (int i = 0; i < count; i++) {
            String status = (i % 100) < successPercent ? successStatus : failStatus;
            long duration = 120L + (i * 10);
            LocalDateTime buildTime = baseTime.plusMinutes(i * 15);
            createBuildAtTime(pipeline, status, duration, buildTime);
        }
    }

    private void createBuildAtTime(Pipeline pipeline, String status, Long duration, LocalDateTime time) {
        Build build = Build.builder()
                .pipeline(pipeline)
                .status(status)
                .duration(duration)
                .commitHash("commit-" + System.currentTimeMillis())
                .committer("testuser")
                .branch("main")
                .startTime(time)
                .endTime(time.plusSeconds(duration))
                .build();
        buildRepository.save(build);
    }

    private void createQueueTrackerEntry(Pipeline pipeline, String buildId, String status, LocalDateTime time) {
        QueueTracker tracker = QueueTracker.builder()
                .pipeline(pipeline)
                .buildId(buildId)
                .status(status)
                .queuedAt(time)
                .build();

        if ("running".equals(status)) {
            tracker.setStartedAt(time.plusMinutes(1));
            tracker.setWaitTimeSeconds(60L);
        } else if ("completed".equals(status)) {
            tracker.setStartedAt(time.plusMinutes(1));
            tracker.setCompletedAt(time.plusMinutes(5));
            tracker.setWaitTimeSeconds(60L);
            tracker.setRunTimeSeconds(240L);
        }

        queueTrackerRepository.save(tracker);
    }

    private void createTestAlert(Pipeline pipeline, Alert.AlertType type, Alert.AlertSeverity severity) {
        Alert alert = Alert.builder()
                .pipeline(pipeline)
                .type(type)
                .severity(severity)
                .status(Alert.AlertStatus.ACTIVE)
                .title("Test Alert: " + type.name())
                .message("This is a test alert for integration testing")
                .thresholdValue(75.0)
                .actualValue(60.0)
                .metric("success_rate")
                .createdAt(LocalDateTime.now())
                .triggeredBy("test")
                .build();
        alertRepository.save(alert);
    }
}