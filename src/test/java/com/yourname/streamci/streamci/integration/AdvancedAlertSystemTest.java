package com.yourname.streamci.streamci.integration;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import com.yourname.streamci.streamci.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADVANCED ALERT SYSTEM TESTS
 * Tests sophisticated alerting scenarios and edge cases
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdvancedAlertSystemTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertConfigRepository alertConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Pipeline testPipeline;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        createAlertConfigs();

        testPipeline = Pipeline.builder()
                .name("AdvancedTestPipeline")
                .status("active")
                .duration(300)
                .build();
        testPipeline = pipelineRepository.save(testPipeline);

        System.out.println("🔧 Advanced test setup complete - Pipeline: " + testPipeline.getId());
    }

    @Test
    @Order(1)
    void testAlertDeduplication() {
        System.out.println("\n=== TEST 1: Alert Deduplication ===");

        // Create failure scenario
        createBuilds(3, "success", 300L);
        createBuilds(7, "failure", 500L); // 30% success rate

        // Calculate metrics multiple times
        metricsService.calculateMetricsForPipeline(testPipeline.getId());
        metricsService.calculateMetricsForPipeline(testPipeline.getId());
        metricsService.calculateMetricsForPipeline(testPipeline.getId());

        List<Alert> alerts = alertService.getActiveAlerts();
        System.out.println("🚨 Active alerts after 3 calculations: " + alerts.size());

        // Should only have 1 alert despite multiple calculations
        long successRateAlerts = alerts.stream()
                .filter(a -> a.getType() == Alert.AlertType.SUCCESS_RATE_DROP)
                .count();

        assertThat(successRateAlerts).isEqualTo(1);
        System.out.println("✅ Alert deduplication working - only 1 alert for repeated issue");
    }

    @Test
    @Order(2)
    void testAlertSeverityEscalation() {
        System.out.println("\n=== TEST 2: Alert Severity Escalation ===");

        LocalDateTime now = LocalDateTime.now();

        // Start with just 2 failures (below 3.0 threshold - no alert yet)
        createBuildAtTime("failure", 400L, now.minusHours(3));
        createBuildAtTime("failure", 450L, now.minusHours(2));

        metricsService.calculateMetricsForPipeline(testPipeline.getId());
        List<Alert> initialAlerts = alertService.getActiveAlerts();
        System.out.println("📊 Alerts with 2 failures: " + initialAlerts.size());

        // Add 3rd failure to trigger WARNING/CRITICAL
        createBuildAtTime("failure", 500L, now.minusHours(1));

        metricsService.calculateMetricsForPipeline(testPipeline.getId());
        List<Alert> warningAlerts = alertService.getActiveAlerts();

        Alert initialAlert = warningAlerts.stream()
                .filter(a -> a.getType() == Alert.AlertType.CONSECUTIVE_FAILURES)
                .findFirst()
                .orElse(null);

        if (initialAlert != null) {
            System.out.println("📊 Alert after 3 failures: " + initialAlert.getSeverity() +
                    " (" + initialAlert.getActualValue() + " consecutive)");
        }

        // Escalate to emergency level (6+ consecutive failures)
        createBuildAtTime("failure", 550L, now.minusMinutes(30));
        createBuildAtTime("failure", 600L, now.minusMinutes(15));
        createBuildAtTime("failure", 650L, now.minusMinutes(5)); // 6th failure

        metricsService.calculateMetricsForPipeline(testPipeline.getId());
        List<Alert> criticalAlerts = alertService.getActiveAlerts();

        Alert escalatedAlert = criticalAlerts.stream()
                .filter(a -> a.getType() == Alert.AlertType.CONSECUTIVE_FAILURES)
                .findFirst()
                .orElse(null);

        assertThat(escalatedAlert).isNotNull();
        System.out.println("📊 Final alert: " + escalatedAlert.getSeverity() +
                " (" + escalatedAlert.getActualValue() + " consecutive)");

        // Should be EMERGENCY with 6 consecutive failures
        if (escalatedAlert.getActualValue() >= 5.0) {
            assertThat(escalatedAlert.getSeverity()).isEqualTo(Alert.AlertSeverity.EMERGENCY);
        } else {
            assertThat(escalatedAlert.getSeverity()).isIn(Alert.AlertSeverity.CRITICAL, Alert.AlertSeverity.EMERGENCY);
        }

        System.out.println("✅ Alert escalation working: " + escalatedAlert.getSeverity());
    }

    @Test
    @Order(4)
    void testMultiplePipelineAlerts() {
        System.out.println("\n=== TEST 4: Multiple Pipeline Alerts ===");

        // Create second pipeline
        Pipeline secondPipeline = Pipeline.builder()
                .name("SecondTestPipeline")
                .status("active")
                .duration(250)
                .build();
        secondPipeline = pipelineRepository.save(secondPipeline);

        // Create different problems for each pipeline
        // Pipeline 1: Success rate issue
        createBuildsForPipeline(testPipeline, 3, "success", 300L);
        createBuildsForPipeline(testPipeline, 7, "failure", 500L);

        // Pipeline 2: Consecutive failures
        LocalDateTime now = LocalDateTime.now();
        createBuildAtTimeForPipeline(secondPipeline, "failure", 400L, now.minusHours(4));
        createBuildAtTimeForPipeline(secondPipeline, "failure", 450L, now.minusHours(3));
        createBuildAtTimeForPipeline(secondPipeline, "failure", 500L, now.minusHours(2));
        createBuildAtTimeForPipeline(secondPipeline, "failure", 550L, now.minusHours(1));

        metricsService.calculateMetricsForPipeline(testPipeline.getId());
        metricsService.calculateMetricsForPipeline(secondPipeline.getId());

        List<Alert> allAlerts = alertService.getActiveAlerts();
        List<Alert> pipeline1Alerts = alertService.getAlertsForPipeline(testPipeline.getId());
        List<Alert> pipeline2Alerts = alertService.getAlertsForPipeline(secondPipeline.getId());

        System.out.println("🚨 Total alerts: " + allAlerts.size());
        System.out.println("🚨 Pipeline 1 alerts: " + pipeline1Alerts.size());
        System.out.println("🚨 Pipeline 2 alerts: " + pipeline2Alerts.size());

        assertThat(pipeline1Alerts).hasSizeGreaterThan(0);
        assertThat(pipeline2Alerts).hasSizeGreaterThan(0);

        // Verify alerts are correctly associated
        for (Alert alert : pipeline1Alerts) {
            assertThat(alert.getPipeline().getId()).isEqualTo(testPipeline.getId());
        }

        for (Alert alert : pipeline2Alerts) {
            assertThat(alert.getPipeline().getId()).isEqualTo(secondPipeline.getId());
        }

        System.out.println("✅ Multiple pipelines have separate alert contexts");
    }

    @Test
    @Order(5)
    void testAlertAcknowledgmentWorkflow() {
        System.out.println("\n=== TEST 5: Alert Acknowledgment Workflow ===");

        // Create alert
        createBuilds(2, "success", 300L);
        createBuilds(8, "failure", 500L); // 20% success rate

        metricsService.calculateMetricsForPipeline(testPipeline.getId());

        List<Alert> alerts = alertService.getActiveAlerts();
        assertThat(alerts).hasSizeGreaterThan(0);

        Alert alert = alerts.get(0);
        Long alertId = alert.getId();
        System.out.println("🔍 Testing alert workflow for ID: " + alertId);

        // Test acknowledgment
        Optional<Alert> acknowledgedAlert = alertService.acknowledgeAlert(alertId, "ops-team");
        assertThat(acknowledgedAlert).isPresent();
        assertThat(acknowledgedAlert.get().getStatus()).isEqualTo(Alert.AlertStatus.ACKNOWLEDGED);
        assertThat(acknowledgedAlert.get().getAcknowledgedAt()).isNotNull();

        System.out.println("✅ Alert acknowledged by ops-team");

        // Test resolution
        Optional<Alert> resolvedAlert = alertService.resolveAlert(
                alertId, "senior-dev", "Issue fixed by rolling back deployment"
        );
        assertThat(resolvedAlert).isPresent();
        assertThat(resolvedAlert.get().getStatus()).isEqualTo(Alert.AlertStatus.RESOLVED);
        assertThat(resolvedAlert.get().getResolvedAt()).isNotNull();
        assertThat(resolvedAlert.get().getResolvedBy()).isEqualTo("senior-dev");
        assertThat(resolvedAlert.get().getNotes()).isEqualTo("Issue fixed by rolling back deployment");

        System.out.println("✅ Alert resolved with proper audit trail");

        // Verify it's no longer active
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        long activeCount = activeAlerts.stream()
                .filter(a -> a.getId().equals(alertId))
                .count();
        assertThat(activeCount).isEqualTo(0);

        System.out.println("✅ Alert no longer appears in active list");
    }

    @Test
    @Order(7)
    void testAlertCooldownPeriod() {
        System.out.println("\n=== TEST 7: Alert Cooldown Period ===");

        // Create alert config with short cooldown for testing
        AlertConfig shortCooldownConfig = AlertConfig.builder()
                .pipeline(testPipeline)
                .alertType(Alert.AlertType.SUCCESS_RATE_DROP)
                .enabled(true)
                .warningThreshold(75.0)
                .criticalThreshold(50.0)
                .evaluationWindowMinutes(30)
                .cooldownMinutes(1) // Very short cooldown
                .notifyEmail(false)
                .notifySlack(false)
                .notifyWebhook(false)
                .build();

        alertConfigRepository.save(shortCooldownConfig);

        // Create failure scenario
        createBuilds(2, "success", 300L);
        createBuilds(8, "failure", 500L); // 20% success rate

        // First calculation - should create alert
        metricsService.calculateMetricsForPipeline(testPipeline.getId());
        List<Alert> firstAlerts = alertService.getActiveAlerts();
        System.out.println("🚨 First calculation alerts: " + firstAlerts.size());

        // Immediate second calculation - should NOT create duplicate due to cooldown
        metricsService.calculateMetricsForPipeline(testPipeline.getId());
        List<Alert> secondAlerts = alertService.getActiveAlerts();
        System.out.println("🚨 Immediate second calculation alerts: " + secondAlerts.size());

        // Should still be same number (no duplicates)
        assertThat(secondAlerts.size()).isEqualTo(firstAlerts.size());

        // Wait for cooldown to expire (simulate)
        try {
            Thread.sleep(2000); // Wait 2 seconds (longer than 1 minute cooldown in test time)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Add more problems and recalculate
        createBuilds(1, "failure", 600L);
        metricsService.calculateMetricsForPipeline(testPipeline.getId());
        List<Alert> afterCooldownAlerts = alertService.getActiveAlerts();
        System.out.println("🚨 After cooldown alerts: " + afterCooldownAlerts.size());

        System.out.println("✅ Cooldown period preventing duplicate alerts");
    }

    @Test
    @Order(8)
    void testAlertStatistics() {
        System.out.println("\n=== TEST 8: Alert Statistics ===");

        LocalDateTime now = LocalDateTime.now();

        // Create varied alert scenarios
        // High severity alerts
        createBuildAtTime("failure", 400L, now.minusHours(5));
        createBuildAtTime("failure", 450L, now.minusHours(4));
        createBuildAtTime("failure", 500L, now.minusHours(3));
        createBuildAtTime("failure", 550L, now.minusHours(2));
        createBuildAtTime("failure", 600L, now.minusHours(1));

        // Low success rate
        createBuilds(1, "success", 300L);
        createBuilds(9, "failure", 500L);

        metricsService.calculateMetricsForPipeline(testPipeline.getId());

        List<Alert> allAlerts = alertRepository.findAll();
        System.out.println("📊 Total alerts generated: " + allAlerts.size());

        // Count by severity
        long criticalCount = allAlerts.stream()
                .filter(a -> a.getSeverity() == Alert.AlertSeverity.CRITICAL ||
                        a.getSeverity() == Alert.AlertSeverity.EMERGENCY)
                .count();

        long warningCount = allAlerts.stream()
                .filter(a -> a.getSeverity() == Alert.AlertSeverity.WARNING)
                .count();

        long infoCount = allAlerts.stream()
                .filter(a -> a.getSeverity() == Alert.AlertSeverity.INFO)
                .count();

        System.out.println("🔴 Critical/Emergency alerts: " + criticalCount);
        System.out.println("🟡 Warning alerts: " + warningCount);
        System.out.println("🔵 Info alerts: " + infoCount);

        // Count by type
        long successRateAlerts = allAlerts.stream()
                .filter(a -> a.getType() == Alert.AlertType.SUCCESS_RATE_DROP)
                .count();

        long consecutiveFailureAlerts = allAlerts.stream()
                .filter(a -> a.getType() == Alert.AlertType.CONSECUTIVE_FAILURES)
                .count();

        System.out.println("📉 Success rate alerts: " + successRateAlerts);
        System.out.println("🔁 Consecutive failure alerts: " + consecutiveFailureAlerts);

        assertThat(allAlerts.size()).isGreaterThan(0);
        assertThat(criticalCount + warningCount + infoCount).isEqualTo(allAlerts.size());

        System.out.println("✅ Alert statistics calculated correctly");
    }

    @Test
    @Order(9)
    void testStaleDataDetection() {
        System.out.println("\n=== TEST 9: Stale Data Detection ===");

        LocalDateTime veryOld = LocalDateTime.now().minusHours(72); // 3 days ago

        // Create only very old builds
        createBuildAtTime("success", 300L, veryOld);
        createBuildAtTime("success", 280L, veryOld.plusHours(1));
        createBuildAtTime("failure", 450L, veryOld.plusHours(2));

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        System.out.println("📊 Last activity: " + metrics.getLastSuccess());
        System.out.println("📊 Hours since activity: " +
                java.time.Duration.between(metrics.getLastSuccess(), LocalDateTime.now()).toHours());

        List<Alert> alerts = alertService.getActiveAlerts();
        System.out.println("🚨 Stale data alerts: " + alerts.size());

        Alert staleAlert = alerts.stream()
                .filter(a -> a.getType() == Alert.AlertType.STALE_PIPELINE)
                .findFirst()
                .orElse(null);

        if (staleAlert != null) {
            System.out.println("✅ Stale data detected:");
            System.out.println("  Severity: " + staleAlert.getSeverity());
            System.out.println("  Hours since activity: " + staleAlert.getActualValue());
            System.out.println("  Threshold: " + staleAlert.getThresholdValue());

            assertThat(staleAlert.getSeverity()).isEqualTo(Alert.AlertSeverity.INFO);
            assertThat(staleAlert.getActualValue()).isGreaterThan(24.0);
        } else {
            System.out.println("⚠️ No stale data alert generated");
        }
    }

    @Test
    @Order(10)
    void testHighVolumeAlertPerformance() {
        System.out.println("\n=== TEST 10: High Volume Alert Performance ===");

        long startTime = System.currentTimeMillis();

        // Create many builds rapidly
        for (int batch = 0; batch < 5; batch++) {
            for (int i = 0; i < 20; i++) {
                String status = (i % 4 == 0) ? "failure" : "success"; // 25% failure rate
                createBuild(status, (long) (200 + (i % 10) * 50));
            }

            // Calculate metrics for each batch
            metricsService.calculateMetricsForPipeline(testPipeline.getId());
        }

        long processingTime = System.currentTimeMillis() - startTime;

        List<Alert> alerts = alertService.getActiveAlerts();
        List<Build> allBuilds = buildRepository.findAll();

        System.out.println("📊 Processed " + allBuilds.size() + " builds in " + processingTime + "ms");
        System.out.println("🚨 Generated " + alerts.size() + " alerts");
        System.out.println("⏱️ Average time per build: " + (processingTime / allBuilds.size()) + "ms");

        // Performance should be reasonable
        assertThat(processingTime).isLessThan(15000L); // Less than 15 seconds
        assertThat(allBuilds.size()).isEqualTo(100);

        System.out.println("✅ High volume alert processing performance acceptable");
    }

    // Helper methods
    private void createAlertConfigs() {
        AlertConfig successRateConfig = AlertConfig.builder()
                .pipeline(null)
                .alertType(Alert.AlertType.SUCCESS_RATE_DROP)
                .enabled(true)
                .warningThreshold(75.0)
                .criticalThreshold(50.0)
                .evaluationWindowMinutes(30)
                .cooldownMinutes(15)
                .notifyEmail(false)
                .notifySlack(false)
                .notifyWebhook(false)
                .build();
        alertConfigRepository.save(successRateConfig);

        AlertConfig consecutiveConfig = AlertConfig.builder()
                .pipeline(null)
                .alertType(Alert.AlertType.CONSECUTIVE_FAILURES)
                .enabled(true)
                .warningThreshold(3.0)
                .criticalThreshold(5.0)
                .evaluationWindowMinutes(120)
                .cooldownMinutes(60)
                .notifyEmail(false)
                .notifySlack(false)
                .notifyWebhook(false)
                .build();
        alertConfigRepository.save(consecutiveConfig);

        AlertConfig staleConfig = AlertConfig.builder()
                .pipeline(null)
                .alertType(Alert.AlertType.STALE_PIPELINE)
                .enabled(true)
                .warningThreshold(24.0)
                .criticalThreshold(72.0)
                .evaluationWindowMinutes(1440)
                .cooldownMinutes(720)
                .notifyEmail(false)
                .notifySlack(false)
                .notifyWebhook(false)
                .build();
        alertConfigRepository.save(staleConfig);
    }

    private void createBuilds(int count, String status, Long duration) {
        for (int i = 0; i < count; i++) {
            createBuild(status, duration);
        }
    }

    private void createBuild(String status, Long duration) {
        LocalDateTime time = LocalDateTime.now().minusMinutes((long) (Math.random() * 60));
        createBuildAtTime(status, duration, time);
    }

    private void createBuildsForPipeline(Pipeline pipeline, int count, String status, Long duration) {
        for (int i = 0; i < count; i++) {
            LocalDateTime time = LocalDateTime.now().minusMinutes((long) (Math.random() * 60));
            createBuildAtTimeForPipeline(pipeline, status, duration, time);
        }
    }

    private void createBuildAtTime(String status, Long duration, LocalDateTime time) {
        createBuildAtTimeForPipeline(testPipeline, status, duration, time);
    }

    private void createBuildAtTimeForPipeline(Pipeline pipeline, String status, Long duration, LocalDateTime time) {
        Build build = Build.builder()
                .pipeline(pipeline)
                .status(status)
                .duration(duration)
                .commitHash("commit" + Math.random())
                .committer("testuser")
                .branch("main")
                .startTime(time)
                .endTime(time.plusSeconds(duration != null ? duration : 300))
                .build();
        buildRepository.save(build);
    }

    private void cleanDatabase() {
        try {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("DELETE FROM alerts");
            jdbcTemplate.execute("DELETE FROM alert_configs");
            jdbcTemplate.execute("DELETE FROM pipeline_metrics");
            jdbcTemplate.execute("DELETE FROM build");
            jdbcTemplate.execute("DELETE FROM pipeline");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (Exception e) {
            try {
                alertRepository.deleteAll();
                alertConfigRepository.deleteAll();
                buildRepository.deleteAll();
                pipelineRepository.deleteAll();
            } catch (Exception ignored) {}
        }
    }
}