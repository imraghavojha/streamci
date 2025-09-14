package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.User;
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
            // check cache first
            CacheEntry cached = sessionCache.get(clerkUserId);
            if (cached != null && !cached.isExpired()) {
                logger.info("returning cached data for user: {}", clerkUserId);
                return cached.data;
            }

            // fetch fresh data
            User user = userService.findByClerkUserId(clerkUserId)
                    .orElseThrow(() -> new RuntimeException("user not found"));

            String token = userService.decryptGithubToken(user);
            String[] repos = parseSelectedRepos(user.getSelectedRepos());

            Map<String, Object> dashboardData = new HashMap<>();
            List<Map<String, Object>> allWorkflows = new ArrayList<>();

            // fetch workflows for each selected repo
            for (String repo : repos) {
                List<Map<String, Object>> workflows = fetchWorkflowsForRepo(repo, token);
                allWorkflows.addAll(workflows);
            }

            dashboardData.put("workflows", allWorkflows);
            dashboardData.put("lastUpdated", LocalDateTime.now().toString());
            dashboardData.put("totalWorkflows", allWorkflows.size());

            // cache the result
            sessionCache.put(clerkUserId, new CacheEntry(dashboardData));

            logger.info("fetched fresh data for user: {} - {} workflows", clerkUserId, allWorkflows.size());
            return dashboardData;

        } catch (Exception e) {
            logger.error("failed to fetch dashboard data: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    private List<Map<String, Object>> fetchWorkflowsForRepo(String repo, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + token);
            headers.set("Accept", "application/vnd.github.v3+json");

            String url = String.format("https://api.github.com/repos/%s/actions/runs?per_page=5", repo);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            Map response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();

            List<Map<String, Object>> workflowRuns = (List<Map<String, Object>>) response.get("workflow_runs");

            // simplify the data structure
            return workflowRuns.stream().map(run -> {
                Map<String, Object> simplified = new HashMap<>();
                simplified.put("id", run.get("id"));
                simplified.put("name", run.get("name"));
                simplified.put("status", run.get("status"));
                simplified.put("conclusion", run.get("conclusion"));
                simplified.put("created_at", run.get("created_at"));
                simplified.put("repository", repo);
                return simplified;
            }).toList();

        } catch (Exception e) {
            logger.error("failed to fetch workflows for repo {}: {}", repo, e.getMessage());
            return List.of();
        }
    }

    private String[] parseSelectedRepos(String selectedRepos) {
        if (selectedRepos == null) return new String[0];
        return selectedRepos.replace("[", "").replace("]", "").replace("\"", "").split(",");
    }

    public void clearCache(String clerkUserId) {
        sessionCache.remove(clerkUserId);
    }

    // simple cache entry with 30 minute expiry
    private static class CacheEntry {
        final Map<String, Object> data;
        final LocalDateTime createdAt;

        CacheEntry(Map<String, Object> data) {
            this.data = data;
            this.createdAt = LocalDateTime.now();
        }

        boolean isExpired() {
            return createdAt.isBefore(LocalDateTime.now().minusMinutes(30));
        }
    }
}