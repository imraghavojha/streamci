package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.service.AnalyticsService;
import com.yourname.streamci.streamci.service.GitHubFetchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final GitHubFetchService gitHubFetchService;

    public AnalyticsController(AnalyticsService analyticsService, GitHubFetchService gitHubFetchService) {
        this.analyticsService = analyticsService;
        this.gitHubFetchService = gitHubFetchService;
    }

    @GetMapping("/trends/{clerkUserId}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getTrends(@PathVariable String clerkUserId) {
        System.out.println("=== DEBUG: analytics request received for user: " + clerkUserId + " ===");

        try {
            Map<String, Object> realtimeData = gitHubFetchService.fetchDashboardData(clerkUserId);
            System.out.println("=== DEBUG: data keys: " + (realtimeData != null ? realtimeData.keySet() : "null") + " ===");

            if (realtimeData == null || realtimeData.get("workflows") == null) {
                System.out.println("=== DEBUG: returning empty trends ===");
                return ResponseEntity.ok(createEmptyTrends());
            }

            List<Map<String, Object>> workflows = (List<Map<String, Object>>) realtimeData.get("workflows");
            System.out.println("=== DEBUG: workflow count: " + workflows.size() + " ===");

            // handle empty workflows gracefully
            if (workflows.isEmpty()) {
                System.out.println("=== DEBUG: no workflows found, returning empty trends ===");
                return ResponseEntity.ok(createEmptyTrends());
            }

            Map<String, Object> trends = new HashMap<>();
            trends.put("success_rate_trends", analyticsService.getSuccessRateTrends(workflows));
            trends.put("failure_patterns", analyticsService.getFailurePatterns(workflows));
            trends.put("build_stats", analyticsService.getBuildDurationStats(workflows));
            trends.put("total_workflows_analyzed", workflows.size());
            trends.put("timestamp", new Date().toInstant().toString());

            System.out.println("=== DEBUG: successfully created trends ===");
            return ResponseEntity.ok(trends);

        } catch (Exception e) {
            System.err.println("=== ERROR: " + e.getMessage() + " ===");
            e.printStackTrace();
            return ResponseEntity.ok(createEmptyTrends());
        }
    }

    private Map<String, Object> createEmptyTrends() {
        Map<String, Object> empty = new HashMap<>();
        empty.put("success_rate_trends", Collections.emptyList());
        empty.put("failure_patterns", Collections.emptyList());
        empty.put("build_stats", Map.of(
                "total_builds", 0,
                "successful_builds", 0,
                "failed_builds", 0,
                "running_builds", 0,
                "builds_analyzed", 0
        ));
        empty.put("total_workflows_analyzed", 0);
        empty.put("timestamp", new Date().toInstant().toString());
        return empty;
    }
}