package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;

@RestController
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/api/webhooks/github/{clerkUserId}")
    public ResponseEntity<Map<String, String>> handleGitHubWebhook(
            @PathVariable String clerkUserId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        logger.info("Received GitHub webhook for user {} - Event type: {}", clerkUserId, eventType);

        // verify signature with user-specific secret
        if (!webhookService.verifySignatureForUser(payload, signature, clerkUserId)) {
            logger.warn("Invalid signature for webhook request from user: {}", clerkUserId);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid signature");
            return ResponseEntity.status(401).body(error);
        }

        // process the webhook
        webhookService.processWebhookAsync(eventType, payload);

        Map<String, String> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("message", "Event received");
        return ResponseEntity.ok(response);
    }

    // Keep the old endpoint for backward compatibility (uses global secret)
    @PostMapping("/api/webhooks/github")
    public ResponseEntity<Map<String, String>> handleGitHubWebhookLegacy(
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        logger.info("Received GitHub webhook (legacy) - Event type: {}", eventType);

        // verify signature with global secret
        if (!webhookService.verifySignature(payload, signature)) {
            logger.warn("Invalid signature for legacy webhook request");
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid signature");
            return ResponseEntity.status(401).body(error);
        }

        // process the webhook
        webhookService.processWebhookAsync(eventType, payload);

        Map<String, String> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("message", "Event received");
        return ResponseEntity.ok(response);
    }
}