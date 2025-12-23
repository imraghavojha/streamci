package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.service.*;
import com.yourname.streamci.streamci.repository.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    private final PipelineService pipelineService;
    private final MetricsService metricsService;
    private final DashboardService dashboardService;
    private final AlertService alertService;
    private final QueueTrackerRepository queueTrackerRepository;
    private final EventCounter eventCounter;

    public DashboardController(PipelineService pipelineService,
                               MetricsService metricsService,
                               DashboardService dashboardService,
                               AlertService alertService,
                               QueueTrackerRepository queueTrackerRepository,
                               EventCounter eventCounter) {
        this.pipelineService = pipelineService;
        this.metricsService = metricsService;
        this.dashboardService = dashboardService;
        this.alertService = alertService;
        this.queueTrackerRepository = queueTrackerRepository;
        this.eventCounter = eventCounter;
    }

    /**
     * DASHBOARD SUMMARY ENDPOINT
     * GET /api/dashboard/summary - The "final backend piece"
     * Returns all key metrics in one consolidated response
     */
    @GetMapping("/summary")
//    @Cacheable("dashboard-summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        logger.info("Fetching dashboard summary");

        Map<String, Object> summary = new HashMap<>();

        // event counter metrics
        summary.put("events_per_minute", eventCounter.getEventsPerMinute());
        summary.put("total_events_processed", eventCounter.getTotalEvents());
        summary.put("uptime_minutes", eventCounter.getUptimeMinutes());

        summary.put("timestamp", LocalDateTime.now());
        summary.put("status", "success");

        try {
            // Get all pipelines for overview
            List<Pipeline> allPipelines = pipelineService.getAllPipelines();
            summary.put("total_pipelines", allPipelines.size());

            // Aggregate metrics across all pipelines
            Map<String, Object> aggregatedMetrics = dashboardService.calculateAggregatedMetrics(allPipelines);
            summary.put("overview", aggregatedMetrics);

            // get recent activity (last 24 hours)
            Map<String, Object> recentActivity = dashboardService.getRecentActivity();
            summary.put("recent_activity", recentActivity);

            // get current alerts
            List<Alert> activeAlerts = alertService.getActiveAlerts();
            summary.put("active_alerts", dashboardService.formatAlerts(activeAlerts));

            // get queue status
            Map<String, Object> queueOverview = dashboardService.getQueueOverview(allPipelines);
            summary.put("queue_status", queueOverview);

            // get pipeline-specific summaries
            List<Map<String, Object>> pipelineSummaries = allPipelines.stream()
                    .map(dashboardService::createPipelineSummary)
                    .collect(Collectors.toList());
            summary.put("pipelines", pipelineSummaries);

            logger.info("Dashboard summary calculated for {} pipelines", allPipelines.size());
            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            logger.error("Error calculating dashboard summary: {}", e.getMessage(), e);
            summary.put("status", "error");
            summary.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(summary);
        }
    }

    /**
     * REAL-TIME STATUS ENDPOINT
     * GET /api/dashboard/live - Shows what builds are currently running
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> getLiveStatus() {
        logger.info("Fetching live dashboard status");

        Map<String, Object> liveStatus = new HashMap<>();
        liveStatus.put("timestamp", LocalDateTime.now());

        try {
            // performance optimization: query by status instead of loading all
            List<QueueTracker> runningBuilds = queueTrackerRepository.findByStatus("running");
            List<QueueTracker> queuedBuilds = queueTrackerRepository.findByStatus("queued");

            // format running builds
            List<Map<String, Object>> runningStatus = runningBuilds.stream()
                    .map(dashboardService::formatRunningBuild)
                    .collect(Collectors.toList());

            // format queued builds
            List<Map<String, Object>> queuedStatus = queuedBuilds.stream()
                    .map(dashboardService::formatQueuedBuild)
                    .collect(Collectors.toList());

            liveStatus.put("running_builds", runningStatus);
            liveStatus.put("queued_builds", queuedStatus);
            liveStatus.put("total_running", runningBuilds.size());
            liveStatus.put("total_queued", queuedBuilds.size());

            // add system health indicators
            liveStatus.put("system_health", dashboardService.calculateSystemHealth());

            logger.info("Live status: {} running, {} queued", runningBuilds.size(), queuedBuilds.size());
            return ResponseEntity.ok(liveStatus);

        } catch (Exception e) {
            logger.error("Error fetching live status: {}", e.getMessage(), e);
            liveStatus.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(liveStatus);
        }
    }

    /**
     * HISTORICAL TRENDS ENDPOINT
     * GET /api/trends?days=7 - Charts data for the last week/month
     */
    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getHistoricalTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Integer pipelineId) {

        logger.info("Fetching trends for {} days, pipeline: {}", days, pipelineId);

        Map<String, Object> trends = new HashMap<>();
        trends.put("timestamp", LocalDateTime.now());
        trends.put("period_days", days);

        try {
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);

            if (pipelineId != null) {
                // single pipeline trends
                trends.put("pipeline_id", pipelineId);
                trends.putAll(dashboardService.calculatePipelineTrends(pipelineId, startDate));
            } else {
                // all pipelines trends
                trends.putAll(dashboardService.calculateGlobalTrends(startDate));
            }

            logger.info("Trends calculated for {} day period", days);
            return ResponseEntity.ok(trends);

        } catch (Exception e) {
            logger.error("Error calculating trends: {}", e.getMessage(), e);
            trends.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(trends);
        }
    }

    /**
     * PIPELINE HEALTH SCORE
     * GET /api/dashboard/health/{pipelineId} - Detailed health analysis
     */
    @GetMapping("/health/{pipelineId}")
    public ResponseEntity<Map<String, Object>> getPipelineHealth(@PathVariable Integer pipelineId) {
        logger.info("Calculating health score for pipeline {}", pipelineId);

        Map<String, Object> health = new HashMap<>();
        health.put("pipeline_id", pipelineId);
        health.put("timestamp", LocalDateTime.now());

        try {
            Optional<PipelineMetrics> latestMetrics = metricsService.getLatestMetrics(pipelineId);
            if (latestMetrics.isEmpty()) {
                health.put("status", "no_data");
                health.put("health_score", 0);
                return ResponseEntity.ok(health);
            }

            PipelineMetrics metrics = latestMetrics.get();

            // calculate health score (0-100)
            double healthScore = dashboardService.calculateHealthScore(metrics);
            health.put("health_score", Math.round(healthScore));

            // get health indicators
            health.put("indicators", dashboardService.getHealthIndicators(metrics, pipelineId));

            // get recommendations
            health.put("recommendations", dashboardService.getHealthRecommendations(metrics, pipelineId));

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            logger.error("Error calculating pipeline health: {}", e.getMessage(), e);
            health.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(health);
        }
    }
}
