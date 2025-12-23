package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.model.Build;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GitHubService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);
    private final PipelineService pipelineService;
    private final BuildService buildService;
    private final RestTemplate restTemplate;

    @Value("${github.token}")
    private String githubToken;

    public GitHubService(PipelineService pipelineService, BuildService buildService, RestTemplate restTemplate) {
        this.pipelineService = pipelineService;
        this.buildService = buildService;
        this.restTemplate = restTemplate;
    }

    public boolean testConnection() {
        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.github.com/user",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            logger.info("GitHub API connection successful: {}", response.getStatusCode());
            return true;

        } catch (RestClientException e) {
            logger.error("Failed to connect to GitHub API: {}", e.getMessage());
            return false;
        }
    }

    public Optional<GitHubRepo> fetchRepositoryInfo(String owner, String repo) {
        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = String.format("https://api.github.com/repos/%s/%s", owner, repo);

            GitHubRepo repoInfo = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    GitHubRepo.class
            ).getBody();

            logger.info("successfully fetched repo info for {}/{}", owner, repo);
            return Optional.ofNullable(repoInfo);

        } catch (RestClientException e) {
            logger.error("failed to fetch repository {}/{}: {}", owner, repo, e.getMessage());
            return Optional.empty();
        }
    }

    public List<WorkflowRun> fetchWorkflowRuns(String owner, String repo) {
        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = String.format("https://api.github.com/repos/%s/%s/actions/runs?per_page=10", owner, repo);

            WorkflowRunsResponse response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    WorkflowRunsResponse.class
            ).getBody();

            if (response != null && response.getWorkflowRuns() != null) {
                // Filter out incomplete data
                List<WorkflowRun> validRuns = response.getWorkflowRuns().stream()
                        .filter(run -> run.getStatus() != null && run.getConclusion() != null)
                        .toList();

                logger.info("Found {} valid workflow runs for {}/{}", validRuns.size(), owner, repo);
                return validRuns;
            }

            return new ArrayList<>();

        } catch (RestClientException e) {
            logger.error("Failed to fetch workflow runs for {}/{}: {}", owner, repo, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Transactional
    public SyncResult syncRepository(String owner, String repo) {
        logger.info("starting sync for repository {}/{}", owner, repo);

        Optional<GitHubRepo> repoInfoOpt = fetchRepositoryInfo(owner, repo);
        if (repoInfoOpt.isEmpty()) {
            return new SyncResult(false, "repository not found or inaccessible", 0, 0);
        }

        GitHubRepo repoInfo = repoInfoOpt.get();
        List<WorkflowRun> workflowRuns = fetchWorkflowRuns(owner, repo);
        if (workflowRuns.isEmpty()) {
            logger.warn("no workflow runs found for {}/{}", owner, repo);
        }

        Pipeline pipeline = mapToPipeline(repoInfo, workflowRuns);
        Pipeline savedPipeline = savePipeline(pipeline);

        List<Build> builds = mapToBuilds(workflowRuns, savedPipeline);
        int savedBuildsCount = saveBuilds(builds);

        logger.info("sync completed for {}/{}: 1 pipeline, {} builds", owner, repo, savedBuildsCount);
        return new SyncResult(true, "sync successful", 1, savedBuildsCount);
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        return headers;
    }

    private Pipeline mapToPipeline(GitHubRepo repoInfo, List<WorkflowRun> workflowRuns) {
        String status = "unknown";
        int avgDuration = 0;

        if (!workflowRuns.isEmpty()) {
            status = mapGitHubStatusToOurStatus(workflowRuns.get(0).getConclusion());

            avgDuration = workflowRuns.stream()
                    .filter(run -> run.getCreatedAt() != null && run.getUpdatedAt() != null)
                    .mapToInt(this::calculateDuration)
                    .sum() / Math.max(1, workflowRuns.size());
        }

        return Pipeline.builder()
                .name(repoInfo.getName())
                .status(status)
                .duration(avgDuration)
                .build();
    }

    private List<Build> mapToBuilds(List<WorkflowRun> workflowRuns, Pipeline pipeline) {
        return workflowRuns.stream()
                .map(run -> Build.builder()
                        .pipeline(pipeline)
                        .status(mapGitHubStatusToOurStatus(run.getConclusion()))
                        .startTime(parseGitHubDateTime(run.getCreatedAt()))
                        .endTime(parseGitHubDateTime(run.getUpdatedAt()))
                        .duration(calculateDurationLong(run))
                        .commitHash(run.getHeadCommit() != null ? run.getHeadCommit().getId() : "unknown")
                        .committer(run.getHeadCommit() != null && run.getHeadCommit().getAuthor() != null ?
                                run.getHeadCommit().getAuthor().getName() : "unknown")
                        .branch("main") // GitHub API doesn't always provide this easily
                        .build())
                .toList();
    }

    private Pipeline savePipeline(Pipeline pipeline) {
        List<Pipeline> existingPipelines = pipelineService.getAllPipelines();
        Optional<Pipeline> existing = existingPipelines.stream()
                .filter(p -> p.getName().equals(pipeline.getName()))
                .findFirst();

        if (existing.isPresent()) {
            Pipeline existingPipeline = existing.get();
            return pipelineService.updatePipeline(existingPipeline.getId(), pipeline)
                    .orElse(existingPipeline);
        } else {
            return pipelineService.savePipeline(pipeline);
        }
    }

    private int saveBuilds(List<Build> builds) {
        int savedCount = 0;
        for (Build build : builds) {
            try {
                buildService.saveBuild(build);
                savedCount++;
            } catch (Exception e) {
                logger.error("Failed to save build: {}", e.getMessage());
            }
        }
        return savedCount;
    }

    private String mapGitHubStatusToOurStatus(String githubConclusion) {
        if (githubConclusion == null) return "running";
        return switch (githubConclusion.toLowerCase()) {
            case "success" -> "success";
            case "failure" -> "failure";
            case "cancelled" -> "cancelled";
            case "skipped" -> "skipped";
            default -> "unknown";
        };
    }

    private LocalDateTime parseGitHubDateTime(String dateTimeString) {
        if (dateTimeString == null) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            logger.warn("Failed to parse datetime: {}", dateTimeString);
            return LocalDateTime.now();
        }
    }

    private int calculateDuration(WorkflowRun run) {
        if (run.getCreatedAt() == null || run.getUpdatedAt() == null) return 0;

        LocalDateTime start = parseGitHubDateTime(run.getCreatedAt());
        LocalDateTime end = parseGitHubDateTime(run.getUpdatedAt());

        return (int) java.time.Duration.between(start, end).toMinutes();
    }

    private Long calculateDurationLong(WorkflowRun run) {
        return (long) calculateDuration(run);
    }


    public boolean testConnectionWithToken(String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + token);
            headers.set("Accept", "application/vnd.github.v3+json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.github.com/user",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            logger.info("github api test with provided token: {}", response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            logger.error("failed to test github token: {}", e.getMessage());
            return false;
        }
    }
    public List<Map<String, Object>> fetchUserRepositories(String token) {
        List<Map<String, Object>> repositories = new ArrayList<>();

        try {
            HttpHeaders headers = createAuthHeaders();
            headers.set("Authorization", "token " + token); // override with user token
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.github.com/user/repos?per_page=100&sort=updated",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // parse the real JSON response
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode repos = mapper.readTree(response.getBody());

                for (com.fasterxml.jackson.databind.JsonNode repo : repos) {
                    Map<String, Object> repoData = new HashMap<>();
                    repoData.put("id", repo.get("id").asLong());
                    repoData.put("name", repo.get("name").asText());
                    repoData.put("full_name", repo.get("full_name").asText());
                    repoData.put("private", repo.get("private").asBoolean());
                    repoData.put("updated_at", repo.get("updated_at").asText());
                    repoData.put("language", repo.has("language") && !repo.get("language").isNull() ?
                            repo.get("language").asText() : "Unknown");

                    repositories.add(repoData);
                }

                logger.info("fetched {} real repositories for user", repositories.size());
            }
        } catch (Exception e) {
            logger.error("error fetching repositories: {}", e.getMessage());

            // fallback to mock data if API fails
            Map<String, Object> repo1 = new HashMap<>();
            repo1.put("name", "streamci-ui");
            repo1.put("full_name", "user/streamci-ui");
            repositories.add(repo1);

            Map<String, Object> repo2 = new HashMap<>();
            repo2.put("name", "streamci");
            repo2.put("full_name", "user/streamci");
            repositories.add(repo2);
        }

        return repositories;
    }
}