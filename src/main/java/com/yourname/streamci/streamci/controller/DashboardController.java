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
    private final QueueService queueService;
    private final AlertService alertService;
    private final BuildRepository buildRepository;
    private final PipelineMetricsRepository metricsRepository;
    private final QueueTrackerRepository queueTrackerRepository;
    private final AlertRepository alertRepository;
    private final EventCounter eventCounter;

    public DashboardController(PipelineService pipelineService,
                               MetricsService metricsService,
                               QueueService queueService,
                               AlertService alertService,
                               BuildRepository buildRepository,
                               PipelineMetricsRepository metricsRepository,
                               QueueTrackerRepository queueTrackerRepository,
                               AlertRepository alertRepository, EventCounter eventCounter) {
        this.pipelineService = pipelineService;
        this.metricsService = metricsService;
        this.queueService = queueService;
        this.alertService = alertService;
        this.buildRepository = buildRepository;
        this.metricsRepository = metricsRepository;
        this.queueTrackerRepository = queueTrackerRepository;
        this.alertRepository = alertRepository;
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
            Map<String, Object> aggregatedMetrics = calculateAggregatedMetrics(allPipelines);
            summary.put("overview", aggregatedMetrics);

            // Get recent activity (last 24 hours)
            Map<String, Object> recentActivity = getRecentActivity();
            summary.put("recent_activity", recentActivity);

            // Get current alerts
            List<Alert> activeAlerts = alertService.getActiveAlerts();
            summary.put("active_alerts", formatAlerts(activeAlerts));

            // Get queue status
            Map<String, Object> queueOverview = getQueueOverview(allPipelines);
            summary.put("queue_status", queueOverview);

            // Get pipeline-specific summaries
            List<Map<String, Object>> pipelineSummaries = allPipelines.stream()
                    .map(this::createPipelineSummary)
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
            // Get all currently running builds
            List<QueueTracker> runningBuilds = queueTrackerRepository
                    .findAll()
                    .stream()
                    .filter(qt -> "running".equals(qt.getStatus()))
                    .collect(Collectors.toList());

            // Get all queued builds
            List<QueueTracker> queuedBuilds = queueTrackerRepository
                    .findAll()
                    .stream()
                    .filter(qt -> "queued".equals(qt.getStatus()))
                    .collect(Collectors.toList());

            // Format running builds
            List<Map<String, Object>> runningStatus = runningBuilds.stream()
                    .map(this::formatRunningBuild)
                    .collect(Collectors.toList());

            // Format queued builds
            List<Map<String, Object>> queuedStatus = queuedBuilds.stream()
                    .map(this::formatQueuedBuild)
                    .collect(Collectors.toList());

            liveStatus.put("running_builds", runningStatus);
            liveStatus.put("queued_builds", queuedStatus);
            liveStatus.put("total_running", runningBuilds.size());
            liveStatus.put("total_queued", queuedBuilds.size());

            // Add system health indicators
            liveStatus.put("system_health", calculateSystemHealth());

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
                // Single pipeline trends
                trends.put("pipeline_id", pipelineId);
                trends.putAll(calculatePipelineTrends(pipelineId, startDate));
            } else {
                // All pipelines trends
                trends.putAll(calculateGlobalTrends(startDate));
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
            double healthScore = calculateHealthScore(metrics);
            health.put("health_score", Math.round(healthScore));

            // get health indicators
            health.put("indicators", getHealthIndicators(metrics, pipelineId));

            // get recommendations
            health.put("recommendations", getHealthRecommendations(metrics, pipelineId));

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            logger.error("Error calculating pipeline health: {}", e.getMessage(), e);
            health.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(health);
        }
    }

    // ============helper methods ============

    private Map<String, Object> calculateAggregatedMetrics(List<Pipeline> pipelines) {
        Map<String, Object> metrics = new HashMap<>();

        double totalSuccessRate = 0;
        long totalBuilds = 0;
        long totalSuccessfulBuilds = 0;
        long totalFailedBuilds = 0;
        int pipelinesWithData = 0;

        for (Pipeline pipeline : pipelines) {
            Optional<PipelineMetrics> latest = metricsService.getLatestMetrics(pipeline.getId());
            if (latest.isPresent()) {
                PipelineMetrics pm = latest.get();
                totalSuccessRate += pm.getSuccessRate();
                totalBuilds += pm.getTotalBuilds();
                totalSuccessfulBuilds += pm.getSuccessfulBuilds();
                totalFailedBuilds += pm.getFailedBuilds();
                pipelinesWithData++;
            }
        }

        metrics.put("average_success_rate", pipelinesWithData > 0 ?
                Math.round(totalSuccessRate / pipelinesWithData * 10) / 10.0 : 0);
        metrics.put("total_builds", totalBuilds);
        metrics.put("total_successful_builds", totalSuccessfulBuilds);
        metrics.put("total_failed_builds", totalFailedBuilds);
        metrics.put("pipelines_with_data", pipelinesWithData);

        return metrics;
    }

    private Map<String, Object> getRecentActivity() {
        Map<String, Object> activity = new HashMap<>();
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);

        // Recent builds count
        List<Build> recentBuilds = buildRepository.findAll()
                .stream()
                .filter(b -> b.getStartTime() != null && b.getStartTime().isAfter(oneDayAgo))
                .collect(Collectors.toList());

        activity.put("builds_last_24h", recentBuilds.size());
        activity.put("successful_builds_last_24h",
                recentBuilds.stream().filter(b -> "success".equals(b.getStatus())).count());
        activity.put("failed_builds_last_24h",
                recentBuilds.stream().filter(b -> "failure".equals(b.getStatus())).count());

        // Recent alerts
        long recentAlerts = alertRepository.findAll()
                .stream()
                .filter(a -> a.getCreatedAt().isAfter(oneDayAgo))
                .count();
        activity.put("alerts_last_24h", recentAlerts);

        return activity;
    }

    private List<Map<String, Object>> formatAlerts(List<Alert> alerts) {
        return alerts.stream()
                .limit(10) // Latest 10 alerts
                .map(alert -> {
                    Map<String, Object> alertData = new HashMap<>();
                    alertData.put("id", alert.getId());
                    alertData.put("type", alert.getType().toString());
                    alertData.put("severity", alert.getSeverity().toString());
                    alertData.put("message", alert.getMessage());
                    alertData.put("pipeline_id", alert.getPipeline() != null ? alert.getPipeline().getId() : null);
                    alertData.put("created_at", alert.getCreatedAt());
                    return alertData;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> getQueueOverview(List<Pipeline> pipelines) {
        Map<String, Object> queueOverview = new HashMap<>();

        int totalQueued = 0;
        int totalRunning = 0;

        for (Pipeline pipeline : pipelines) {
            List<QueueTracker> queued = queueTrackerRepository
                    .findByPipelineIdAndStatus(pipeline.getId(), "queued");
            List<QueueTracker> running = queueTrackerRepository
                    .findByPipelineIdAndStatus(pipeline.getId(), "running");

            totalQueued += queued.size();
            totalRunning += running.size();
        }

        queueOverview.put("total_queued", totalQueued);
        queueOverview.put("total_running", totalRunning);
        queueOverview.put("is_busy", totalQueued > 5 || totalRunning > 3);

        return queueOverview;
    }

    private Map<String, Object> createPipelineSummary(Pipeline pipeline) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", pipeline.getId());
        summary.put("name", pipeline.getName());
        summary.put("status", pipeline.getStatus());

        // Get latest metrics
        Optional<PipelineMetrics> metrics = metricsService.getLatestMetrics(pipeline.getId());
        if (metrics.isPresent()) {
            PipelineMetrics pm = metrics.get();
            summary.put("success_rate", pm.getSuccessRate());
            summary.put("total_builds", pm.getTotalBuilds());
            summary.put("avg_duration_seconds", pm.getAvgDurationSeconds());
            summary.put("last_calculated", pm.getCalculatedAt());
        } else {
            summary.put("success_rate", 0);
            summary.put("total_builds", 0);
            summary.put("avg_duration_seconds", 0);
        }

        // Get current queue status
        Map<String, Object> queueStatus = queueService.getQueueStatus(pipeline.getId());
        summary.put("queue_depth", queueStatus.get("current_depth"));

        // Get active alerts count
        List<Alert> pipelineAlerts = alertService.getAlertsForPipeline(pipeline.getId());
        summary.put("active_alerts", pipelineAlerts.size());

        return summary;
    }

    private Map<String, Object> formatRunningBuild(QueueTracker tracker) {
        Map<String, Object> build = new HashMap<>();
        build.put("build_id", tracker.getBuildId());
        build.put("pipeline_id", tracker.getPipeline().getId());
        build.put("pipeline_name", tracker.getPipeline().getName());
        build.put("started_at", tracker.getStartedAt());
        build.put("duration_seconds", tracker.getStartedAt() != null ?
                ChronoUnit.SECONDS.between(tracker.getStartedAt(), LocalDateTime.now()) : 0);
        build.put("status", "running");
        return build;
    }

    private Map<String, Object> formatQueuedBuild(QueueTracker tracker) {
        Map<String, Object> build = new HashMap<>();
        build.put("build_id", tracker.getBuildId());
        build.put("pipeline_id", tracker.getPipeline().getId());
        build.put("pipeline_name", tracker.getPipeline().getName());
        build.put("queued_at", tracker.getQueuedAt());
        build.put("wait_time_seconds",
                ChronoUnit.SECONDS.between(tracker.getQueuedAt(), LocalDateTime.now()));
        build.put("status", "queued");
        return build;
    }

    private Map<String, Object> calculateSystemHealth() {
        Map<String, Object> health = new HashMap<>();

        // Simple system health indicators
        long totalActive = queueTrackerRepository.findAll().stream()
                .filter(qt -> "running".equals(qt.getStatus()) || "queued".equals(qt.getStatus()))
                .count();

        health.put("active_builds", totalActive);
        health.put("system_load", totalActive > 10 ? "high" : totalActive > 5 ? "medium" : "low");
        health.put("status", totalActive > 20 ? "overloaded" : "healthy");

        return health;
    }

    private Map<String, Object> calculatePipelineTrends(Integer pipelineId, LocalDateTime startDate) {
        Map<String, Object> trends = new HashMap<>();

        // Get metrics history
        List<PipelineMetrics> history = metricsRepository
                .findByPipelineIdAndCalculatedAtBetween(pipelineId, startDate, LocalDateTime.now());

        // Success rate trend
        List<Map<String, Object>> successRateTrend = history.stream()
                .map(m -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", m.getCalculatedAt());
                    point.put("success_rate", m.getSuccessRate());
                    return point;
                })
                .collect(Collectors.toList());

        // Duration trend
        List<Map<String, Object>> durationTrend = history.stream()
                .map(m -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", m.getCalculatedAt());
                    point.put("avg_duration", m.getAvgDurationSeconds());
                    return point;
                })
                .collect(Collectors.toList());

        trends.put("success_rate_trend", successRateTrend);
        trends.put("duration_trend", durationTrend);
        trends.put("data_points", history.size());

        return trends;
    }

    private Map<String, Object> calculateGlobalTrends(LocalDateTime startDate) {
        Map<String, Object> trends = new HashMap<>();

        // Global metrics across all pipelines
        List<PipelineMetrics> allMetrics = metricsRepository
                .findAll()
                .stream()
                .filter(m -> m.getCalculatedAt().isAfter(startDate))
                .collect(Collectors.toList());

        // Aggregate by time periods (daily)
        Map<String, Object> dailyTrends = aggregateMetricsByDay(allMetrics);
        trends.put("daily_trends", dailyTrends);

        // Overall statistics
        if (!allMetrics.isEmpty()) {
            double avgSuccessRate = allMetrics.stream()
                    .mapToDouble(PipelineMetrics::getSuccessRate)
                    .average()
                    .orElse(0);
            trends.put("average_success_rate", Math.round(avgSuccessRate * 10) / 10.0);
        }

        return trends;
    }

    private Map<String, Object> aggregateMetricsByDay(List<PipelineMetrics> metrics) {
        // Group by day and calculate averages
        Map<String, List<PipelineMetrics>> byDay = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getCalculatedAt().toLocalDate().toString()));

        List<Map<String, Object>> dailyData = byDay.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> dayData = new HashMap<>();
                    dayData.put("date", entry.getKey());

                    List<PipelineMetrics> dayMetrics = entry.getValue();
                    double avgSuccess = dayMetrics.stream()
                            .mapToDouble(PipelineMetrics::getSuccessRate)
                            .average()
                            .orElse(0);

                    dayData.put("avg_success_rate", Math.round(avgSuccess * 10) / 10.0);
                    dayData.put("total_builds", dayMetrics.stream()
                            .mapToInt(PipelineMetrics::getTotalBuilds)
                            .sum());

                    return dayData;
                })
                .sorted((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("daily_data", dailyData);
        return result;
    }

    private double calculateHealthScore(PipelineMetrics metrics) {
        double score = 0;

        // Success rate (40% weight)
        score += (metrics.getSuccessRate() * 0.4);

        // Build frequency (20% weight) - more builds = healthier
        int buildScore = Math.min(metrics.getTotalBuilds(), 50) * 2; // cap at 100
        score += (buildScore * 0.2);

        // Trend (20% weight)
        if (metrics.getSuccessRateChange() != null) {
            if (metrics.getSuccessRateChange() >= 0) {
                score += 20; // positive trend
            } else {
                score += Math.max(0, 20 + metrics.getSuccessRateChange()); // negative trend penalty
            }
        } else {
            score += 10; // neutral if no trend data
        }

        // Duration stability (20% weight)
        if (metrics.getAvgDurationChange() != null) {
            long durationChange = Math.abs(metrics.getAvgDurationChange());
            if (durationChange < 60) { // less than 1 minute change
                score += 20;
            } else if (durationChange < 300) { // less than 5 minutes
                score += 10;
            } // else 0 points
        } else {
            score += 10; // neutral
        }

        return Math.min(100, Math.max(0, score));
    }

    private Map<String, Object> getHealthIndicators(PipelineMetrics metrics, Integer pipelineId) {
        Map<String, Object> indicators = new HashMap<>();

        // Success rate indicator
        if (metrics.getSuccessRate() >= 95) {
            indicators.put("success_rate", Map.of("status", "excellent", "value", metrics.getSuccessRate()));
        } else if (metrics.getSuccessRate() >= 80) {
            indicators.put("success_rate", Map.of("status", "good", "value", metrics.getSuccessRate()));
        } else if (metrics.getSuccessRate() >= 60) {
            indicators.put("success_rate", Map.of("status", "warning", "value", metrics.getSuccessRate()));
        } else {
            indicators.put("success_rate", Map.of("status", "critical", "value", metrics.getSuccessRate()));
        }

        // Build frequency
        int buildsPerDay = metrics.getBuildsToday() != null ? metrics.getBuildsToday() : 0;
        String frequency = buildsPerDay > 10 ? "high" : buildsPerDay > 3 ? "normal" : "low";
        indicators.put("build_frequency", Map.of("status", frequency, "builds_today", buildsPerDay));

        // Duration trends
        if (metrics.getAvgDurationChange() != null) {
            String durationTrend = metrics.getAvgDurationChange() < -60 ? "improving" :
                    metrics.getAvgDurationChange() > 60 ? "degrading" : "stable";
            indicators.put("duration_trend", Map.of("status", durationTrend,
                    "change_seconds", metrics.getAvgDurationChange()));
        }

        return indicators;
    }

    private List<String> getHealthRecommendations(PipelineMetrics metrics, Integer pipelineId) {
        List<String> recommendations = new ArrayList<>();

        if (metrics.getSuccessRate() < 80) {
            recommendations.add("Success rate is below 80% - review recent failures and fix flaky tests");
        }

        if (metrics.getAvgDurationChange() != null && metrics.getAvgDurationChange() > 300) {
            recommendations.add("Build duration increased by " + (metrics.getAvgDurationChange() / 60) +
                    " minutes - check for new dependencies or resource constraints");
        }

        if (metrics.getConsecutiveFailures() != null && metrics.getConsecutiveFailures() > 3) {
            recommendations.add("Multiple consecutive failures detected - urgent investigation needed");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Pipeline health looks good! Continue current practices.");
        }

        return recommendations;
    }
}