package com.yourname.streamci.streamci.integration;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import com.yourname.streamci.streamci.service.MetricsService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * COMPREHENSIVE METRICS CALCULATION TESTS
 * Tests the brain of StreamCI - the metrics that drive predictions and alerts
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MetricsCalculationTest {

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    private PipelineMetricsRepository metricsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Pipeline testPipeline;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        // Create test pipeline
        testPipeline = Pipeline.builder()
                .name("TestPipeline")
                .status("active")
                .duration(300)
                .build();
        testPipeline = pipelineRepository.save(testPipeline);
    }

    @Test
    @Order(1)
    void testBasicSuccessRateCalculation() {
        System.out.println("\n=== TEST: Basic Success Rate Calculation ===");

        // Create 10 builds: 7 success, 3 failures
        createBuilds(7, "success", 300L);
        createBuilds(3, "failure", 450L);

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        assertThat(metrics).isNotNull();
        assertThat(metrics.getTotalBuilds()).isEqualTo(10);
        assertThat(metrics.getSuccessfulBuilds()).isEqualTo(7);
        assertThat(metrics.getFailedBuilds()).isEqualTo(3);
        assertThat(metrics.getSuccessRate()).isEqualTo(70.0);

        System.out.println("✅ Success rate: " + metrics.getSuccessRate() + "%");
    }

    @Test
    @Order(2)
    void testDurationMetricsCalculation() {
        System.out.println("\n=== TEST: Duration Metrics Calculation ===");

        // Create builds with varying durations: 100, 200, 300, 400, 500 seconds
        for (int i = 1; i <= 5; i++) {
            createBuild("success", (long) (i * 100));
        }

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        assertThat(metrics.getAvgDurationSeconds()).isEqualTo(300L); // (100+200+300+400+500)/5
        assertThat(metrics.getMinDurationSeconds()).isEqualTo(100L);
        assertThat(metrics.getMaxDurationSeconds()).isEqualTo(500L);

        System.out.println("✅ Avg duration: " + metrics.getAvgDurationSeconds() + "s");
        System.out.println("✅ Min duration: " + metrics.getMinDurationSeconds() + "s");
        System.out.println("✅ Max duration: " + metrics.getMaxDurationSeconds() + "s");
    }

    @Test
    @Order(3)
    void testConsecutiveFailuresTracking() {
        System.out.println("\n=== TEST: Consecutive Failures Tracking ===");

        LocalDateTime now = LocalDateTime.now();

        // Create pattern: success, success, failure, failure, failure (most recent)
        createBuildAtTime("success", 300L, now.minusHours(5));
        createBuildAtTime("success", 250L, now.minusHours(4));
        createBuildAtTime("failure", 400L, now.minusHours(3));
        createBuildAtTime("failure", 450L, now.minusHours(2));
        createBuildAtTime("failure", 500L, now.minusHours(1));

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        assertThat(metrics.getConsecutiveFailures()).isEqualTo(3);
        assertThat(metrics.getLastSuccess()).isBeforeOrEqualTo(now.minusHours(3));
        assertThat(metrics.getLastFailure()).isAfterOrEqualTo(now.minusHours(2));

        System.out.println("✅ Consecutive failures: " + metrics.getConsecutiveFailures());
    }

    @Test
    @Order(4)
    void testTimePatternAnalysis() {
        System.out.println("\n=== TEST: Time Pattern Analysis ===");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayMorning = now.withHour(9).withMinute(0).withSecond(0);
        LocalDateTime yesterdayEvening = now.minusDays(1).withHour(18).withMinute(0);

        // Create builds at different times
        createBuildAtTime("success", 300L, todayMorning);           // 9 AM today
        createBuildAtTime("success", 300L, todayMorning.plusHours(1)); // 10 AM today
        createBuildAtTime("failure", 400L, todayMorning.plusHours(2)); // 11 AM today
        createBuildAtTime("success", 250L, yesterdayEvening);      // 6 PM yesterday
        createBuildAtTime("success", 300L, todayMorning);           // 9 AM
        createBuildAtTime("success", 300L, todayMorning);           // 9 AM again
        createBuildAtTime("success", 300L, todayMorning.plusHours(1)); // 10 AM
        createBuildAtTime("failure", 400L, todayMorning.plusHours(2)); // 11 AM
        createBuildAtTime("success", 250L, yesterdayEvening);      // 6 PM (only one)

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        assertThat(metrics.getBuildsToday()).isEqualTo(7);
        assertThat(metrics.getBuildsThisWeek()).isGreaterThanOrEqualTo(4);

        // Peak hour should be around 9-11 AM (where we have 3 builds)
        assertThat(metrics.getPeakHour()).isIn("9", "10", "11");

        System.out.println("✅ Builds today: " + metrics.getBuildsToday());
        System.out.println("✅ Peak hour: " + metrics.getPeakHour());
    }

    @Test
    @Order(5)
    void testFailureAnalysisPattern() {
        System.out.println("\n=== TEST: Failure Analysis Pattern ===");

        LocalDateTime now = LocalDateTime.now();

        // Create failures mostly at 2 PM (14:00)
        LocalDateTime failureTime = now.withHour(14).withMinute(0);
        createBuildAtTime("failure", 400L, failureTime);
        createBuildAtTime("failure", 450L, failureTime.plusMinutes(30));
        createBuildAtTime("failure", 500L, failureTime.plusDays(1)); // Another day, same hour

        // Some successes at different times
        createBuildAtTime("success", 300L, now.withHour(10).withMinute(0));
        createBuildAtTime("success", 250L, now.withHour(16).withMinute(0));

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        // Most common failure time should be around 14:00 (2 PM)
        assertThat(metrics.getMostCommonFailureTime()).isEqualTo("14:00");

        System.out.println("✅ Most common failure time: " + metrics.getMostCommonFailureTime());
    }

    @Test
    @Order(6)
    void testTrendCalculationWithPreviousMetrics() {
        System.out.println("\n=== TEST: Trend Calculation ===");

        // First, create some builds and calculate initial metrics
        createBuilds(8, "success", 300L);
        createBuilds(2, "failure", 400L);

        PipelineMetrics firstMetrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        // Add more builds with worse performance
        createBuilds(5, "success", 500L); // Slower builds
        createBuilds(5, "failure", 600L); // More failures

        PipelineMetrics secondMetrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        // Success rate should have dropped (was 80%, now 65%)
        assertThat(secondMetrics.getSuccessRateChange()).isNegative();

        // Duration should have increased
        assertThat(secondMetrics.getAvgDurationChange()).isPositive();

        System.out.println("✅ Success rate change: " + secondMetrics.getSuccessRateChange() + "%");
        System.out.println("✅ Duration change: " + secondMetrics.getAvgDurationChange() + "s");
    }

    @Test
    @Order(7)
    void testMetricsForEmptyPipeline() {
        System.out.println("\n=== TEST: Empty Pipeline Metrics ===");

        // Create pipeline with no builds
        Pipeline emptyPipeline = Pipeline.builder()
                .name("EmptyPipeline")
                .status("active")
                .duration(0)
                .build();
        emptyPipeline = pipelineRepository.save(emptyPipeline);

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(emptyPipeline.getId());

        assertThat(metrics).isNotNull();
        assertThat(metrics.getTotalBuilds()).isEqualTo(0);
        assertThat(metrics.getSuccessRate()).isEqualTo(0.0);
        assertThat(metrics.getAvgDurationSeconds()).isEqualTo(0L);
        assertThat(metrics.getConsecutiveFailures()).isEqualTo(0);

        System.out.println("✅ Empty pipeline handled gracefully");
    }

    @Test
    @Order(8)
    void testMetricsAccuracyWithRealWorldPattern() {
        System.out.println("\n=== TEST: Real-World Build Pattern ===");

        LocalDateTime now = LocalDateTime.now();

        // Simulate a week of builds with realistic patterns
        // Monday: Heavy development day - more builds, some failures
        simulateDayOfBuilds(now.with(DayOfWeek.MONDAY), 8, 6, 2); // 75% success

        // Tuesday-Thursday: Steady development
        simulateDayOfBuilds(now.with(DayOfWeek.TUESDAY), 5, 4, 1); // 80% success
        simulateDayOfBuilds(now.with(DayOfWeek.WEDNESDAY), 6, 5, 1); // 83% success
        simulateDayOfBuilds(now.with(DayOfWeek.THURSDAY), 4, 4, 0); // 100% success

        // Friday: Release day - fewer builds but higher success (more careful)
        simulateDayOfBuilds(now.with(DayOfWeek.FRIDAY), 3, 3, 0); // 100% success

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        // Overall success rate should be around 82% (22 success / 26 total)
        assertThat(metrics.getSuccessRate()).isBetween(80.0, 85.0);
        assertThat(metrics.getTotalBuilds()).isEqualTo(26);
        assertThat(metrics.getBuildsThisWeek()).isEqualTo(26);

        // Peak day should be Monday (8 builds)
        assertThat(metrics.getPeakDay()).isEqualTo("MONDAY");

        System.out.println("✅ Weekly pattern analysis:");
        System.out.println("   Total builds: " + metrics.getTotalBuilds());
        System.out.println("   Success rate: " + metrics.getSuccessRate() + "%");
        System.out.println("   Peak day: " + metrics.getPeakDay());
    }

    @Test
    @Order(9)
    void testMetricsHistoryRetrieval() {
        System.out.println("\n=== TEST: Metrics History ===");

        // Create builds and generate multiple metrics snapshots
        createBuilds(5, "success", 300L);
        createBuilds(1, "failure", 400L);

        PipelineMetrics metrics1 = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        // Wait a moment and create more builds
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        createBuilds(3, "success", 350L);
        createBuilds(2, "failure", 450L);

        PipelineMetrics metrics2 = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        // Test history retrieval
        List<PipelineMetrics> history = metricsService.getMetricsHistory(testPipeline.getId(), 1);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getCalculatedAt()).isBefore(history.get(1).getCalculatedAt());

        // Test latest metrics retrieval
        var latestMetrics = metricsService.getLatestMetrics(testPipeline.getId());
        assertThat(latestMetrics).isPresent();
        assertThat(latestMetrics.get().getId()).isEqualTo(metrics2.getId());

        System.out.println("✅ Metrics history tracked correctly");
    }

    @Test
    @Order(10)
    void testMetricsCalculationPerformance() {
        System.out.println("\n=== TEST: Metrics Performance with Large Dataset ===");

        // Create a lot of builds to test performance
        long startTime = System.currentTimeMillis();

        // 100 builds spread over time
        for (int i = 0; i < 100; i++) {
            String status = (i % 4 == 0) ? "failure" : "success"; // 25% failure rate
            createBuild(status, (long) (200 + (i % 10) * 50)); // Varying durations
        }

        long dataCreationTime = System.currentTimeMillis() - startTime;

        // Calculate metrics
        startTime = System.currentTimeMillis();
        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());
        long calculationTime = System.currentTimeMillis() - startTime;

        assertThat(metrics.getTotalBuilds()).isEqualTo(100);
        assertThat(metrics.getSuccessRate()).isEqualTo(75.0); // 75 success / 100 total

        // Performance check - should be reasonably fast
        assertThat(calculationTime).isLessThan(5000L); // Less than 5 seconds

        System.out.println("✅ Performance test:");
        System.out.println("   Data creation: " + dataCreationTime + "ms");
        System.out.println("   Metrics calculation: " + calculationTime + "ms");
        System.out.println("   Builds processed: " + metrics.getTotalBuilds());
    }

    @Test
    @Order(11)
    void testEdgeCaseHandling() {
        System.out.println("\n=== TEST: Edge Cases ===");

        // Test with builds that have null durations
        Build buildWithNullDuration = Build.builder()
                .pipeline(testPipeline)
                .status("success")
                .duration(null)
                .commitHash("abc123")
                .committer("testuser")
                .branch("main")
                .build();
        buildRepository.save(buildWithNullDuration);

        // Test with builds that have zero duration
        createBuild("success", 0L);

        // Test with valid builds
        createBuilds(3, "success", 300L);

        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(testPipeline.getId());

        // Should handle null/zero durations gracefully
        assertThat(metrics.getTotalBuilds()).isEqualTo(5);
        assertThat(metrics.getAvgDurationSeconds()).isEqualTo(225L); // Only counts valid durations

        System.out.println("✅ Edge cases handled gracefully");
        System.out.println("   Total builds: " + metrics.getTotalBuilds());
        System.out.println("   Avg duration: " + metrics.getAvgDurationSeconds() + "s");
    }

    // Helper methods
    private void createBuilds(int count, String status, Long duration) {
        for (int i = 0; i < count; i++) {
            createBuild(status, duration);
        }
    }

    private void createBuild(String status, Long duration) {
        createBuildAtTime(status, duration, LocalDateTime.now().minusMinutes((long) (Math.random() * 1440)));
    }

    private void createBuildAtTime(String status, Long duration, LocalDateTime time) {
        Build build = Build.builder()
                .pipeline(testPipeline)
                .status(status)
                .duration(duration)
                .commitHash("commit" + Math.random())
                .committer("testuser")
                .branch("main")
                .createdAt(time)
                .startTime(time)
                .endTime(time.plusSeconds(duration != null ? duration : 300))
                .build();
        buildRepository.save(build);
    }

    private void simulateDayOfBuilds(LocalDateTime dayStart, int totalBuilds, int successes, int failures) {
        // Spread builds throughout the day (9 AM to 6 PM)
        for (int i = 0; i < successes; i++) {
            LocalDateTime buildTime = dayStart.withHour(9 + (i % 9)).withMinute((i * 17) % 60);
            createBuildAtTime("success", 300L + (i * 10), buildTime);
        }

        for (int i = 0; i < failures; i++) {
            LocalDateTime buildTime = dayStart.withHour(10 + (i % 8)).withMinute((i * 23) % 60);
            createBuildAtTime("failure", 450L + (i * 20), buildTime);
        }
    }

    private void cleanDatabase() {
        try {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("DELETE FROM pipeline_metrics");
            jdbcTemplate.execute("DELETE FROM build");
            jdbcTemplate.execute("DELETE FROM pipeline");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (Exception e) {
            try {
                metricsRepository.deleteAll();
                buildRepository.deleteAll();
                pipelineRepository.deleteAll();
            } catch (Exception ignored) {}
        }
    }
}