package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.service.GitHubFetchService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeDashboardController {

    private final GitHubFetchService gitHubFetchService;

    public RealtimeDashboardController(GitHubFetchService gitHubFetchService) {
        this.gitHubFetchService = gitHubFetchService;
    }

    @GetMapping("/dashboard/{clerkUserId}")
    public ResponseEntity<Map<String, Object>> getDashboard(@PathVariable String clerkUserId) {
        Map<String, Object> data = gitHubFetchService.fetchDashboardData(clerkUserId);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/refresh/{clerkUserId}")
    public ResponseEntity<Map<String, Object>> refreshDashboard(@PathVariable String clerkUserId) {
        // clear cache and fetch fresh
        gitHubFetchService.clearCache(clerkUserId);
        Map<String, Object> data = gitHubFetchService.fetchDashboardData(clerkUserId);
        return ResponseEntity.ok(data);
    }
}