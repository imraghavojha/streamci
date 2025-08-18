package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.Alert;
import com.yourname.streamci.streamci.model.AlertConfig;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private final RestTemplate restTemplate;

    @Value("${alerts.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${alerts.slack.webhook:}")
    private String slackWebhook;

    public NotificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendAlert(Alert alert, AlertConfig config) {
        // log the alert (always do this)
        logAlert(alert);

        // send to configured channels
        if (config.getNotifySlack() && !slackWebhook.isEmpty()) {
            sendSlackNotification(alert);
        }

        if (config.getNotifyEmail() && emailEnabled) {
            sendEmailNotification(alert);
        }

        if (config.getNotifyWebhook() && config.getNotificationEndpoint() != null) {
            sendWebhookNotification(alert, config.getNotificationEndpoint());
        }
    }

    private void logAlert(Alert alert) {
        String emoji = getEmoji(alert.getSeverity());

        logger.warn("\n" +
                        "==================== ALERT {} ====================\n" +
                        "Pipeline: {}\n" +
                        "Type: {}\n" +
                        "Severity: {}\n" +
                        "Title: {}\n" +
                        "Message: {}\n" +
                        "Recommendation: {}\n" +
                        "Threshold: {} | Actual: {}\n" +
                        "====================================================",
                emoji,
                alert.getPipeline().getName(),
                alert.getType(),
                alert.getSeverity(),
                alert.getTitle(),
                alert.getMessage(),
                alert.getRecommendation(),
                alert.getThresholdValue(),
                alert.getActualValue()
        );
    }

    private void sendSlackNotification(Alert alert) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("text", formatSlackMessage(alert));
            message.put("username", "StreamCI Alert");
            message.put("icon_emoji", getEmoji(alert.getSeverity()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    slackWebhook, request, String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Slack notification sent for alert {}", alert.getId());
            }
        } catch (Exception e) {
            logger.error("Failed to send Slack notification: {}", e.getMessage());
        }
    }

    private void sendEmailNotification(Alert alert) {
        // placeholder for email implementation
        logger.info("Email notification would be sent for alert {}", alert.getId());
    }

    private void sendWebhookNotification(Alert alert, String endpoint) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alertId", alert.getId());
            payload.put("pipeline", alert.getPipeline().getName());
            payload.put("type", alert.getType().toString());
            payload.put("severity", alert.getSeverity().toString());
            payload.put("title", alert.getTitle());
            payload.put("message", alert.getMessage());
            payload.put("timestamp", alert.getCreatedAt());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(endpoint, request, String.class);
            logger.info("Webhook notification sent to {}", endpoint);

        } catch (Exception e) {
            logger.error("Failed to send webhook notification: {}", e.getMessage());
        }
    }

    private String formatSlackMessage(Alert alert) {
        return String.format(
                "*%s*\n%s\n\n*Pipeline:* %s\n*Severity:* %s\n*Message:* %s\n*Action:* %s",
                alert.getTitle(),
                getEmoji(alert.getSeverity()),
                alert.getPipeline().getName(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getRecommendation()
        );
    }

    private String getEmoji(Alert.AlertSeverity severity) {
        return switch (severity) {
            case INFO -> "ℹ️";
            case WARNING -> "⚠️";
            case CRITICAL -> "🔴";
            case EMERGENCY -> "🚨";
        };
    }
}