package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.QueueMetrics;
import com.yourname.streamci.streamci.model.QueueTracker;
import com.yourname.streamci.streamci.service.QueueService;
import com.yourname.streamci.streamci.repository.QueueMetricsRepository;
import com.yourname.streamci.streamci.repository.QueueTrackerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@RestController
@RequestMapping("/api")
public class QueueController {

    private final QueueService queueService;
    private final QueueMetricsRepository metricsRepository;
    private final QueueTrackerRepository trackerRepository;

    public QueueController(QueueService queueService,
                           QueueMetricsRepository metricsRepository,
                           QueueTrackerRepository trackerRepository) {
        this.queueService = queueService;
        this.metricsRepository = metricsRepository;
        this.trackerRepository = trackerRepository;
    }

    // get current queue prediction
    @GetMapping("/predictions/queue")
    public ResponseEntity<Map<String, Object>> getQueuePredictions(
            @RequestParam(required = false) Integer pipelineId) {

        if (pipelineId == null) {
            pipelineId = 1; // default to first pipeline
        }

        Map<String, Object> predictions = queueService.getQueueStatus(pipelineId);
        predictions.put("timestamp", LocalDateTime.now());
        predictions.put("pipeline_id", pipelineId);

        return ResponseEntity.ok(predictions);
    }

    // get queue history
    @GetMapping("/queue/history/{pipelineId}")
    public ResponseEntity<List<QueueMetrics>> getQueueHistory(
            @PathVariable Integer pipelineId,
            @RequestParam(defaultValue = "2") int hours) {

        LocalDateTime start = LocalDateTime.now().minusHours(hours);
        LocalDateTime end = LocalDateTime.now();

        List<QueueMetrics> history = metricsRepository
                .findByPipelineIdAndTimestampBetweenOrderByTimestampAsc(
                        pipelineId, start, end
                );

        return ResponseEntity.ok(history);
    }

    // manually calculate queue metrics
    @PostMapping("/queue/{pipelineId}/calculate")
    public ResponseEntity<QueueMetrics> calculateQueue(@PathVariable Integer pipelineId) {
        return queueService.calculateQueueMetrics(pipelineId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // debug endpoint to check queue tracker
    @GetMapping("/queue/debug/{pipelineId}")
    public ResponseEntity<Map<String, Object>> debugQueue(@PathVariable Integer pipelineId) {
        Map<String, Object> debug = new HashMap<>();

        // get all queue trackers for this pipeline
        List<QueueTracker> queued = trackerRepository.findByPipelineIdAndStatus(pipelineId, "queued");
        List<QueueTracker> running = trackerRepository.findByPipelineIdAndStatus(pipelineId, "running");
        List<QueueTracker> completed = trackerRepository.findByPipelineIdAndStatus(pipelineId, "completed");

        debug.put("queued_count", queued.size());
        debug.put("running_count", running.size());
        debug.put("completed_count", completed.size());
        debug.put("queued_builds", queued.stream().map(QueueTracker::getBuildId).toList());
        debug.put("running_builds", running.stream().map(QueueTracker::getBuildId).toList());

        return ResponseEntity.ok(debug);
    }

    // get queue analysis
    @GetMapping("/analysis/queue")
    public ResponseEntity<Map<String, Object>> analyzeQueue(
            @RequestParam(defaultValue = "1") Integer pipelineId) {

        Map<String, Object> analysis = new HashMap<>();

        // get current state
        Map<String, Object> current = queueService.getQueueStatus(pipelineId);
        analysis.put("current", current);

        // get average queue depth
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        Double avgDepth = metricsRepository.getAverageQueueDepth(pipelineId, oneHourAgo);
        analysis.put("avg_depth_last_hour", avgDepth != null ? avgDepth : 0);

        // recommendations
        List<String> recommendations = new ArrayList<>();
        if (current.get("predicted_30min") != null) {
            int predicted = (int) current.get("predicted_30min");
            if (predicted > 10) {
                recommendations.add("queue will be backed up - scale runners now");
            }
            if ("increasing".equals(current.get("trend"))) {
                recommendations.add("queue depth trending up - monitor closely");
            }
        }
        analysis.put("recommendations", recommendations);

        return ResponseEntity.ok(analysis);
    }
}