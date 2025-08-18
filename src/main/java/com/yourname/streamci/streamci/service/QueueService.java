package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QueueService {

    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);

    private final QueueTrackerRepository trackerRepository;
    private final QueueMetricsRepository metricsRepository;
    private final PipelineService pipelineService;

    public QueueService(QueueTrackerRepository trackerRepository,
                        QueueMetricsRepository metricsRepository,
                        PipelineService pipelineService) {
        this.trackerRepository = trackerRepository;
        this.metricsRepository = metricsRepository;
        this.pipelineService = pipelineService;
    }

    // called when build enters queue
    @Transactional
    public void trackBuildQueued(String buildId, Integer pipelineId) {
        // check if already exists
        Optional<QueueTracker> existing = trackerRepository.findByBuildId(buildId);
        if (existing.isPresent()) {
            logger.debug("build {} already tracked", buildId);
            return;
        }

        Optional<Pipeline> pipeline = pipelineService.getPipelineById(pipelineId);
        if (pipeline.isEmpty()) {
            logger.warn("pipeline {} not found", pipelineId);
            return;
        }

        QueueTracker tracker = QueueTracker.builder()
                .pipeline(pipeline.get())
                .buildId(buildId)
                .status("queued")
                .queuedAt(LocalDateTime.now())
                .build();

        trackerRepository.save(tracker);
        logger.info("build {} queued for pipeline {}", buildId, pipelineId);
    }

    // called when build starts running
    @Transactional
    public void trackBuildStarted(String buildId) {
        Optional<QueueTracker> tracker = trackerRepository.findByBuildId(buildId);
        if (tracker.isEmpty()) {
            logger.warn("build {} not found in queue tracker", buildId);
            return;
        }

        QueueTracker qt = tracker.get();

        // skip if already started
        if ("running".equals(qt.getStatus()) || "completed".equals(qt.getStatus())) {
            logger.debug("build {} already started or completed", buildId);
            return;
        }

        qt.setStatus("running");
        qt.setStartedAt(LocalDateTime.now());

        // calculate wait time
        if (qt.getQueuedAt() != null) {
            long waitSeconds = Duration.between(qt.getQueuedAt(), qt.getStartedAt()).getSeconds();
            qt.setWaitTimeSeconds(waitSeconds);
        }

        trackerRepository.save(qt);
        logger.info("build {} started after {} seconds", buildId, qt.getWaitTimeSeconds());
    }

    // called when build completes
    @Transactional
    public void trackBuildCompleted(String buildId) {
        Optional<QueueTracker> tracker = trackerRepository.findByBuildId(buildId);
        if (tracker.isEmpty()) {
            logger.warn("build {} not found in queue tracker", buildId);
            return;
        }

        QueueTracker qt = tracker.get();

        // skip if already completed
        if ("completed".equals(qt.getStatus())) {
            logger.debug("build {} already completed", buildId);
            return;
        }

        qt.setStatus("completed");
        qt.setCompletedAt(LocalDateTime.now());

        // calculate run time
        if (qt.getStartedAt() != null) {
            long runSeconds = Duration.between(qt.getStartedAt(), qt.getCompletedAt()).getSeconds();
            qt.setRunTimeSeconds(runSeconds);
        } else if (qt.getQueuedAt() != null) {
            // if no start time, use queue time
            long totalSeconds = Duration.between(qt.getQueuedAt(), qt.getCompletedAt()).getSeconds();
            qt.setRunTimeSeconds(totalSeconds);
        }

        trackerRepository.save(qt);
        logger.info("build {} completed after {} seconds", buildId, qt.getRunTimeSeconds());
    }

    // calculate current queue metrics
    @Transactional
    public QueueMetrics calculateQueueMetrics(Integer pipelineId) {
        Optional<Pipeline> pipeline = pipelineService.getPipelineById(pipelineId);
        if (pipeline.isEmpty()) {
            logger.warn("pipeline {} not found", pipelineId);
            return null;
        }

        // get current queue state
        List<QueueTracker> queued = trackerRepository.findByPipelineIdAndStatus(pipelineId, "queued");
        List<QueueTracker> running = trackerRepository.findByPipelineIdAndStatus(pipelineId, "running");

        logger.info("pipeline {} has {} queued, {} running builds",
                pipelineId, queued.size(), running.size());

        // calculate averages from last hour
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        Double avgWaitTime = trackerRepository.getAverageWaitTime(pipelineId, oneHourAgo);

        // get historical metrics for prediction
        List<QueueMetrics> history = metricsRepository
                .findByPipelineIdAndTimestampBetweenOrderByTimestampAsc(
                        pipelineId,
                        LocalDateTime.now().minusHours(2),
                        LocalDateTime.now()
                );

        // calculate predictions
        PredictionResult prediction = predictQueueDepth(history, queued.size());

        QueueMetrics metrics = QueueMetrics.builder()
                .pipeline(pipeline.get())
                .timestamp(LocalDateTime.now())
                .currentQueueDepth(queued.size())
                .runningBuilds(running.size())
                .waitingBuilds(queued.size())
                .avgWaitTimeSeconds(avgWaitTime != null ? avgWaitTime : 0.0)
                .predictedQueueDepth30Min(prediction.predicted30Min)
                .predictedPeakTime(prediction.peakTime)
                .predictedPeakDepth(prediction.peakDepth)
                .trend(prediction.trend)
                .trendSlope(prediction.slope)
                .bottleneckReason(determineBottleneck(queued.size(), running.size(), avgWaitTime))
                .build();

        QueueMetrics saved = metricsRepository.save(metrics);
        logger.info("saved queue metrics for pipeline {}: depth={}, running={}",
                pipelineId, metrics.getCurrentQueueDepth(), metrics.getRunningBuilds());

        return saved;
    }

    // simple prediction using moving average and trend
    private PredictionResult predictQueueDepth(List<QueueMetrics> history, int currentDepth) {
        PredictionResult result = new PredictionResult();

        if (history.size() < 3) {
            // not enough data, use current
            result.predicted30Min = currentDepth;
            result.trend = "stable";
            result.slope = 0.0;
            result.peakDepth = currentDepth;
            result.peakTime = LocalDateTime.now();
            return result;
        }

        // calculate moving average
        double sum = 0;
        int count = Math.min(history.size(), 5);
        for (int i = history.size() - count; i < history.size(); i++) {
            sum += history.get(i).getCurrentQueueDepth();
        }
        double movingAvg = sum / count;

        // calculate trend (simple linear regression)
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < history.size(); i++) {
            sumX += i;
            sumY += history.get(i).getCurrentQueueDepth();
            sumXY += i * history.get(i).getCurrentQueueDepth();
            sumX2 += i * i;
        }

        double n = history.size();
        double slope = 0;
        if (n * sumX2 - sumX * sumX != 0) {
            slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        }

        // predict 30 minutes ahead (6 data points if collecting every 5 min)
        result.predicted30Min = (int) Math.max(0, movingAvg + (slope * 6));
        result.slope = slope;

        // determine trend
        if (Math.abs(slope) < 0.1) {
            result.trend = "stable";
        } else if (slope > 0) {
            result.trend = "increasing";
        } else {
            result.trend = "decreasing";
        }

        // find peak in history
        int maxDepth = currentDepth;
        LocalDateTime peakTime = LocalDateTime.now();
        for (QueueMetrics m : history) {
            if (m.getCurrentQueueDepth() > maxDepth) {
                maxDepth = m.getCurrentQueueDepth();
                peakTime = m.getTimestamp();
            }
        }

        result.peakDepth = maxDepth;
        result.peakTime = peakTime;

        return result;
    }

    private String determineBottleneck(int queueDepth, int runningBuilds, Double avgWaitTime) {
        if (queueDepth > 10) {
            return "high queue depth - consider scaling runners";
        }
        if (avgWaitTime != null && avgWaitTime > 300) { // 5 minutes
            return "long wait times - runners may be overloaded";
        }
        if (runningBuilds > 5 && queueDepth > 5) {
            return "both queue and runners busy - system at capacity";
        }
        if (queueDepth > runningBuilds * 2 && runningBuilds > 0) {
            return "queue growing faster than processing - bottleneck detected";
        }
        return "system operating normally";
    }

    // get current queue status
    public Map<String, Object> getQueueStatus(Integer pipelineId) {
        Map<String, Object> status = new HashMap<>();

        Optional<QueueMetrics> latest = metricsRepository
                .findTopByPipelineIdOrderByTimestampDesc(pipelineId);

        if (latest.isPresent()) {
            QueueMetrics m = latest.get();
            status.put("current_depth", m.getCurrentQueueDepth());
            status.put("running", m.getRunningBuilds());
            status.put("waiting", m.getWaitingBuilds());
            status.put("trend", m.getTrend());
            status.put("predicted_30min", m.getPredictedQueueDepth30Min());
            status.put("bottleneck", m.getBottleneckReason());
        } else {
            status.put("message", "no queue data available");
        }

        return status;
    }

    // helper class for predictions
    private static class PredictionResult {
        int predicted30Min;
        LocalDateTime peakTime;
        int peakDepth;
        String trend;
        double slope;
    }
}