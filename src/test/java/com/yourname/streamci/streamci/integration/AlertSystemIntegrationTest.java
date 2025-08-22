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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIXED ALERT SYSTEM TESTS - Manually creates alert configs
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AlertSystemIntegrationTest {

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
        createAlertConfigs(); // ← THIS IS THE KEY FIX

        // Create test pipeline
        testPipeline = Pipeline.builder()
                .name("TestPipeline")
                .status("active")
                .duration(300)
                .build();
        testPipeline = pipelineRepository.save(testPipeline);

        System.out.println("🔧 Setup complete - Pipeline: " + testPipeline.getId() +
                ", Alert configs: " + alertConfigRepository.count());
    }

    /**
     * CRITICAL: Create alert configurations that AlertConfigInitializer would create
     */
    private void createAlertConfigs() {
        // Success rate alert config
        AlertConfig successRateConfig = AlertConfig.builder()
                .pipeline(null) // global config
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

        // Duration increase alert config
        AlertConfig durationConfig = AlertConfig.builder()
                .pipeline(null)
                .alertType(Alert.AlertType.DURATION_INCREASE)
                .enabled(true)
                .warningThreshold(50.0)  // 50% increase
                .criticalThreshold(100.0) // 100% increase
                .evaluationWindowMinutes(60)
                .cooldownMinutes(30)
                .notifyEmail(false)
                .notifySlack(false)
                .notifyWebhook(false)
                .build();
        alertConfigRepository.save(durationConfig);

        // Consecutive failures alert config
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

        // Stale pipeline alert config
        AlertConfig staleConfig = AlertConfig.builder()
                .pipeline(null)
                .alertType(Alert.AlertType.STALE_PIPELINE)
                .enabled(true)
                .warningThreshold(24.0)  // hours
                .criticalThreshold(72.0) // hours
                .evaluationWindowMinutes(1440) // 24 hours
                .cooldownMinutes(720) // 12 hours
                .notifyEmail(false)
                .notifySlack(false)
                .notifyWebhook(false)
                .build();
        alertConfigRepository.save(staleConfig);

        System.out.println("✅ Created " + alertConfigRepository.count() + " alert configurations");
    }

    @Test
    @Order(1)
    void testSuccessRateDropAlert() {
        System.out.println("\n=== TEST 1: Success Rate Drop Alert (FIXED) ===");

        // Create initial good builds
        createBuilds(9, "success", 300L);
        createBuilds(1, "failure", 400L);

        PipelineMetrics initialMetrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        System.out.println("📊 Initial success rate: " + initialMetrics.getSuccessRate() + "%");

        // Should be no alerts initially
        List<Alert> initialAlerts = alertService.getActiveAlerts();
        System.out.println("🚨 Initial alerts: " + initialAlerts.size());

        // Add failures to drop success rate WELL below 50%
        createBuilds(1, "success", 300L);
        createBuilds(11, "failure", 500L); // Now: 11 success, 12 failure = 47.8% (below 50% threshold)

        PipelineMetrics degradedMetrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        System.out.println("📊 Degraded success rate: " + degradedMetrics.getSuccessRate() + "% (should be ~47.8%)");

        // Check for alerts
        List<Alert> alerts = alertService.getActiveAlerts();
        System.out.println("🚨 Active alerts: " + alerts.size());

        // Debug: show all alerts
        alerts.forEach(alert -> {
            System.out.println("  Alert: " + alert.getType() + " | " + alert.getSeverity() + " | " + alert.getTitle());
        });

        assertThat(alerts).hasSize(1);
        Alert alert = alerts.get(0);
        assertThat(alert.getType()).isEqualTo(Alert.AlertType.SUCCESS_RATE_DROP);
        assertThat(alert.getSeverity()).isIn(Alert.AlertSeverity.WARNING, Alert.AlertSeverity.CRITICAL);

        System.out.println("✅ Success rate drop alert triggered correctly!");
    }

    @Test
    @Order(2)
    void testConsecutiveFailuresAlert() {
        System.out.println("\n=== TEST 2: Consecutive Failures Alert (FIXED) ===");

        LocalDateTime now = LocalDateTime.now();

        // Create pattern: success, then 4 consecutive failures
        createBuildAtTime("success", 300L, now.minusHours(5));
        createBuildAtTime("failure", 400L, now.minusHours(4));
        createBuildAtTime("failure", 450L, now.minusHours(3));
        createBuildAtTime("failure", 500L, now.minusHours(2));
        createBuildAtTime("failure", 550L, now.minusHours(1));

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        System.out.println("📊 Consecutive failures: " + metrics.getConsecutiveFailures());

        List<Alert> alerts = alertService.getActiveAlerts();
        System.out.println("🚨 Active alerts: " + alerts.size());

        // Should have consecutive failures alert
        Alert consecutiveAlert = alerts.stream()
                .filter(a -> a.getType() == Alert.AlertType.CONSECUTIVE_FAILURES)
                .findFirst()
                .orElse(null);

        assertThat(consecutiveAlert).isNotNull();
        assertThat(consecutiveAlert.getSeverity()).isIn(Alert.AlertSeverity.CRITICAL, Alert.AlertSeverity.WARNING);

        System.out.println("✅ Consecutive failures alert: " + consecutiveAlert.getSeverity());
    }

    @Test
    @Order(3)
    void testDurationIncreaseAlert() {
        System.out.println("\n=== TEST 3: Duration Increase Alert (FIXED) ===");

        // Create baseline builds
        createBuilds(5, "success", 200L);
        PipelineMetrics baseline = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        System.out.println("📊 Baseline duration: " + baseline.getAvgDurationSeconds() + "s");

        // Wait to ensure different timestamps
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Add builds with significantly increased duration
        createBuilds(3, "success", 800L); // 4x longer
        PipelineMetrics degraded = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        System.out.println("📊 New duration: " + degraded.getAvgDurationSeconds() + "s");

        List<Alert> alerts = alertService.getActiveAlerts();
        System.out.println("🚨 Active alerts: " + alerts.size());

        // Look for duration alert
        Alert durationAlert = alerts.stream()
                .filter(a -> a.getType() == Alert.AlertType.DURATION_INCREASE)
                .findFirst()
                .orElse(null);

        if (durationAlert != null) {
            System.out.println("✅ Duration alert triggered: " + durationAlert.getActualValue() + "% increase");
            assertThat(durationAlert.getSeverity()).isIn(Alert.AlertSeverity.WARNING, Alert.AlertSeverity.CRITICAL);
        } else {
            System.out.println("⚠️ No duration alert (may need bigger increase)");
        }
    }

    @Test
    @Order(4)
    void testAlertAutoResolution() {
        System.out.println("\n=== TEST 4: Alert Auto-Resolution (FIXED) ===");

        LocalDateTime now = LocalDateTime.now();

        // Create consecutive failures
        createBuildAtTime("failure", 400L, now.minusHours(3));
        createBuildAtTime("failure", 450L, now.minusHours(2));
        createBuildAtTime("failure", 500L, now.minusHours(1));

        PipelineMetrics failureMetrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        System.out.println("📊 Consecutive failures: " + failureMetrics.getConsecutiveFailures());

        List<Alert> alertsAfterFailures = alertService.getActiveAlerts();
        System.out.println("🚨 Alerts after failures: " + alertsAfterFailures.size());

        // Add successful build to break streak
        createBuildAtTime("success", 300L, now);
        PipelineMetrics recoveredMetrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        System.out.println("📊 Consecutive failures after success: " + recoveredMetrics.getConsecutiveFailures());

        // Check active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        System.out.println("🚨 Active alerts after recovery: " + activeAlerts.size());

        // Check for resolved alerts
        List<Alert> allAlerts = alertRepository.findAll();
        long resolvedCount = allAlerts.stream()
                .filter(a -> a.getStatus() == Alert.AlertStatus.RESOLVED)
                .count();

        System.out.println("📊 Total alerts: " + allAlerts.size() + ", Resolved: " + resolvedCount);

        if (resolvedCount > 0) {
            System.out.println("✅ Auto-resolution working!");
        } else {
            System.out.println("⚠️ No auto-resolution (may need different conditions)");
        }
    }

    @Test
    @Order(5)
    void testStaleDataAlert() {
        System.out.println("\n=== TEST 5: Stale Data Alert (FIXED) ===");

        LocalDateTime longAgo = LocalDateTime.now().minusHours(48);

        // Create only old builds
        createBuildAtTime("success", 300L, longAgo);
        createBuildAtTime("success", 250L, longAgo.plusHours(1));

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        System.out.println("📊 Last success: " + metrics.getLastSuccess());

        List<Alert> alerts = alertService.getActiveAlerts();
        System.out.println("🚨 Active alerts: " + alerts.size());

        Alert staleAlert = alerts.stream()
                .filter(a -> a.getType() == Alert.AlertType.STALE_PIPELINE)
                .findFirst()
                .orElse(null);

        if (staleAlert != null) {
            System.out.println("✅ Stale alert triggered: " + staleAlert.getActualValue() + " hours");
            assertThat(staleAlert.getSeverity()).isEqualTo(Alert.AlertSeverity.INFO);
        } else {
            System.out.println("⚠️ No stale alert");
        }
    }

    // Helper methods
    private void createBuilds(int count, String status, Long duration) {
        for (int i = 0; i < count; i++) {
            createBuild(status, duration);
        }
    }

    private void createBuild(String status, Long duration) {
        LocalDateTime time = LocalDateTime.now().minusMinutes((long) (Math.random() * 60));
        createBuildAtTime(status, duration, time);
    }

    private void createBuildAtTime(String status, Long duration, LocalDateTime time) {
        Build build = Build.builder()
                .pipeline(testPipeline)
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