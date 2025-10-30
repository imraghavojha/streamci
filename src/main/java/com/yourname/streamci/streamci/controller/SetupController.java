package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.User;
import com.yourname.streamci.streamci.service.UserService;
import com.yourname.streamci.streamci.service.GitHubService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private static final Logger logger = LoggerFactory.getLogger(SetupController.class);

    private final UserService userService;
    private final GitHubService gitHubService;

    public SetupController(UserService userService, GitHubService gitHubService) {
        this.userService = userService;
        this.gitHubService = gitHubService;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> saveToken(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();

        try {
            String clerkUserId = body.get("clerkUserId");
            String token = body.get("token");

            if (clerkUserId == null || token == null) {
                response.put("success", false);
                response.put("error", "clerkUserId and token required");
                return ResponseEntity.badRequest().body(response);
            }

            // create user if doesn't exist
            userService.createOrUpdateUser(clerkUserId);

            // save encrypted token
            User user = userService.saveGithubToken(clerkUserId, token);

            response.put("success", true);
            response.put("message", "token saved successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("failed to save token: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();

        try {
            String clerkUserId = body.get("clerkUserId");

            User user = userService.findByClerkUserId(clerkUserId)
                    .orElseThrow(() -> new RuntimeException("user not found"));

            String token = userService.decryptGithubToken(user);

            // test github api with token
            boolean valid = gitHubService.testConnectionWithToken(token);

            if (valid) {
                user.setTokenValidated(true);
                userService.createOrUpdateUser(clerkUserId); // updates lastActiveAt

                response.put("success", true);
                response.put("valid", true);
                response.put("message", "token is valid");
            } else {
                response.put("success", true);
                response.put("valid", false);
                response.put("message", "token is invalid");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("failed to validate token: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/webhook-secret")
    public ResponseEntity<Map<String, Object>> saveWebhookSecret(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();

        try {
            String clerkUserId = body.get("clerkUserId");
            String webhookSecret = body.get("webhookSecret");

            if (clerkUserId == null || webhookSecret == null) {
                response.put("success", false);
                response.put("error", "clerkUserId and webhookSecret required");
                return ResponseEntity.badRequest().body(response);
            }

            // create user if doesn't exist
            User user = userService.createOrUpdateUser(clerkUserId);
            user.setWebhookSecret(webhookSecret);
            userService.saveUser(user);

            logger.info("webhook secret saved for user: {}", clerkUserId);

            response.put("success", true);
            response.put("message", "webhook secret saved successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("failed to save webhook secret: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/webhook-secret/{clerkUserId}")
    public ResponseEntity<Map<String, Object>> getWebhookSecret(@PathVariable String clerkUserId) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = userService.findByClerkUserId(clerkUserId)
                    .orElse(null);

            if (user != null && user.getWebhookSecret() != null) {
                response.put("success", true);
                response.put("webhookSecret", user.getWebhookSecret());
            } else {
                response.put("success", true);
                response.put("webhookSecret", null);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("failed to get webhook secret: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}