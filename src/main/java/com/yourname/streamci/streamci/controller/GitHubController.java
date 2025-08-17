package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.service.GitHubService;
import com.yourname.streamci.streamci.model.SyncResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class GitHubController {

    private static final Logger logger = LoggerFactory.getLogger(GitHubController.class);
    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    // Test endpoint for GitHub connection
    @GetMapping("/api/github/test")
    public ResponseEntity<Map<String, Object>> testGitHubConnection() {
        Map<String, Object> response = new HashMap<>();

        boolean connected = gitHubService.testConnection();

        if (connected) {
            response.put("status", "success");
            response.put("message", "GitHub API connection successful");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "Failed to connect to GitHub API");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(500).body(response);
        }
    }

    // Main sync endpoint
    @GetMapping("/api/sync/github/{owner}/{repo}")
    public ResponseEntity<Map<String, Object>> syncRepository(
            @PathVariable String owner,
            @PathVariable String repo) {

        logger.info("Sync request received for {}/{}", owner, repo);

        // Input validation
        if (owner == null || owner.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("Owner parameter is required"));
        }

        if (repo == null || repo.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("Repository parameter is required"));
        }

        // Check for invalid characters
        if (!isValidGitHubName(owner) || !isValidGitHubName(repo)) {
            return ResponseEntity.badRequest().body(createErrorResponse("Invalid characters in owner or repository name"));
        }

        try {
            SyncResult result = gitHubService.syncRepository(owner, repo);

            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", result.getMessage());
                response.put("repository", owner + "/" + repo);
                response.put("pipelines_synced", result.getPipelinesSynced());
                response.put("builds_synced", result.getBuildsSynced());
                response.put("timestamp", LocalDateTime.now());

                logger.info("Successfully synced {}/{}: {} pipelines, {} builds",
                        owner, repo, result.getPipelinesSynced(), result.getBuildsSynced());

                return ResponseEntity.ok(response);
            } else {
                // Handle specific error cases
                if (result.getMessage().contains("not found")) {
                    return ResponseEntity.notFound().build();
                } else if (result.getMessage().contains("rate limit")) {
                    return ResponseEntity.status(503).body(createErrorResponse("GitHub API rate limit exceeded. Please try again later."));
                } else {
                    return ResponseEntity.status(500).body(createErrorResponse(result.getMessage()));
                }
            }

        } catch (Exception e) {
            logger.error("Unexpected error during sync for {}/{}: {}", owner, repo, e.getMessage());
            return ResponseEntity.status(500).body(createErrorResponse("Internal server error during sync operation"));
        }
    }

    // Helper methods
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    private boolean isValidGitHubName(String name) {
        // GitHub usernames and repo names can contain alphanumeric characters, hyphens, and underscores
        return name.matches("^[a-zA-Z0-9._-]+$");
    }
}