package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.service.*;
import com.yourname.streamci.streamci.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;

@RestController
@RequestMapping("/api/trends")
public class TrendsController {

    private static final Logger logger = LoggerFactory.getLogger(TrendsController.class);

    private final PipelineService pipelineService;
    private final MetricsService metricsService;
    private final BuildRepository buildRepository;
    private final PipelineMetricsRepository metricsRepository;
    private final QueueMetricsRepository queueMetricsRepository;

    public TrendsController(PipelineService pipelineService,
                            MetricsService metricsService,
                            BuildRepository buildRepository,
                            PipelineMetricsRepository metricsRepository,
                            QueueMetricsRepository queueMetricsRepository) {
        this.pipelineService = pipelineService;
        this.metricsService = metricsService;
        this.buildRepository = buildRepository;
        this.metricsRepository = metricsRepository;
        this.queueMetricsRepository = queueMetricsRepository;
    }

    /**
     * Main trends endpoint - GET /api/trends?days=7
     * Returns comprehensive trend data for charting
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Integer pipelineId,
            @RequestParam(defaultValue = "hourly") String granularity) {

        logger.info("Getting trends for {} days, pipeline: {}, granularity: {}",
                days, pipelineId, granularity);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("period_days", days);
        response.put("granularity", granularity);

        try {
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            LocalDateTime endDate = LocalDateTime.now();

            if (pipelineId != null) {
                // Single pipeline trends
                response.put("pipeline_id", pipelineId);
                response.putAll(calculateSinglePipelineTrends(pipelineId, startDate, endDate, granularity));
            } else {
                // Global trends across all pipelines
                response.putAll(calculateGlobalTrends(startDate, endDate, granularity));
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error calculating trends: {}", e.getMessage(), e);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Success rate trends - GET /api/trends/success-rate
     */
    @GetMapping("/success-rate")
    public ResponseEntity<Map<String, Object>> getSuccessRateTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Integer pipelineId) {

        Map<String, Object> response = new HashMap<>();
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        try {
            List<Map<String, Object>> successRateData;

            if (pipelineId != null) {
                successRateData = calculateSuccessRateByTimeRange(pipelineId, startDate);
                response.put("pipeline_id", pipelineId);
            } else {
                successRateData = calculateGlobalSuccessRateByTimeRange(startDate);
            }

            response.put("success_rate_data", successRateData);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error calculating success rate trends: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Build duration trends - GET /api/trends/duration
     */
    @GetMapping("/duration")
    public ResponseEntity<Map<String, Object>> getDurationTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Integer pipelineId) {

        Map<String, Object> response = new HashMap<>();
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        try {
            List<Map<String, Object>> durationData = calculateDurationTrends(pipelineId, startDate);

            response.put("duration_data", durationData);
            response.put("pipeline_id", pipelineId);
            response.put("timestamp", LocalDateTime.now());

            // Calculate duration statistics
            response.put("statistics", calculateDurationStatistics(durationData));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error calculating duration trends: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Build frequency trends - GET /api/trends/frequency
     */
    @GetMapping("/frequency")
    public ResponseEntity<Map<String, Object>> getBuildFrequencyTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Integer pipelineId) {

        Map<String, Object> response = new HashMap<>();
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        try {
            List<Map<String, Object>> frequencyData = calculateBuildFrequency(pipelineId, startDate);

            response.put("frequency_data", frequencyData);
            response.put("pipeline_id", pipelineId);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error calculating frequency trends: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Queue depth trends - GET /api/trends/queue
     */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueueTrends(
            @RequestParam(defaultValue = "1") int days,
            @RequestParam(required = false) Integer pipelineId) {

        Map<String, Object> response = new HashMap<>();
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        try {
            List<Map<String, Object>> queueData = calculateQueueTrends(pipelineId, startDate);

            response.put("queue_data", queueData);
            response.put("pipeline_id", pipelineId);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error calculating queue trends: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Comparative trends across multiple pipelines
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> getComparativeTrends(
            @RequestParam List<Integer> pipelineIds,
            @RequestParam(defaultValue = "7") int days) {

        Map<String, Object> response = new HashMap<>();
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        try {
            List<Map<String, Object>> comparativeData = new ArrayList<>();

            for (Integer pipelineId : pipelineIds) {
                Map<String, Object> pipelineData = new HashMap<>();
                pipelineData.put("pipeline_id", pipelineId);

                // Get pipeline name
                Optional<Pipeline> pipeline = pipelineService.getPipelineById(pipelineId);
                pipelineData.put("pipeline_name", pipeline.map(Pipeline::getName).orElse("Unknown"));

                // Get success rate trend
                List<Map<String, Object>> successRateData = calculateSuccessRateByTimeRange(pipelineId, startDate);
                pipelineData.put("success_rate_trend", successRateData);

                // Get duration trend
                List<Map<String, Object>> durationData = calculateDurationTrends(pipelineId, startDate);
                pipelineData.put("duration_trend", durationData);

                comparativeData.add(pipelineData);
            }

            response.put("comparative_data", comparativeData);
            response.put("pipeline_ids", pipelineIds);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error calculating comparative trends: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ============ HELPER METHODS ============

    private Map<String, Object> calculateSinglePipelineTrends(Integer pipelineId,
                                                              LocalDateTime startDate,
                                                              LocalDateTime endDate,
                                                              String granularity) {
        Map<String, Object> trends = new HashMap<>();

        // Get metrics history
        List<PipelineMetrics> metricsHistory = metricsRepository
                .findByPipelineIdAndCalculatedAtBetween(pipelineId, startDate, endDate);

        // Success rate over time
        List<Map<String, Object>> successRatePoints = metricsHistory.stream()
                .map(m -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", m.getCalculatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    point.put("success_rate", m.getSuccessRate());
                    point.put("total_builds", m.getTotalBuilds());
                    return point;
                })
                .collect(Collectors.toList());

        // Duration trends
        List<Map<String, Object>> durationPoints = metricsHistory.stream()
                .filter(m -> m.getAvgDurationSeconds() != null)
                .map(m -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", m.getCalculatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    point.put("avg_duration_seconds", m.getAvgDurationSeconds());
                    point.put("min_duration_seconds", m.getMinDurationSeconds());
                    point.put("max_duration_seconds", m.getMaxDurationSeconds());
                    return point;
                })
                .collect(Collectors.toList());

        trends.put("success_rate_trend", successRatePoints);
        trends.put("duration_trend", durationPoints);
        trends.put("data_points", metricsHistory.size());

        return trends;
    }

    private Map<String, Object> calculateGlobalTrends(LocalDateTime startDate,
                                                      LocalDateTime endDate,
                                                      String granularity) {
        Map<String, Object> trends = new HashMap<>();

        // Get all pipelines
        List<Pipeline> allPipelines = pipelineService.getAllPipelines();

        // Aggregate metrics by time buckets
        Map<String, List<Double>> successRatesByTime = new TreeMap<>();
        Map<String, List<Long>> durationsByTime = new TreeMap<>();

        for (Pipeline pipeline : allPipelines) {
            List<PipelineMetrics> metricsHistory = metricsRepository
                    .findByPipelineIdAndCalculatedAtBetween(pipeline.getId(), startDate, endDate);

            for (PipelineMetrics metrics : metricsHistory) {
                String timeKey = formatTimeKey(metrics.getCalculatedAt(), granularity);

                successRatesByTime.computeIfAbsent(timeKey, k -> new ArrayList<>())
                        .add(metrics.getSuccessRate());

                if (metrics.getAvgDurationSeconds() != null) {
                    durationsByTime.computeIfAbsent(timeKey, k -> new ArrayList<>())
                            .add(metrics.getAvgDurationSeconds());
                }
            }
        }

        // Calculate averages for each time bucket
        List<Map<String, Object>> globalSuccessRate = successRatesByTime.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", entry.getKey());
                    point.put("avg_success_rate", entry.getValue().stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0));
                    point.put("pipeline_count", entry.getValue().size());
                    return point;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> globalDuration = durationsByTime.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", entry.getKey());
                    point.put("avg_duration_seconds", entry.getValue().stream()
                            .mapToLong(Long::longValue)
                            .average()
                            .orElse(0));
                    point.put("pipeline_count", entry.getValue().size());
                    return point;
                })
                .collect(Collectors.toList());

        trends.put("global_success_rate_trend", globalSuccessRate);
        trends.put("global_duration_trend", globalDuration);
        trends.put("total_pipelines", allPipelines.size());

        return trends;
    }

    private List<Map<String, Object>> calculateSuccessRateByTimeRange(Integer pipelineId, LocalDateTime startDate) {
        // Get builds in time range and group by hour/day
        List<Build> builds = buildRepository.findAll().stream()
                .filter(b -> Objects.equals(b.getPipeline().getId(), pipelineId))
                .filter(b -> b.getStartTime() != null && b.getStartTime().isAfter(startDate))
                .sorted(Comparator.comparing(Build::getStartTime))
                .collect(Collectors.toList());

        // Group by hour
        Map<String, List<Build>> buildsByHour = builds.stream()
                .collect(Collectors.groupingBy(b ->
                        b.getStartTime().truncatedTo(ChronoUnit.HOURS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                ));

        return buildsByHour.entrySet().stream()
                .map(entry -> {
                    List<Build> hourlyBuilds = entry.getValue();
                    long successful = hourlyBuilds.stream()
                            .filter(b -> "success".equals(b.getStatus()))
                            .count();

                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", entry.getKey());
                    point.put("total_builds", hourlyBuilds.size());
                    point.put("successful_builds", successful);
                    point.put("success_rate", hourlyBuilds.size() > 0 ?
                            (successful * 100.0 / hourlyBuilds.size()) : 0);

                    return point;
                })
                .sorted(Comparator.comparing(p -> (String) p.get("timestamp")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> calculateGlobalSuccessRateByTimeRange(LocalDateTime startDate) {
        // Get all builds across all pipelines
        List<Build> allBuilds = buildRepository.findAll().stream()
                .filter(b -> b.getStartTime() != null && b.getStartTime().isAfter(startDate))
                .collect(Collectors.toList());

        // Group by hour
        Map<String, List<Build>> buildsByHour = allBuilds.stream()
                .collect(Collectors.groupingBy(b ->
                        b.getStartTime().truncatedTo(ChronoUnit.HOURS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                ));

        return buildsByHour.entrySet().stream()
                .map(entry -> {
                    List<Build> hourlyBuilds = entry.getValue();
                    long successful = hourlyBuilds.stream()
                            .filter(b -> "success".equals(b.getStatus()))
                            .count();

                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", entry.getKey());
                    point.put("total_builds", hourlyBuilds.size());
                    point.put("successful_builds", successful);
                    point.put("success_rate", hourlyBuilds.size() > 0 ?
                            (successful * 100.0 / hourlyBuilds.size()) : 0);

                    return point;
                })
                .sorted(Comparator.comparing(p -> (String) p.get("timestamp")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> calculateDurationTrends(Integer pipelineId, LocalDateTime startDate) {
        List<Build> builds = buildRepository.findAll().stream()
                .filter(b -> pipelineId == null || Objects.equals(b.getPipeline().getId(), pipelineId))
                .filter(b -> b.getStartTime() != null && b.getStartTime().isAfter(startDate))
                .filter(b -> b.getDuration() != null)
                .sorted(Comparator.comparing(Build::getStartTime))
                .collect(Collectors.toList());

        // Group by day for duration trends
        Map<String, List<Build>> buildsByDay = builds.stream()
                .collect(Collectors.groupingBy(b ->
                        b.getStartTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                ));

        return buildsByDay.entrySet().stream()
                .map(entry -> {
                    List<Build> dailyBuilds = entry.getValue();
                    DoubleSummaryStatistics stats = dailyBuilds.stream()
                            .mapToDouble(Build::getDuration)
                            .summaryStatistics();

                    Map<String, Object> point = new HashMap<>();
                    point.put("date", entry.getKey());
                    point.put("avg_duration", stats.getAverage());
                    point.put("min_duration", stats.getMin());
                    point.put("max_duration", stats.getMax());
                    point.put("build_count", stats.getCount());

                    return point;
                })
                .sorted(Comparator.comparing(p -> (String) p.get("date")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> calculateBuildFrequency(Integer pipelineId, LocalDateTime startDate) {
        List<Build> builds = buildRepository.findAll().stream()
                .filter(b -> pipelineId == null || Objects.equals(b.getPipeline().getId(), pipelineId))
                .filter(b -> b.getStartTime() != null && b.getStartTime().isAfter(startDate))
                .sorted(Comparator.comparing(Build::getStartTime))
                .collect(Collectors.toList());

        // Group by day
        Map<String, Long> buildCountByDay = builds.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getStartTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        Collectors.counting()
                ));

        return buildCountByDay.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("date", entry.getKey());
                    point.put("build_count", entry.getValue());
                    return point;
                })
                .sorted(Comparator.comparing(p -> (String) p.get("date")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> calculateQueueTrends(Integer pipelineId, LocalDateTime startDate) {
        List<QueueMetrics> queueMetrics = queueMetricsRepository.findAll().stream()
                .filter(q -> pipelineId == null || Objects.equals(q.getPipeline().getId(), pipelineId))
                .filter(q -> q.getTimestamp().isAfter(startDate))
                .sorted(Comparator.comparing(QueueMetrics::getTimestamp))
                .collect(Collectors.toList());

        return queueMetrics.stream()
                .map(qm -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", qm.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    point.put("queue_depth", qm.getCurrentQueueDepth());
                    point.put("running_builds", qm.getRunningBuilds());
                    point.put("avg_wait_time", qm.getAvgWaitTimeSeconds());
                    return point;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> calculateDurationStatistics(List<Map<String, Object>> durationData) {
        Map<String, Object> stats = new HashMap<>();

        if (durationData.isEmpty()) {
            stats.put("avg_duration", 0);
            stats.put("trend", "stable");
            return stats;
        }

        // Calculate overall average
        double overallAvg = durationData.stream()
                .mapToDouble(d -> (Double) d.get("avg_duration"))
                .average()
                .orElse(0);

        // Calculate trend (first half vs second half)
        int midPoint = durationData.size() / 2;
        double firstHalfAvg = durationData.subList(0, midPoint).stream()
                .mapToDouble(d -> (Double) d.get("avg_duration"))
                .average()
                .orElse(0);
        double secondHalfAvg = durationData.subList(midPoint, durationData.size()).stream()
                .mapToDouble(d -> (Double) d.get("avg_duration"))
                .average()
                .orElse(0);

        stats.put("overall_avg_duration", Math.round(overallAvg));
        stats.put("first_half_avg", Math.round(firstHalfAvg));
        stats.put("second_half_avg", Math.round(secondHalfAvg));

        double changePercent = firstHalfAvg > 0 ?
                ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100 : 0;
        stats.put("trend_change_percent", Math.round(changePercent * 10) / 10.0);

        if (changePercent > 10) {
            stats.put("trend", "increasing");
        } else if (changePercent < -10) {
            stats.put("trend", "decreasing");
        } else {
            stats.put("trend", "stable");
        }

        return stats;
    }

    private String formatTimeKey(LocalDateTime dateTime, String granularity) {
        switch (granularity.toLowerCase()) {
            case "hourly":
                return dateTime.truncatedTo(ChronoUnit.HOURS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "daily":
                return dateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "weekly":
                return dateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE); // Simplified
            default:
                return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}