package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.service.GitHubService;
import com.yourname.streamci.streamci.service.UserService;
import com.yourname.streamci.streamci.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryController.class);
    private final UserService userService;
    private final GitHubService gitHubService;

    public RepositoryController(UserService userService, GitHubService gitHubService) {
        this.userService = userService;
        this.gitHubService = gitHubService;
    }

    @GetMapping("/{clerkUserId}")
    public ResponseEntity<Map<String, Object>> getUserRepositories(@PathVariable String clerkUserId) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = userService.findByClerkUserId(clerkUserId)
                    .orElseThrow(() -> new RuntimeException("user not found"));

            String token = userService.decryptGithubToken(user);
            List<Map<String, Object>> repositories = gitHubService.fetchUserRepositories(token);

            // get currently selected repos - simple parsing
            String selectedReposJson = user.getSelectedRepos();
            List<String> selectedRepos = new ArrayList<>();
            if (selectedReposJson != null && !selectedReposJson.isEmpty()) {
                // simple json array parsing
                selectedReposJson = selectedReposJson.replace("[", "").replace("]", "").replace("\"", "");
                if (!selectedReposJson.trim().isEmpty()) {
                    String[] repoNames = selectedReposJson.split(",");
                    for (String name : repoNames) {
                        selectedRepos.add(name.trim());
                    }
                }
            }

            response.put("success", true);
            response.put("repositories", repositories);
            response.put("selected_repos", selectedRepos);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("failed to get repositories: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/{clerkUserId}/select")
    public ResponseEntity<Map<String, Object>> updateSelectedRepositories(
            @PathVariable String clerkUserId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            User user = userService.findByClerkUserId(clerkUserId)
                    .orElseThrow(() -> new RuntimeException("user not found"));

            @SuppressWarnings("unchecked")
            List<String> selectedRepos = (List<String>) request.get("selected_repos");

            // simple json array creation
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");
            for (int i = 0; i < selectedRepos.size(); i++) {
                if (i > 0) jsonBuilder.append(",");
                jsonBuilder.append("\"").append(selectedRepos.get(i)).append("\"");
            }
            jsonBuilder.append("]");

            user.setSelectedRepos(jsonBuilder.toString());
            userService.saveUser(user);

            response.put("success", true);
            response.put("selected_count", selectedRepos.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("failed to update selected repositories: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}