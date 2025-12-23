package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * service for dashboard business logic
 * extracted from dashboard controller to follow SRP
 */
@Service
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    private final PipelineService pipelineService;
    private final MetricsService metricsService;
    private final QueueService queueService;
    private final AlertService alertService;
    private final BuildRepository buildRepository;
    private final PipelineMetricsRepository metricsRepository;
    private final QueueTrackerRepository queueTrackerRepository;
    private final AlertRepository alertRepository;

    public DashboardService(PipelineService pipelineService,
                            MetricsService metricsService,
                            QueueService queueService,
                            AlertService alertService,
                            BuildRepository buildRepository,
                            PipelineMetricsRepository metricsRepository,
                            QueueTrackerRepository queueTrackerRepository,
                            AlertRepository alertRepository) {
        this.pipelineService = pipelineService;
        this.metricsService = metricsService;
        this.queueService = queueService;
        this.alertService = alertService;
        this.buildRepository = buildRepository;
        this.metricsRepository = metricsRepository;
        this.queueTrackerRepository = queueTrackerRepository;
        this.alertRepository = alertRepository;
    }

    public Map<String, Object> calculateAggregatedMetrics(List<Pipeline> pipelines) {
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

    public Map<String, Object> getRecentActivity() {
        Map<String, Object> activity = new HashMap<>();
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);

        // performance optimization: use database queries instead of loading all and filtering
        List<Build> recentBuilds = buildRepository.findByStartTimeAfter(oneDayAgo);

        activity.put("builds_last_24h", recentBuilds.size());
        activity.put("successful_builds_last_24h",
                recentBuilds.stream().filter(b -> "success".equals(b.getStatus())).count());
        activity.put("failed_builds_last_24h",
                recentBuilds.stream().filter(b -> "failure".equals(b.getStatus())).count());

        // performance optimization: use count query instead of loading all
        long recentAlerts = alertRepository.countByCreatedAtAfter(oneDayAgo);
        activity.put("alerts_last_24h", recentAlerts);

        return activity;
    }

    public List<Map<String, Object>> formatAlerts(List<Alert> alerts) {
        return alerts.stream()
                .limit(10) // latest 10 alerts
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

    public Map<String, Object> getQueueOverview(List<Pipeline> pipelines) {
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

    public Map<String, Object> createPipelineSummary(Pipeline pipeline) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", pipeline.getId());
        summary.put("name", pipeline.getName());
        summary.put("status", pipeline.getStatus());

        // get latest metrics
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

        // get current queue status
        Map<String, Object> queueStatus = queueService.getQueueStatus(pipeline.getId());
        summary.put("queue_depth", queueStatus.get("current_depth"));

        // get active alerts count
        List<Alert> pipelineAlerts = alertService.getAlertsForPipeline(pipeline.getId());
        summary.put("active_alerts", pipelineAlerts.size());

        return summary;
    }

    public Map<String, Object> formatRunningBuild(QueueTracker tracker) {
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

    public Map<String, Object> formatQueuedBuild(QueueTracker tracker) {
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

    public Map<String, Object> calculateSystemHealth() {
        Map<String, Object> health = new HashMap<>();

        // performance optimization: use count query instead of loading all
        long totalActive = queueTrackerRepository.countAllActive();

        health.put("active_builds", totalActive);
        health.put("system_load", totalActive > 10 ? "high" : totalActive > 5 ? "medium" : "low");
        health.put("status", totalActive > 20 ? "overloaded" : "healthy");

        return health;
    }

    public Map<String, Object> calculatePipelineTrends(Integer pipelineId, LocalDateTime startDate) {
        Map<String, Object> trends = new HashMap<>();

        // get metrics history
        List<PipelineMetrics> history = metricsRepository
                .findByPipelineIdAndCalculatedAtBetween(pipelineId, startDate, LocalDateTime.now());

        // success rate trend
        List<Map<String, Object>> successRateTrend = history.stream()
                .map(m -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", m.getCalculatedAt());
                    point.put("success_rate", m.getSuccessRate());
                    return point;
                })
                .collect(Collectors.toList());

        // duration trend
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

    public Map<String, Object> calculateGlobalTrends(LocalDateTime startDate) {
        Map<String, Object> trends = new HashMap<>();

        // performance optimization: query by date range instead of loading all
        List<PipelineMetrics> allMetrics = metricsRepository.findByCalculatedAtAfter(startDate);

        // aggregate by time periods (daily)
        Map<String, Object> dailyTrends = aggregateMetricsByDay(allMetrics);
        trends.put("daily_trends", dailyTrends);

        // overall statistics
        if (!allMetrics.isEmpty()) {
            double avgSuccessRate = allMetrics.stream()
                    .mapToDouble(PipelineMetrics::getSuccessRate)
                    .average()
                    .orElse(0);
            trends.put("average_success_rate", Math.round(avgSuccessRate * 10) / 10.0);
        }

        return trends;
    }

    public Map<String, Object> aggregateMetricsByDay(List<PipelineMetrics> metrics) {
        // group by day and calculate averages
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

    public double calculateHealthScore(PipelineMetrics metrics) {
        double score = 0;

        // success rate (40% weight)
        score += (metrics.getSuccessRate() * 0.4);

        // build frequency (20% weight) - more builds = healthier
        int buildScore = Math.min(metrics.getTotalBuilds(), 50) * 2; // cap at 100
        score += (buildScore * 0.2);

        // trend (20% weight)
        if (metrics.getSuccessRateChange() != null) {
            if (metrics.getSuccessRateChange() >= 0) {
                score += 20; // positive trend
            } else {
                score += Math.max(0, 20 + metrics.getSuccessRateChange()); // negative trend penalty
            }
        } else {
            score += 10; // neutral if no trend data
        }

        // duration stability (20% weight)
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

    public Map<String, Object> getHealthIndicators(PipelineMetrics metrics, Integer pipelineId) {
        Map<String, Object> indicators = new HashMap<>();

        // success rate indicator
        if (metrics.getSuccessRate() >= 95) {
            indicators.put("success_rate", Map.of("status", "excellent", "value", metrics.getSuccessRate()));
        } else if (metrics.getSuccessRate() >= 80) {
            indicators.put("success_rate", Map.of("status", "good", "value", metrics.getSuccessRate()));
        } else if (metrics.getSuccessRate() >= 60) {
            indicators.put("success_rate", Map.of("status", "warning", "value", metrics.getSuccessRate()));
        } else {
            indicators.put("success_rate", Map.of("status", "critical", "value", metrics.getSuccessRate()));
        }

        // build frequency
        int buildsPerDay = metrics.getBuildsToday() != null ? metrics.getBuildsToday() : 0;
        String frequency = buildsPerDay > 10 ? "high" : buildsPerDay > 3 ? "normal" : "low";
        indicators.put("build_frequency", Map.of("status", frequency, "builds_today", buildsPerDay));

        // duration trends
        if (metrics.getAvgDurationChange() != null) {
            String durationTrend = metrics.getAvgDurationChange() < -60 ? "improving" :
                    metrics.getAvgDurationChange() > 60 ? "degrading" : "stable";
            indicators.put("duration_trend", Map.of("status", durationTrend,
                    "change_seconds", metrics.getAvgDurationChange()));
        }

        return indicators;
    }

    public List<String> getHealthRecommendations(PipelineMetrics metrics, Integer pipelineId) {
        List<String> recommendations = new ArrayList<>();

        if (metrics.getSuccessRate() < 80) {
            recommendations.add("success rate is below 80% - review recent failures and fix flaky tests");
        }

        if (metrics.getAvgDurationChange() != null && metrics.getAvgDurationChange() > 300) {
            recommendations.add("build duration increased by " + (metrics.getAvgDurationChange() / 60) +
                    " minutes - check for new dependencies or resource constraints");
        }

        if (metrics.getConsecutiveFailures() != null && metrics.getConsecutiveFailures() > 3) {
            recommendations.add("multiple consecutive failures detected - urgent investigation needed");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("pipeline health looks good! continue current practices.");
        }

        return recommendations;
    }
}
