package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private final DashboardWebSocketService webSocketService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${github.webhook.secret:}")
    private String webhookSecret;

    public WebhookService(DashboardWebSocketService webSocketService, UserService userService) {
        this.webSocketService = webSocketService;
        this.userService = userService;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void validateConfiguration() {
        // allow empty secret only in test profile
        if (webhookSecret == null || webhookSecret.trim().isEmpty() || webhookSecret.equals("default-secret")) {
            logger.warn("github webhook secret not configured - signature verification will fail in production");
            logger.warn("set GITHUB_WEBHOOK_SECRET environment variable or github.webhook.secret property");
        }
    }

    public boolean verifySignature(String payload, String signature) {
        // fail if no signature provided
        if (signature == null || signature.trim().isEmpty()) {
            logger.warn("webhook signature missing - rejecting request");
            return false;
        }

        // fail if secret not configured properly
        if (webhookSecret == null || webhookSecret.trim().isEmpty() || webhookSecret.equals("default-secret")) {
            logger.error("webhook secret not configured - cannot verify signature");
            return false;
        }

        return verifySignatureWithSecret(payload, signature, webhookSecret);
    }

    public boolean verifySignatureForUser(String payload, String signature, String clerkUserId) {
        try {
            User user = userService.findByClerkUserId(clerkUserId).orElse(null);

            if (user == null || user.getWebhookSecret() == null) {
                logger.warn("no webhook secret found for user: {}", clerkUserId);
                return false;
            }

            return verifySignatureWithSecret(payload, signature, user.getWebhookSecret());
        } catch (Exception e) {
            logger.error("error verifying signature for user {}: {}", clerkUserId, e.getMessage());
            return false;
        }
    }

    private boolean verifySignatureWithSecret(String payload, String signature, String secret) {
        if (signature == null) {
            logger.warn("no signature provided");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            String expected = "sha256=" + sb.toString();
            boolean valid = expected.equals(signature);

            if (!valid) {
                logger.warn("signature mismatch - expected: {}, received: {}", expected, signature);
            }

            return valid;
        } catch (Exception e) {
            logger.error("signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    @Async
    public void processWebhookAsync(String eventType, String payload) {
        try {
            logger.info("processing github webhook: {}", eventType);

            if ("workflow_run".equals(eventType)) {
                processWorkflowRunEvent(payload);
            } else if ("push".equals(eventType)) {
                processPushEvent(payload);
            } else {
                logger.debug("ignoring event type: {}", eventType);
            }

        } catch (Exception e) {
            logger.error("webhook processing failed: {}", e.getMessage(), e);
        }
    }

    private void processWorkflowRunEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode workflowRun = root.get("workflow_run");

            if (workflowRun == null) {
                logger.warn("no workflow_run found in payload");
                return;
            }

            String action = root.get("action").asText();
            String status = workflowRun.get("status").asText();
            String conclusion = workflowRun.has("conclusion") && !workflowRun.get("conclusion").isNull()
                    ? workflowRun.get("conclusion").asText() : null;

            Map<String, Object> buildData = createBuildData(workflowRun, action);

            logger.info("workflow {} - status: {}, conclusion: {}",
                    workflowRun.get("id").asLong(), status, conclusion);

            if ("completed".equals(action)) {
                broadcastBuildCompleted(buildData, conclusion);
            } else if ("in_progress".equals(status)) {
                broadcastBuildStarted(buildData);
            } else if ("queued".equals(status)) {
                broadcastBuildQueued(buildData);
            }

        } catch (Exception e) {
            logger.error("failed to process workflow_run event: {}", e.getMessage(), e);
        }
    }

    private void processPushEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode repository = root.get("repository");

            if (repository == null) return;

            String repoName = repository.get("full_name").asText();
            String branch = root.get("ref").asText().replace("refs/heads/", "");

            Map<String, Object> pushData = new HashMap<>();
            pushData.put("repository", repoName);
            pushData.put("branch", branch);
            pushData.put("commits", root.get("commits").size());
            pushData.put("pusher", root.get("pusher").get("name").asText());

            logger.info("push event: {} commits to {}/{}",
                    pushData.get("commits"), repoName, branch);

            webSocketService.broadcastDashboardUpdate("push_received", pushData);

        } catch (Exception e) {
            logger.error("failed to process push event: {}", e.getMessage(), e);
        }
    }

    private Map<String, Object> createBuildData(JsonNode workflowRun, String action) {
        Map<String, Object> buildData = new HashMap<>();

        buildData.put("workflow_id", workflowRun.get("id").asLong());
        buildData.put("workflow_name", workflowRun.get("name").asText());
        buildData.put("status", workflowRun.get("status").asText());
        buildData.put("repository", workflowRun.get("repository").get("full_name").asText());
        buildData.put("branch", workflowRun.get("head_branch").asText());
        buildData.put("commit_sha", workflowRun.get("head_sha").asText().substring(0, 7));
        buildData.put("run_number", workflowRun.get("run_number").asInt());
        buildData.put("created_at", workflowRun.get("created_at").asText());
        buildData.put("updated_at", workflowRun.get("updated_at").asText());
        buildData.put("html_url", workflowRun.get("html_url").asText());
        buildData.put("action", action);

        if (workflowRun.has("conclusion") && !workflowRun.get("conclusion").isNull()) {
            buildData.put("conclusion", workflowRun.get("conclusion").asText());
        }

        return buildData;
    }

    private void broadcastBuildCompleted(Map<String, Object> buildData, String conclusion) {
        buildData.put("event_type", "build_completed");
        buildData.put("timestamp", LocalDateTime.now());

        logger.info("broadcasting build completed: {} - {}",
                buildData.get("workflow_name"), conclusion);

        webSocketService.broadcastDashboardUpdate("build_completed", buildData);

        if ("failure".equals(conclusion)) {
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("type", "build_failure");
            alertData.put("severity", "error");
            alertData.put("workflow", buildData.get("workflow_name"));
            alertData.put("repository", buildData.get("repository"));
            alertData.put("branch", buildData.get("branch"));

            webSocketService.broadcastDashboardUpdate("alert", alertData);
        }
    }

    private void broadcastBuildStarted(Map<String, Object> buildData) {
        buildData.put("event_type", "build_started");
        buildData.put("timestamp", LocalDateTime.now());

        logger.info("broadcasting build started: {}", buildData.get("workflow_name"));
        webSocketService.broadcastDashboardUpdate("build_started", buildData);
    }

    private void broadcastBuildQueued(Map<String, Object> buildData) {
        buildData.put("event_type", "build_queued");
        buildData.put("timestamp", LocalDateTime.now());

        logger.info("broadcasting build queued: {}", buildData.get("workflow_name"));
        webSocketService.broadcastDashboardUpdate("build_queued", buildData);
    }
}