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

    @PostMapping("/api/webhooks/github")
    public ResponseEntity<Map<String, String>> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        logger.info("Received GitHub webhook - Event type: {}", eventType);

        // verify signature
        if (!webhookService.verifySignature(payload, signature)) {
            logger.warn("Invalid signature for webhook request");
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