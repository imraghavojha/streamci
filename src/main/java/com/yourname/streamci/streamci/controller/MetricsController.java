package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.PipelineMetrics;
import com.yourname.streamci.streamci.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    // get latest metrics for a pipeline
    @GetMapping("/{pipelineId}")
    public ResponseEntity<?> getMetrics(@PathVariable Integer pipelineId) {
        return metricsService.getLatestMetrics(pipelineId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // get metrics history
    @GetMapping("/{pipelineId}/history")
    public ResponseEntity<List<PipelineMetrics>> getMetricsHistory(
            @PathVariable Integer pipelineId,
            @RequestParam(defaultValue = "7") int days) {

        List<PipelineMetrics> history = metricsService.getMetricsHistory(pipelineId, days);
        return ResponseEntity.ok(history);
    }

    // manually trigger metrics calculation
    @PostMapping("/{pipelineId}/calculate")
    public ResponseEntity<?> calculateMetrics(@PathVariable Integer pipelineId) {
        PipelineMetrics metrics = metricsService.calculateMetricsForPipeline(pipelineId);

        if (metrics != null) {
            return ResponseEntity.ok(metrics);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Pipeline not found");
            return ResponseEntity.notFound().build();
        }
    }

    // get metrics summary for all pipelines
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();

        // this would aggregate all pipeline metrics
        // for now, return a simple response
        summary.put("message", "Metrics summary endpoint");
        summary.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(summary);
    }
}