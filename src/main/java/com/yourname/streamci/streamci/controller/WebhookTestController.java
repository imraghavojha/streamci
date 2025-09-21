// src/main/java/com/yourname/streamci/streamci/controller/WebhookTestController.java
package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.service.WebhookService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/test")
public class WebhookTestController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookTestController.class);
    private final WebhookService webhookService;

    public WebhookTestController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook/workflow-completed")
    public ResponseEntity<Map<String, String>> simulateWorkflowCompleted(
            @RequestParam(defaultValue = "success") String conclusion,
            @RequestParam(defaultValue = "test-repo") String repository,
            @RequestParam(defaultValue = "main") String branch) {

        logger.info("simulating workflow completion: {} for {}/{}", conclusion, repository, branch);

        String fakePayload = createFakeWorkflowPayload(conclusion, repository, branch);
        webhookService.processWebhookAsync("workflow_run", fakePayload);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "fake workflow " + conclusion + " event sent");
        response.put("repository", repository);
        response.put("branch", branch);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook/workflow-started")
    public ResponseEntity<Map<String, String>> simulateWorkflowStarted(
            @RequestParam(defaultValue = "test-repo") String repository,
            @RequestParam(defaultValue = "main") String branch) {

        logger.info("simulating workflow started for {}/{}", repository, branch);

        String fakePayload = createFakeWorkflowPayload("in_progress", repository, branch);
        webhookService.processWebhookAsync("workflow_run", fakePayload);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "fake workflow started event sent");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook/push")
    public ResponseEntity<Map<String, String>> simulatePush(
            @RequestParam(defaultValue = "test-repo") String repository,
            @RequestParam(defaultValue = "main") String branch) {

        logger.info("simulating push to {}/{}", repository, branch);

        String fakePayload = createFakePushPayload(repository, branch);
        webhookService.processWebhookAsync("push", fakePayload);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "fake push event sent");

        return ResponseEntity.ok(response);
    }

    private String createFakeWorkflowPayload(String conclusion, String repository, String branch) {
        long workflowId = System.currentTimeMillis();
        String status = "in_progress".equals(conclusion) ? "in_progress" : "completed";
        String conclusionField = "in_progress".equals(conclusion) ? "null" : "\"" + conclusion + "\"";

        return String.format("""
            {
              "action": "%s",
              "workflow_run": {
                "id": %d,
                "name": "CI Test Suite",
                "status": "%s",
                "conclusion": %s,
                "head_branch": "%s",
                "head_sha": "abc123def456",
                "run_number": %d,
                "created_at": "%s",
                "updated_at": "%s",
                "run_started_at": "%s",
                "html_url": "https://github.com/%s/actions/runs/%d",
                "repository": {
                  "full_name": "%s"
                }
              }
            }
            """,
                status.equals("in_progress") ? "in_progress" : "completed",
                workflowId,
                status,
                conclusionField,
                branch,
                (int)(System.currentTimeMillis() % 1000),
                java.time.Instant.now().toString(),
                java.time.Instant.now().toString(),
                java.time.Instant.now().toString(),
                repository,
                workflowId,
                repository
        );
    }

    private String createFakePushPayload(String repository, String branch) {
        return String.format("""
            {
              "ref": "refs/heads/%s",
              "commits": [
                {
                  "id": "abc123def456",
                  "message": "test commit for webhook simulation"
                }
              ],
              "pusher": {
                "name": "test-user"
              },
              "repository": {
                "full_name": "%s"
              }
            }
            """,
                branch,
                repository
        );
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getTestStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("webhook_service", "active");
        status.put("test_endpoints", Map.of(
                "workflow_completed", "POST /api/test/webhook/workflow-completed",
                "workflow_started", "POST /api/test/webhook/workflow-started",
                "push", "POST /api/test/webhook/push"
        ));
        status.put("websocket_endpoint", "/ws/dashboard");

        return ResponseEntity.ok(status);
    }
}