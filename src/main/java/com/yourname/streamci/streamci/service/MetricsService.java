package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.PipelineMetricsRepository;
import com.yourname.streamci.streamci.event.MetricsCalculatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    private final PipelineService pipelineService;
    private final BuildService buildService;
    private final PipelineMetricsRepository metricsRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MetricsService(PipelineService pipelineService,
                          BuildService buildService,
                          PipelineMetricsRepository metricsRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.pipelineService = pipelineService;
        this.buildService = buildService;
        this.metricsRepository = metricsRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PipelineMetrics calculateMetricsForPipeline(Integer pipelineId) {
        logger.info("Calculating metrics for pipeline {}", pipelineId);

        Optional<Pipeline> pipelineOpt = pipelineService.getPipelineById(pipelineId);
        if (pipelineOpt.isEmpty()) {
            logger.warn("Pipeline {} not found", pipelineId);
            return null;
        }

        Pipeline pipeline = pipelineOpt.get();
        List<Build> builds = buildService.getBuildsByPipelineId(pipelineId);

        if (builds.isEmpty()) {
            logger.info("No builds found for pipeline {}", pipelineId);
            return createEmptyMetrics(pipeline);
        }

        // get previous metrics for comparison
        Optional<PipelineMetrics> previousMetrics = metricsRepository
                .findTopByPipelineIdOrderByCalculatedAtDesc(pipelineId);

        PipelineMetrics metrics = PipelineMetrics.builder()
                .pipeline(pipeline)
                .calculatedAt(LocalDateTime.now())
                .build();

        // calculate basic counts
        calculateBasicMetrics(metrics, builds);

        // calculate duration metrics
        calculateDurationMetrics(metrics, builds);

        // calculate time patterns
        calculateTimePatterns(metrics, builds);

        // calculate failure analysis
        calculateFailureAnalysis(metrics, builds);

        // calculate trends
        if (previousMetrics.isPresent()) {
            calculateTrends(metrics, previousMetrics.get());
        }

        PipelineMetrics saved = metricsRepository.save(metrics);
        logger.info("Saved metrics for pipeline {}: {} builds, {:.1f}% success rate",
                pipelineId, metrics.getTotalBuilds(), metrics.getSuccessRate());

        // publish event for alert checking
        eventPublisher.publishEvent(new MetricsCalculatedEvent(this, pipeline, saved));

        return saved;
    }

    private void calculateBasicMetrics(PipelineMetrics metrics, List<Build> builds) {
        metrics.setTotalBuilds(builds.size());

        long successCount = builds.stream()
                .filter(b -> "success".equalsIgnoreCase(b.getStatus()))
                .count();

        long failureCount = builds.stream()
                .filter(b -> "failure".equalsIgnoreCase(b.getStatus()))
                .count();

        metrics.setSuccessfulBuilds((int) successCount);
        metrics.setFailedBuilds((int) failureCount);

        if (metrics.getTotalBuilds() > 0) {
            double rate = (successCount * 100.0) / metrics.getTotalBuilds();
            metrics.setSuccessRate(Math.round(rate * 10) / 10.0); // round to 1 decimal
        } else {
            metrics.setSuccessRate(0.0);
        }
    }

    private void calculateDurationMetrics(PipelineMetrics metrics, List<Build> builds) {
        List<Long> durations = builds.stream()
                .filter(b -> b.getDuration() != null && b.getDuration() > 0)
                .map(Build::getDuration)
                .collect(Collectors.toList());

        if (!durations.isEmpty()) {
            metrics.setAvgDurationSeconds(
                    (long) durations.stream().mapToLong(Long::longValue).average().orElse(0)
            );
            metrics.setMinDurationSeconds(
                    durations.stream().min(Long::compare).orElse(0L)
            );
            metrics.setMaxDurationSeconds(
                    durations.stream().max(Long::compare).orElse(0L)
            );
        } else {
            metrics.setAvgDurationSeconds(0L);
            metrics.setMinDurationSeconds(0L);
            metrics.setMaxDurationSeconds(0L);
        }
    }

    private void calculateTimePatterns(PipelineMetrics metrics, List<Build> builds) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime weekStart = now.with(DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);

        // builds today
        long todayCount = builds.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isAfter(todayStart))
                .count();
        metrics.setBuildsToday((int) todayCount);

        // builds this week
        long weekCount = builds.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isAfter(weekStart))
                .count();
        metrics.setBuildsThisWeek((int) weekCount);

        // find peak hour
        Map<Integer, Long> hourCounts = builds.stream()
                .filter(b -> b.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getCreatedAt().getHour(),
                        Collectors.counting()
                ));

        if (!hourCounts.isEmpty()) {
            Integer peakHour = hourCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(0);
            metrics.setPeakHour(String.valueOf(peakHour));
        }

        // find peak day
        Map<DayOfWeek, Long> dayCounts = builds.stream()
                .filter(b -> b.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getCreatedAt().getDayOfWeek(),
                        Collectors.counting()
                ));

        if (!dayCounts.isEmpty()) {
            DayOfWeek peakDay = dayCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(DayOfWeek.MONDAY);
            metrics.setPeakDay(peakDay.toString());
        }
    }

    private void calculateFailureAnalysis(PipelineMetrics metrics, List<Build> builds) {
        // sort builds by time
        List<Build> sortedBuilds = builds.stream()
                .filter(b -> b.getCreatedAt() != null)
                .sorted(Comparator.comparing(Build::getCreatedAt).reversed())
                .collect(Collectors.toList());

        // find consecutive failures
        int consecutiveFailures = 0;
        for (Build build : sortedBuilds) {
            if ("failure".equalsIgnoreCase(build.getStatus())) {
                consecutiveFailures++;
            } else {
                break;
            }
        }
        metrics.setConsecutiveFailures(consecutiveFailures);

        // find last success and failure
        sortedBuilds.stream()
                .filter(b -> "success".equalsIgnoreCase(b.getStatus()))
                .findFirst()
                .ifPresent(b -> metrics.setLastSuccess(b.getCreatedAt()));

        sortedBuilds.stream()
                .filter(b -> "failure".equalsIgnoreCase(b.getStatus()))
                .findFirst()
                .ifPresent(b -> metrics.setLastFailure(b.getCreatedAt()));

        // find most common failure time
        Map<Integer, Long> failureHours = builds.stream()
                .filter(b -> "failure".equalsIgnoreCase(b.getStatus()) && b.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getCreatedAt().getHour(),
                        Collectors.counting()
                ));

        if (!failureHours.isEmpty()) {
            Integer commonFailureHour = failureHours.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(0);
            metrics.setMostCommonFailureTime(commonFailureHour + ":00");
        }
    }

    private void calculateTrends(PipelineMetrics current, PipelineMetrics previous) {
        // success rate change
        if (previous.getSuccessRate() != null && previous.getSuccessRate() > 0) {
            double change = current.getSuccessRate() - previous.getSuccessRate();
            current.setSuccessRateChange(Math.round(change * 10) / 10.0);
        }

        // duration change
        if (previous.getAvgDurationSeconds() != null && previous.getAvgDurationSeconds() > 0) {
            long change = current.getAvgDurationSeconds() - previous.getAvgDurationSeconds();
            current.setAvgDurationChange(change);
        }
    }

    private PipelineMetrics createEmptyMetrics(Pipeline pipeline) {
        return PipelineMetrics.builder()
                .pipeline(pipeline)
                .calculatedAt(LocalDateTime.now())
                .totalBuilds(0)
                .successfulBuilds(0)
                .failedBuilds(0)
                .successRate(0.0)
                .avgDurationSeconds(0L)
                .minDurationSeconds(0L)
                .maxDurationSeconds(0L)
                .buildsToday(0)
                .buildsThisWeek(0)
                .consecutiveFailures(0)
                .build();
    }

    public Optional<PipelineMetrics> getLatestMetrics(Integer pipelineId) {
        return metricsRepository.findLatestByPipelineId(pipelineId);
    }

    public List<PipelineMetrics> getMetricsHistory(Integer pipelineId, int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        return metricsRepository.findByPipelineIdAndCalculatedAtBetween(pipelineId, start, end);
    }
}