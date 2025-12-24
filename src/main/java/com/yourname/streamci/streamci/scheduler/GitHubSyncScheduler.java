package com.yourname.streamci.streamci.scheduler;

import com.yourname.streamci.streamci.service.GitHubService;
import com.yourname.streamci.streamci.model.SyncResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * automatically syncs github repositories to keep build data fresh for pattern analysis
 * configure repos to sync via GITHUB_SYNC_REPOS environment variable
 * format: "owner1/repo1,owner2/repo2,owner3/repo3"
 */
@Component
public class GitHubSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GitHubSyncScheduler.class);
    private final GitHubService gitHubService;

    @Value("${github.sync.repos:}")
    private String syncReposConfig;

    @Value("${github.sync.enabled:false}")
    private boolean syncEnabled;

    public GitHubSyncScheduler(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    /**
     * runs every 30 minutes to keep build data fresh
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void autoSyncRepositories() {
        if (!syncEnabled) {
            logger.debug("github auto-sync is disabled");
            return;
        }

        if (syncReposConfig == null || syncReposConfig.trim().isEmpty()) {
            logger.warn("github auto-sync enabled but no repos configured - set GITHUB_SYNC_REPOS");
            return;
        }

        List<String> repos = parseReposConfig(syncReposConfig);
        if (repos.isEmpty()) {
            logger.warn("no valid repos found in config: {}", syncReposConfig);
            return;
        }

        logger.info("starting auto-sync for {} repositories", repos.size());
        int successCount = 0;
        int failureCount = 0;

        for (String repoConfig : repos) {
            try {
                String[] parts = repoConfig.split("/");
                if (parts.length != 2) {
                    logger.warn("invalid repo format: {} (expected owner/repo)", repoConfig);
                    failureCount++;
                    continue;
                }

                String owner = parts[0].trim();
                String repo = parts[1].trim();

                logger.info("syncing {}/{}...", owner, repo);
                SyncResult result = gitHubService.syncRepository(owner, repo);

                if (result.isSuccess()) {
                    logger.info("synced {}/{}: {} pipelines, {} builds",
                            owner, repo, result.getPipelinesSynced(), result.getBuildsSynced());
                    successCount++;
                } else {
                    logger.error("failed to sync {}/{}: {}", owner, repo, result.getMessage());
                    failureCount++;
                }

            } catch (Exception e) {
                logger.error("error syncing {}: {}", repoConfig, e.getMessage());
                failureCount++;
            }
        }

        logger.info("auto-sync completed: {} succeeded, {} failed", successCount, failureCount);
    }

    private List<String> parseReposConfig(String config) {
        List<String> repos = new ArrayList<>();
        if (config == null || config.trim().isEmpty()) {
            return repos;
        }

        String[] parts = config.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                repos.add(trimmed);
            }
        }
        return repos;
    }
}
