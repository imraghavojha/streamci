package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.service.AnalyticsService;
import com.yourname.streamci.streamci.service.GitHubFetchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);
    private final AnalyticsService analyticsService;
    private final GitHubFetchService gitHubFetchService;

    public AnalyticsController(AnalyticsService analyticsService, GitHubFetchService gitHubFetchService) {
        this.analyticsService = analyticsService;
        this.gitHubFetchService = gitHubFetchService;
    }

    @GetMapping("/trends/{clerkUserId}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getTrends(@PathVariable String clerkUserId) {
        logger.debug("Analytics request received for user: {}", clerkUserId);

        try {
            Map<String, Object> realtimeData = gitHubFetchService.fetchDashboardData(clerkUserId);

            if (realtimeData == null || realtimeData.get("workflows") == null) {
                logger.debug("No workflow data available for user: {}, returning empty trends", clerkUserId);
                return ResponseEntity.ok(createEmptyTrends());
            }

            List<Map<String, Object>> workflows = (List<Map<String, Object>>) realtimeData.get("workflows");

            // handle empty workflows gracefully
            if (workflows.isEmpty()) {
                logger.debug("No workflows found for user: {}, returning empty trends", clerkUserId);
                return ResponseEntity.ok(createEmptyTrends());
            }

            Map<String, Object> trends = new HashMap<>();
            trends.put("success_rate_trends", analyticsService.getSuccessRateTrends(workflows));
            trends.put("failure_patterns", analyticsService.getFailurePatterns(workflows));
            trends.put("build_stats", analyticsService.getBuildDurationStats(workflows));
            trends.put("total_workflows_analyzed", workflows.size());
            trends.put("timestamp", new Date().toInstant().toString());

            logger.debug("Successfully created trends for user: {}", clerkUserId);
            return ResponseEntity.ok(trends);

        } catch (Exception e) {
            logger.error("Error fetching analytics trends for user: {}", clerkUserId, e);
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