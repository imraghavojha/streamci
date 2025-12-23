package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.dto.WebhookResponse;
import com.yourname.streamci.streamci.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/api/webhooks/github/{clerkUserId}")
    public ResponseEntity<WebhookResponse> handleGitHubWebhook(
            @PathVariable String clerkUserId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        logger.info("received github webhook for user {} - event type: {}", clerkUserId, eventType);

        // verify signature with user-specific secret
        if (!webhookService.verifySignatureForUser(payload, signature, clerkUserId)) {
            logger.warn("invalid signature for webhook request from user: {}", clerkUserId);
            return ResponseEntity.status(401)
                    .body(WebhookResponse.error("invalid signature"));
        }

        // process the webhook
        webhookService.processWebhookAsync(eventType, payload);

        return ResponseEntity.ok(WebhookResponse.accepted());
    }

    // keep old endpoint for backward compatibility (uses global secret)
    @PostMapping("/api/webhooks/github")
    public ResponseEntity<WebhookResponse> handleGitHubWebhookLegacy(
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        logger.info("received github webhook (legacy) - event type: {}", eventType);

        // verify signature with global secret
        if (!webhookService.verifySignature(payload, signature)) {
            logger.warn("invalid signature for legacy webhook request");
            return ResponseEntity.status(401)
                    .body(WebhookResponse.error("invalid signature"));
        }

        // process the webhook
        webhookService.processWebhookAsync(eventType, payload);

        return ResponseEntity.ok(WebhookResponse.accepted());
    }
}
