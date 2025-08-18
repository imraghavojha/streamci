package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.model.Pipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private final PipelineService pipelineService;
    private final BuildService buildService;
    private final QueueService queueService;
    private final ObjectMapper objectMapper;

    @Value("${github.webhook.secret:default-secret}")
    private String webhookSecret;

    public WebhookService(PipelineService pipelineService,
                          BuildService buildService,
                          QueueService queueService) {
        this.pipelineService = pipelineService;
        this.buildService = buildService;
        this.queueService = queueService;
        this.objectMapper = new ObjectMapper();
    }

    public boolean verifySignature(String payload, String signature) {
        // skip verification if no secret is configured or in test mode
        if (webhookSecret.equals("default-secret") || webhookSecret.isEmpty()) {
            logger.warn("Webhook signature verification disabled (no secret configured)");
            return true;
        }

        // allow test requests without signature in development
        if (signature == null) {
            logger.warn("No signature provided - rejecting in production mode");
            return false;
        }

        if (!signature.startsWith("sha256=")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes());

            String computed = "sha256=" + bytesToHex(hash);
            return computed.equals(signature);

        } catch (Exception e) {
            logger.error("Error verifying signature: {}", e.getMessage());
            return false;
        }
    }

    @Async
    public void processWebhookAsync(String eventType, String payload) {
        logger.info("Processing webhook event: {}", eventType);

        // only process workflow_run events
        if (!"workflow_run".equals(eventType)) {
            logger.info("Ignoring non-workflow_run event: {}", eventType);
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode workflowRun = root.path("workflow_run");

            if (workflowRun.isMissingNode()) {
                logger.warn("No workflow_run data in payload");
                return;
            }

            // extract repository info
            String repoName = root.path("repository").path("name").asText();

            // find or create pipeline
            Pipeline pipeline = findOrCreatePipeline(repoName);

            // extract workflow info
            String workflowId = workflowRun.path("id").asText();
            String status = workflowRun.path("status").asText("unknown");
            String conclusion = workflowRun.path("conclusion").asText();

            logger.info("processing workflow {} with status: {} conclusion: {}",
                    workflowId, status, conclusion);

            // track queue status based on workflow status
            if ("queued".equals(status) || "waiting".equals(status) || "pending".equals(status)) {
                // build is queued
                logger.info("tracking queued build: {}", workflowId);
                queueService.trackBuildQueued(workflowId, pipeline.getId());

            } else if ("in_progress".equals(status)) {
                // build started running
                logger.info("tracking build started: {}", workflowId);
                queueService.trackBuildStarted(workflowId);

            } else if ("completed".equals(status)) {
                // build completed
                logger.info("tracking build completed: {}", workflowId);
                queueService.trackBuildCompleted(workflowId);

                // also create the build record as before
                Build build = extractBuildFromWebhook(workflowRun, pipeline);
                Build savedBuild = buildService.saveBuild(build);
                logger.info("created build {} for pipeline {}", savedBuild.getBuildId(), pipeline.getName());

                updatePipelineStatus(pipeline, build);
            }

        } catch (Exception e) {
            logger.error("error processing webhook: {}", e.getMessage(), e);
        }
    }

    private Pipeline findOrCreatePipeline(String repoName) {
        List<Pipeline> pipelines = pipelineService.getAllPipelines();

        return pipelines.stream()
                .filter(p -> p.getName().equals(repoName))
                .findFirst()
                .orElseGet(() -> {
                    Pipeline newPipeline = Pipeline.builder()
                            .name(repoName)
                            .status("active")
                            .duration(0)
                            .build();
                    return pipelineService.savePipeline(newPipeline);
                });
    }

    private Build extractBuildFromWebhook(JsonNode workflowRun, Pipeline pipeline) {
        String status = workflowRun.path("status").asText("unknown");
        String conclusion = workflowRun.path("conclusion").asText();

        // map github status to our status - use conclusion if available, otherwise status
        String buildStatus = !conclusion.isEmpty() && !conclusion.equals("null") ? conclusion : status;

        // parse timestamps correctly from ISO format with Z
        LocalDateTime startTime = parseDateTime(workflowRun.path("run_started_at").asText());
        if (startTime == null) {
            startTime = parseDateTime(workflowRun.path("created_at").asText());
        }
        LocalDateTime endTime = parseDateTime(workflowRun.path("updated_at").asText());

        // calculate duration in seconds
        long duration = 0;
        if (startTime != null && endTime != null) {
            duration = java.time.Duration.between(startTime, endTime).getSeconds();
        }

        // extract commit info
        String commitHash = workflowRun.path("head_sha").asText("unknown");
        String branch = workflowRun.path("head_branch").asText("main");

        // get actor info
        String committer = workflowRun.path("actor").path("login").asText("unknown");

        return Build.builder()
                .pipeline(pipeline)
                .status(mapStatus(buildStatus))
                .startTime(startTime)
                .endTime(endTime)
                .duration(duration)
                .commitHash(commitHash)
                .committer(committer)
                .branch(branch)
                .build();
    }

    private void updatePipelineStatus(Pipeline pipeline, Build build) {
        pipeline.setStatus(build.getStatus());

        // update average duration
        List<Build> allBuilds = buildService.getBuildsByPipelineId(pipeline.getId());
        if (!allBuilds.isEmpty()) {
            int avgDuration = (int) allBuilds.stream()
                    .filter(b -> b.getDuration() != null)
                    .mapToLong(Build::getDuration)
                    .average()
                    .orElse(0);
            pipeline.setDuration(avgDuration);
        }

        pipelineService.updatePipeline(pipeline.getId(), pipeline);
    }

    private String mapStatus(String githubStatus) {
        if (githubStatus == null) return "unknown";

        return switch (githubStatus.toLowerCase()) {
            case "success", "completed" -> "success";
            case "failure", "failed" -> "failure";
            case "cancelled", "canceled" -> "cancelled";
            case "in_progress", "queued", "pending", "waiting", "requested" -> "running";
            case "skipped" -> "skipped";
            case "null", "" -> "unknown";
            default -> {
                logger.debug("Unknown GitHub status: {}", githubStatus);
                yield "unknown";
            }
        };
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty() || "null".equals(dateTimeStr)) {
            return LocalDateTime.now();
        }

        try {
            if (dateTimeStr.contains("Z") || dateTimeStr.contains("+")) {
                java.time.Instant instant = java.time.Instant.parse(dateTimeStr);
                return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
            } else {
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse datetime: {}", dateTimeStr);
            return LocalDateTime.now();
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}