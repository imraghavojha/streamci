package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * unit tests for dashboard service
 * verifies business logic extracted from controller
 */
class DashboardServiceTest {

    @Mock
    private PipelineService pipelineService;

    @Mock
    private MetricsService metricsService;

    @Mock
    private QueueService queueService;

    @Mock
    private AlertService alertService;

    @Mock
    private BuildRepository buildRepository;

    @Mock
    private PipelineMetricsRepository metricsRepository;

    @Mock
    private QueueTrackerRepository queueTrackerRepository;

    @Mock
    private AlertRepository alertRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dashboardService = new DashboardService(
                pipelineService,
                metricsService,
                queueService,
                alertService,
                buildRepository,
                metricsRepository,
                queueTrackerRepository,
                alertRepository
        );
    }

    @Test
    void testCalculateAggregatedMetrics_WithData() {
        // arrange
        Pipeline pipeline1 = new Pipeline();
        pipeline1.setId(1);

        Pipeline pipeline2 = new Pipeline();
        pipeline2.setId(2);

        List<Pipeline> pipelines = Arrays.asList(pipeline1, pipeline2);

        PipelineMetrics metrics1 = PipelineMetrics.builder()
                .successRate(80.0)
                .totalBuilds(100)
                .successfulBuilds(80)
                .failedBuilds(20)
                .build();

        PipelineMetrics metrics2 = PipelineMetrics.builder()
                .successRate(90.0)
                .totalBuilds(50)
                .successfulBuilds(45)
                .failedBuilds(5)
                .build();

        when(metricsService.getLatestMetrics(1)).thenReturn(Optional.of(metrics1));
        when(metricsService.getLatestMetrics(2)).thenReturn(Optional.of(metrics2));

        // act
        Map<String, Object> result = dashboardService.calculateAggregatedMetrics(pipelines);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.get("total_builds")).isEqualTo(150L);
        assertThat(result.get("total_successful_builds")).isEqualTo(125L);
        assertThat(result.get("total_failed_builds")).isEqualTo(25L);
        assertThat(result.get("pipelines_with_data")).isEqualTo(2);
        // average success rate should be (80 + 90) / 2 = 85
        assertThat(result.get("average_success_rate")).isEqualTo(85.0);
    }

    @Test
    void testCalculateAggregatedMetrics_WithNoData() {
        // arrange
        List<Pipeline> pipelines = Collections.emptyList();

        // act
        Map<String, Object> result = dashboardService.calculateAggregatedMetrics(pipelines);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.get("total_builds")).isEqualTo(0L);
        assertThat(result.get("average_success_rate")).isEqualTo(0.0);
    }

    @Test
    void testCalculateHealthScore_HighSuccess() {
        // arrange
        PipelineMetrics metrics = PipelineMetrics.builder()
                .successRate(95.0)
                .totalBuilds(50)
                .successRateChange(5.0) // positive trend
                .avgDurationChange(30L) // stable duration (under 60 seconds)
                .build();

        // act
        double score = dashboardService.calculateHealthScore(metrics);

        // assert
        assertThat(score).isGreaterThanOrEqualTo(70.0); // should be a high score
        assertThat(score).isLessThanOrEqualTo(100.0);
    }

    @Test
    void testCalculateHealthScore_LowSuccess() {
        // arrange
        PipelineMetrics metrics = PipelineMetrics.builder()
                .successRate(50.0)
                .totalBuilds(10)
                .successRateChange(-10.0) // negative trend
                .avgDurationChange(400L) // unstable duration
                .build();

        // act
        double score = dashboardService.calculateHealthScore(metrics);

        // assert
        assertThat(score).isLessThan(50.0); // should be a low score
        assertThat(score).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void testGetHealthRecommendations_HealthyPipeline() {
        // arrange
        PipelineMetrics metrics = PipelineMetrics.builder()
                .successRate(95.0)
                .avgDurationChange(10L)
                .consecutiveFailures(0)
                .build();

        // act
        List<String> recommendations = dashboardService.getHealthRecommendations(metrics, 1);

        // assert
        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0)).contains("pipeline health looks good");
    }

    @Test
    void testGetHealthRecommendations_LowSuccessRate() {
        // arrange
        PipelineMetrics metrics = PipelineMetrics.builder()
                .successRate(70.0)
                .avgDurationChange(10L)
                .consecutiveFailures(0)
                .build();

        // act
        List<String> recommendations = dashboardService.getHealthRecommendations(metrics, 1);

        // assert
        assertThat(recommendations).hasSizeGreaterThan(0);
        assertThat(recommendations.get(0)).contains("below 80%");
    }

    @Test
    void testGetHealthRecommendations_ConsecutiveFailures() {
        // arrange
        PipelineMetrics metrics = PipelineMetrics.builder()
                .successRate(95.0)
                .avgDurationChange(10L)
                .consecutiveFailures(5)
                .build();

        // act
        List<String> recommendations = dashboardService.getHealthRecommendations(metrics, 1);

        // assert
        assertThat(recommendations).hasSizeGreaterThan(0);
        boolean hasFailureWarning = recommendations.stream()
                .anyMatch(r -> r.contains("consecutive failures"));
        assertThat(hasFailureWarning).isTrue();
    }

    @Test
    void testCalculateSystemHealth_Healthy() {
        // arrange
        when(queueTrackerRepository.findAll()).thenReturn(Collections.emptyList());

        // act
        Map<String, Object> health = dashboardService.calculateSystemHealth();

        // assert
        assertThat(health).isNotNull();
        assertThat(health.get("active_builds")).isEqualTo(0L);
        assertThat(health.get("system_load")).isEqualTo("low");
        assertThat(health.get("status")).isEqualTo("healthy");
    }

    @Test
    void testFormatAlerts_LimitsTo10() {
        // arrange
        List<Alert> alerts = new ArrayList<>();
        Pipeline pipeline = new Pipeline();
        pipeline.setId(1);

        for (int i = 0; i < 15; i++) {
            Alert alert = new Alert();
            alert.setId((long) i);
            alert.setType(Alert.AlertType.SUCCESS_RATE_DROP);
            alert.setSeverity(Alert.AlertSeverity.WARNING);
            alert.setMessage("test alert " + i);
            alert.setPipeline(pipeline);
            alert.setCreatedAt(LocalDateTime.now());
            alerts.add(alert);
        }

        // act
        List<Map<String, Object>> formatted = dashboardService.formatAlerts(alerts);

        // assert
        assertThat(formatted).hasSize(10); // should be limited to 10
    }
}
