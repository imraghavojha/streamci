package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GitHubFetchService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubFetchService.class);

    // in-memory cache: userId -> cached data
    private final Map<String, CacheEntry> sessionCache = new ConcurrentHashMap<>();

    private final UserService userService;
    private final RestTemplate restTemplate;

    public GitHubFetchService(UserService userService, RestTemplate restTemplate) {
        this.userService = userService;
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> fetchDashboardData(String clerkUserId) {
        try {
            logger.info("=== DEBUGGING fetchDashboardData for user: {} ===", clerkUserId);

            // check cache first - but make it expire faster (5 minutes instead of 30)
            CacheEntry cached = sessionCache.get(clerkUserId);
            if (cached != null && !cached.isExpired()) {
                logger.info("returning cached data for user: {} (expires in {} minutes)",
                        clerkUserId, cached.getMinutesUntilExpiry());
                return cached.data;
            }

            // fetch fresh data
            User user = userService.findByClerkUserId(clerkUserId)
                    .orElseThrow(() -> new RuntimeException("user not found"));
            logger.info("found user, decrypting token...");

            String token = userService.decryptGithubToken(user);
            logger.info("token decrypted successfully, length: {}", token.length());

            String[] repos = parseSelectedRepos(user.getSelectedRepos());
            logger.info("parsed selected repos: {}", java.util.Arrays.toString(repos));

            Map<String, Object> dashboardData = new HashMap<>();
            List<Map<String, Object>> allWorkflows = new ArrayList<>();

            // fetch workflows for each selected repo
            for (String repo : repos) {
                logger.info("fetching workflows for repo: '{}'", repo);
                List<Map<String, Object>> workflows = fetchWorkflowsForRepo(repo, token);
                logger.info("found {} workflows for repo: {}", workflows.size(), repo);
                allWorkflows.addAll(workflows);
            }

            dashboardData.put("workflows", allWorkflows);
            dashboardData.put("lastUpdated", LocalDateTime.now().toString());
            dashboardData.put("totalWorkflows", allWorkflows.size());
            dashboardData.put("selectedRepos", repos); // add this for frontend

            // cache the result with shorter expiry
            sessionCache.put(clerkUserId, new CacheEntry(dashboardData));

            logger.info("=== FINAL RESULT: {} total workflows from {} repos ===",
                    allWorkflows.size(), repos.length);
            return dashboardData;

        } catch (Exception e) {
            logger.error("=== ERROR in fetchDashboardData: {} ===", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    private List<Map<String, Object>> fetchWorkflowsForRepo(String repoFullName, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + token);
            headers.set("Accept", "application/vnd.github.v3+json");

            // use the full repository name (owner/repo format)
            String url = String.format("https://api.github.com/repos/%s/actions/runs?per_page=10", repoFullName);
            logger.info("fetching workflows from: {}", url);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("failed to fetch workflows for repo {}: status {}", repoFullName, response.getStatusCode());
                return List.of();
            }

            // parse JSON response
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(response.getBody());
            com.fasterxml.jackson.databind.JsonNode workflowRuns = rootNode.get("workflow_runs");

            if (workflowRuns == null || !workflowRuns.isArray()) {
                logger.warn("no workflow_runs found for repo: {}", repoFullName);
                return List.of();
            }

            List<Map<String, Object>> workflows = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode run : workflowRuns) {
                Map<String, Object> simplified = new HashMap<>();
                simplified.put("id", run.get("id").asLong());
                simplified.put("name", run.has("name") ? run.get("name").asText() : "Workflow");
                simplified.put("status", run.has("status") ? run.get("status").asText() : "unknown");
                simplified.put("conclusion", run.has("conclusion") && !run.get("conclusion").isNull() ?
                        run.get("conclusion").asText() : null);
                simplified.put("created_at", run.has("created_at") ? run.get("created_at").asText() : "");
                simplified.put("repository", repoFullName);
                workflows.add(simplified);
            }

            logger.info("fetched {} workflows for repo: {}", workflows.size(), repoFullName);
            return workflows;

        } catch (Exception e) {
            logger.error("failed to fetch workflows for repo {}: {}", repoFullName, e.getMessage());
            return List.of();
        }
    }

    private String[] parseSelectedRepos(String selectedRepos) {
        if (selectedRepos == null || selectedRepos.trim().isEmpty()) {
            return new String[0];
        }

        // clean up JSON format and split
        String cleaned = selectedRepos.replace("[", "").replace("]", "").replace("\"", "").trim();
        if (cleaned.isEmpty()) {
            return new String[0];
        }

        String[] repos = cleaned.split(",");
        List<String> validRepos = new ArrayList<>();

        for (String repo : repos) {
            String trimmed = repo.trim();
            if (!trimmed.isEmpty()) {
                validRepos.add(trimmed);
            }
        }

        return validRepos.toArray(new String[0]);
    }

    public void clearCache(String clerkUserId) {
        sessionCache.remove(clerkUserId);
        logger.info("cleared cache for user: {}", clerkUserId);
    }

    // simple cache entry with 30 minute expiry
    private static class CacheEntry {
        final Map<String, Object> data;
        final LocalDateTime createdAt;
        private static final int CACHE_MINUTES = 5; // reduced from 30 to 5 minutes

        CacheEntry(Map<String, Object> data) {
            this.data = data;
            this.createdAt = LocalDateTime.now();
        }

        boolean isExpired() {
            return createdAt.isBefore(LocalDateTime.now().minusMinutes(CACHE_MINUTES));
        }

        long getMinutesUntilExpiry() {
            LocalDateTime expiryTime = createdAt.plusMinutes(CACHE_MINUTES);
            return java.time.Duration.between(LocalDateTime.now(), expiryTime).toMinutes();
        }
    }
}