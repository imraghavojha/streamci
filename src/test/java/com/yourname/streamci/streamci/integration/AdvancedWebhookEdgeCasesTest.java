package com.yourname.streamci.streamci.integration;

import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.model.QueueTracker;
import com.yourname.streamci.streamci.repository.PipelineRepository;
import com.yourname.streamci.streamci.repository.BuildRepository;
import com.yourname.streamci.streamci.repository.QueueTrackerRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Advanced GitHub webhook edge cases and real-world scenarios
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdvancedWebhookEdgeCasesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    private QueueTrackerRepository queueTrackerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    @Order(1)
    void testMultipleBuildsForSamePipeline() throws Exception {
        System.out.println("\n=== EDGE CASE: Multiple builds for same pipeline ===");

        // Send first build
        String successPayload = loadJsonFixture("workflow_run_success.json");
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPayload))
                .andExpect(status().isOk());

        Thread.sleep(3000);

        // Send second build (failure) for same pipeline
        String failurePayload = loadJsonFixture("workflow_run_failure.json");
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failurePayload))
                .andExpect(status().isOk());

        Thread.sleep(3000);

        // Verify: Should have 1 pipeline but 2 builds
        List<Pipeline> pipelines = pipelineRepository.findAll();
        List<Build> builds = buildRepository.findAll();

        assertThat(pipelines).hasSize(1);
        assertThat(builds).hasSize(2);

        // Pipeline should reflect latest build status (failure)
        Pipeline pipeline = pipelines.get(0);
        assertThat(pipeline.getStatus()).isEqualTo("failure");

        // Should have one success and one failure build
        long successCount = builds.stream().filter(b -> "success".equals(b.getStatus())).count();
        long failureCount = builds.stream().filter(b -> "failure".equals(b.getStatus())).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);

        System.out.println("✅ Multiple builds handled correctly");
    }

    @Test
    @Order(2)
    void testBuildQueueLifecycle() throws Exception {
        System.out.println("\n=== EDGE CASE: Complete build lifecycle (queued → running → completed) ===");

        // Step 1: Build gets queued
        String queuedPayload = loadJsonFixture("workflow_run_queued.json");
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queuedPayload))
                .andExpect(status().isOk());

        Thread.sleep(2000);

        // Verify queued state
        List<QueueTracker> queueAfterQueued = queueTrackerRepository.findAll();
        assertThat(queueAfterQueued).hasSize(1);
        assertThat(queueAfterQueued.get(0).getStatus()).isEqualTo("queued");

        // Step 2: Build starts running
        String inProgressPayload = loadJsonFixture("workflow_run_in_progress.json");
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inProgressPayload))
                .andExpect(status().isOk());

        Thread.sleep(2000);

        // Verify running state (might be same build ID)
        List<QueueTracker> queueAfterRunning = queueTrackerRepository.findAll();
        if (!queueAfterRunning.isEmpty()) {
            // Check if any tracker moved to running state
            boolean hasRunning = queueAfterRunning.stream()
                    .anyMatch(q -> "running".equals(q.getStatus()));
            System.out.println("Has running status: " + hasRunning);
        }

        // Step 3: Build completes
        String successPayload = loadJsonFixture("workflow_run_success.json");
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPayload))
                .andExpect(status().isOk());

        Thread.sleep(3000);

        // Verify final state
        List<Build> builds = buildRepository.findAll();
        List<QueueTracker> queueAfterCompleted = queueTrackerRepository.findAll();

        assertThat(builds).hasSizeGreaterThanOrEqualTo(1);
        assertThat(queueAfterCompleted).hasSizeGreaterThanOrEqualTo(1);

        // At least one build should be completed
        boolean hasCompletedBuild = builds.stream()
                .anyMatch(b -> "success".equals(b.getStatus()));
        assertThat(hasCompletedBuild).isTrue();

        System.out.println("✅ Build lifecycle tracked correctly");
    }

    @Test
    @Order(3)
    void testCancelledWorkflow() throws Exception {
        System.out.println("\n=== EDGE CASE: Cancelled workflow ===");

        String cancelledPayload = loadJsonFixture("workflow_run_cancelled.json");

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelledPayload))
                .andExpect(status().isOk());

        Thread.sleep(3000);

        List<Build> builds = buildRepository.findAll();
        assertThat(builds).hasSize(1);

        Build build = builds.get(0);
        assertThat(build.getStatus()).isEqualTo("cancelled");
        assertThat(build.getBranch()).isEqualTo("release/v2.1.0");
        assertThat(build.getCommitter()).isEqualTo("release-manager");

        System.out.println("✅ Cancelled workflow handled correctly");
    }





    @Test
    @Order(5)
    void testMalformedJsonPayload() throws Exception {
        System.out.println("\n=== EDGE CASE: Malformed JSON payload ===");

        String malformedPayload = """
            {
                "action": "completed",
                "workflow_run": {
                    "id": "not_a_number",
                    "status": null,
                    "missing_required_fields": true
                }
                // Missing closing brace and repository info
            """;

        // Should handle gracefully (400 or 200 depending on implementation)
        var result = mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedPayload))
                .andReturn();

        // Should not create any entities
        Thread.sleep(1000);
        assertThat(pipelineRepository.findAll()).isEmpty();
        assertThat(buildRepository.findAll()).isEmpty();

        System.out.println("✅ Malformed JSON handled gracefully");
    }

    @Test
    @Order(6)
    void testMissingRequiredFields() throws Exception {
        System.out.println("\n=== EDGE CASE: Missing required fields in payload ===");

        String incompletePayload = """
            {
                "action": "completed",
                "workflow_run": {
                    "id": 999999999,
                    "status": "completed",
                    "conclusion": "success"
                }
            }
            """;

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompletePayload))
                .andExpect(status().isOk());

        Thread.sleep(3000);

        // Should handle gracefully - might create entities with default values
        List<Pipeline> pipelines = pipelineRepository.findAll();
        List<Build> builds = buildRepository.findAll();

        System.out.println("Pipelines after incomplete payload: " + pipelines.size());
        System.out.println("Builds after incomplete payload: " + builds.size());

        // If entities are created, they should have reasonable defaults
        if (!builds.isEmpty()) {
            Build build = builds.get(0);
            assertThat(build.getStatus()).isEqualTo("success");
            // Other fields might be "unknown" or default values
        }

        System.out.println("✅ Missing fields handled gracefully");
    }

    @Test
    @Order(7)
    void testHighVolumeWebhooks() throws Exception {
        System.out.println("\n=== PERFORMANCE: High volume webhooks ===");

        String payload = loadJsonFixture("workflow_run_success.json");

        // Send 10 webhooks rapidly
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/webhooks/github")
                            .header("X-GitHub-Event", "workflow_run")
                            .header("X-Hub-Signature-256", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk());

            // Small delay to simulate real rapid webhooks
            Thread.sleep(100);
        }

        // Wait for all async processing
        Thread.sleep(5000);

        List<Build> builds = buildRepository.findAll();
        System.out.println("Builds created from 10 rapid webhooks: " + builds.size());

        // Should handle all requests (exact count depends on how duplicates are handled)
        assertThat(builds.size()).isGreaterThan(0);
        assertThat(builds.size()).isLessThanOrEqualTo(10);

        System.out.println("✅ High volume webhooks processed");
    }

    @Test
    @Order(8)
    void testUnknownEventTypes() throws Exception {
        System.out.println("\n=== EDGE CASE: Unknown GitHub event types ===");

        String[] unknownEvents = {
                "repository_dispatch",
                "deployment",
                "release",
                "issues",
                "pull_request_review"
        };

        for (String event : unknownEvents) {
            mockMvc.perform(post("/api/webhooks/github")
                            .header("X-GitHub-Event", event)
                            .header("X-Hub-Signature-256", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"opened\"}"))
                    .andExpect(status().isOk());
        }

        Thread.sleep(2000);

        // Should not create any entities
        assertThat(pipelineRepository.findAll()).isEmpty();
        assertThat(buildRepository.findAll()).isEmpty();
        assertThat(queueTrackerRepository.findAll()).isEmpty();

        System.out.println("✅ Unknown event types ignored correctly");
    }

    @Test
    @Order(9)
    void testDifferentRepositories() throws Exception {
        System.out.println("\n=== EDGE CASE: Multiple repositories ===");

        // Modify payload for different repository
        String payload1 = loadJsonFixture("workflow_run_success.json")
                .replace("\"Hello-World\"", "\"Repository-One\"");

        String payload2 = loadJsonFixture("workflow_run_failure.json")
                .replace("\"Hello-World\"", "\"Repository-Two\"");

        // Send builds for different repositories
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isOk());

        Thread.sleep(4000);

        // Should create separate pipelines for each repository
        List<Pipeline> pipelines = pipelineRepository.findAll();
        List<Build> builds = buildRepository.findAll();

        assertThat(pipelines).hasSize(2);
        assertThat(builds).hasSize(2);

        // Verify different repository names
        List<String> pipelineNames = pipelines.stream()
                .map(Pipeline::getName)
                .sorted()
                .toList();

        assertThat(pipelineNames).containsExactly("Repository-One", "Repository-Two");

        System.out.println("✅ Multiple repositories handled correctly");
    }

    private void cleanDatabase() {
        try {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("DELETE FROM queue_tracker");
            jdbcTemplate.execute("DELETE FROM build");
            jdbcTemplate.execute("DELETE FROM pipeline_metrics");
            jdbcTemplate.execute("DELETE FROM alerts");
            jdbcTemplate.execute("DELETE FROM alert_configs");
            jdbcTemplate.execute("DELETE FROM queue_metrics");
            jdbcTemplate.execute("DELETE FROM pipeline");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (Exception e) {
            try {
                queueTrackerRepository.deleteAll();
                buildRepository.deleteAll();
                pipelineRepository.deleteAll();
            } catch (Exception ignored) {}
        }
    }

    private String loadJsonFixture(String filename) throws Exception {
        ClassPathResource resource = new ClassPathResource("fixtures/" + filename);
        return Files.readString(resource.getFile().toPath());
    }
}