package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
public class PatternAnalysisController {

    private final PatternAnalysisService patternService;
    private final BuildSuccessPredictor successPredictor;

    public PatternAnalysisController(PatternAnalysisService patternService,
                                     BuildSuccessPredictor successPredictor) {
        this.patternService = patternService;
        this.successPredictor = successPredictor;
    }

    @GetMapping("/analysis/patterns")
    public ResponseEntity<Map<String, Object>> getFailurePatterns(
            @RequestParam Integer pipelineId,
            @RequestParam(defaultValue = "7") int days) {

        List<PatternDetectionResult> patterns = patternService.analyzeFailurePatterns(pipelineId, days);

        Map<String, Object> response = new HashMap<>();
        response.put("pipeline_id", pipelineId);
        response.put("analysis_period_days", days);
        response.put("patterns_found", patterns.size());
        response.put("patterns", patterns);
        response.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/analysis/flaky-tests")
    public ResponseEntity<Map<String, Object>> getFlakyTests(@RequestParam Integer pipelineId) {
        List<FlakyTestResult> flakyTests = patternService.detectFlakyTests(pipelineId);

        Map<String, Object> response = new HashMap<>();
        response.put("pipeline_id", pipelineId);
        response.put("flaky_tests_found", flakyTests.size());
        response.put("flaky_tests", flakyTests);
        response.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/analysis/correlations")
    public ResponseEntity<Map<String, Object>> getCorrelations(
            @RequestParam Integer pipelineId,
            @RequestParam String type) {

        Map<String, Object> response = new HashMap<>();
        response.put("pipeline_id", pipelineId);
        response.put("correlation_type", type);

        switch (type.toLowerCase()) {
            case "time":
                List<TimePatternResult> timePatterns = patternService.getTimeBasedCorrelations(pipelineId);
                response.put("correlations", timePatterns);
                break;

            case "committer":
                List<CommitterPatternResult> committerPatterns = patternService.getCommitterBasedCorrelations(pipelineId);
                response.put("correlations", committerPatterns);
                break;

            case "files":
                response.put("correlations", List.of());
                response.put("message", "file correlation analysis not yet implemented");
                break;

            default:
                return ResponseEntity.badRequest().body(Map.of("error", "invalid correlation type: " + type));
        }

        response.put("timestamp", java.time.LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/predictions/success")
    public ResponseEntity<SuccessPrediction> predictSuccess(
            @RequestParam Integer pipelineId,
            @RequestParam(required = false) String committer,
            @RequestParam(required = false) String branch) {

        SuccessPrediction prediction = successPredictor.predictNextBuildSuccess(pipelineId, committer, branch);
        return ResponseEntity.ok(prediction);
    }

    @GetMapping("/analysis/summary")
    public ResponseEntity<Map<String, Object>> getAnalysisSummary(@RequestParam Integer pipelineId) {
        List<PatternDetectionResult> patterns = patternService.analyzeFailurePatterns(pipelineId, 7);
        List<FlakyTestResult> flakyTests = patternService.detectFlakyTests(pipelineId);
        SuccessPrediction prediction = successPredictor.predictNextBuildSuccess(pipelineId);

        Map<String, Object> summary = new HashMap<>();
        summary.put("pipeline_id", pipelineId);
        summary.put("patterns_count", patterns.size());
        summary.put("flaky_tests_count", flakyTests.size());
        summary.put("next_build_success_probability", prediction.getProbability());
        summary.put("confidence", prediction.getConfidence());

        summary.put("top_issues", patterns.stream()
                .limit(3)
                .map(p -> Map.of("type", p.getPatternType(), "description", p.getDescription()))
                .toList());

        summary.put("recommendations", patterns.stream()
                .limit(3)
                .map(PatternDetectionResult::getRecommendation)
                .toList());

        return ResponseEntity.ok(summary);
    }
}