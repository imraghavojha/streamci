package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import com.yourname.streamci.streamci.event.MetricsCalculatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final AlertConfigRepository configRepository;
    private final PipelineMetricsRepository metricsRepository;
    private final NotificationService notificationService;

    public AlertService(AlertRepository alertRepository,
                        AlertConfigRepository configRepository,
                        PipelineMetricsRepository metricsRepository,
                        NotificationService notificationService) {
        this.alertRepository = alertRepository;
        this.configRepository = configRepository;
        this.metricsRepository = metricsRepository;
        this.notificationService = notificationService;
    }

    @EventListener
    public void handleMetricsCalculated(MetricsCalculatedEvent event) {
        try {
            checkAlertsForPipeline(event.getPipeline(), event.getMetrics());
        } catch (Exception e) {
            logger.error("Error checking alerts for pipeline {}: {}",
                    event.getPipeline().getId(), e.getMessage());
        }
    }

    @Transactional
    public void checkAlertsForPipeline(Pipeline pipeline, PipelineMetrics metrics) {
        logger.debug("Checking alerts for pipeline {}", pipeline.getName());

        // check each alert type
        checkSuccessRateAlert(pipeline, metrics);
        checkDurationAlert(pipeline, metrics);
        checkConsecutiveFailuresAlert(pipeline, metrics);
        checkStaleDataAlert(pipeline, metrics);

        // auto-resolve alerts that are no longer valid
        autoResolveAlerts(pipeline, metrics);
    }

    private void checkSuccessRateAlert(Pipeline pipeline, PipelineMetrics metrics) {
        AlertConfig config = getConfig(pipeline.getId(), Alert.AlertType.SUCCESS_RATE_DROP);
        if (!config.getEnabled()) return;

        Double threshold = config.getCriticalThreshold() != null ?
                config.getCriticalThreshold() : 80.0;

        if (metrics.getSuccessRate() < threshold && metrics.getTotalBuilds() >= 5) {
            Alert.AlertSeverity severity = metrics.getSuccessRate() < 50 ?
                    Alert.AlertSeverity.CRITICAL : Alert.AlertSeverity.WARNING;

            String fingerprint = String.format("success_rate_%d", pipeline.getId());

            // check if alert already exists
            Optional<Alert> existing = alertRepository.findActiveByFingerprint(fingerprint);
            if (existing.isPresent() && !shouldRenotify(existing.get(), config)) {
                logger.debug("Alert already exists for success rate, skipping");
                return;
            }

            Alert alert = Alert.builder()
                    .pipeline(pipeline)
                    .type(Alert.AlertType.SUCCESS_RATE_DROP)
                    .severity(severity)
                    .status(Alert.AlertStatus.ACTIVE)
                    .title(String.format("Low success rate for %s", pipeline.getName()))
                    .message(String.format(
                            "Success rate has dropped to %.1f%% (threshold: %.1f%%). " +
                                    "%d out of %d builds failed in the last evaluation window.",
                            metrics.getSuccessRate(), threshold,
                            metrics.getFailedBuilds(), metrics.getTotalBuilds()
                    ))
                    .recommendation(determineRecommendation(Alert.AlertType.SUCCESS_RATE_DROP, metrics))
                    .thresholdValue(threshold)
                    .actualValue(metrics.getSuccessRate())
                    .metric("success_rate")
                    .fingerprint(fingerprint)
                    .triggeredBy("metrics_check")
                    .build();

            saveAndNotify(alert, config);
        }
    }

    private void checkDurationAlert(Pipeline pipeline, PipelineMetrics metrics) {
        AlertConfig config = getConfig(pipeline.getId(), Alert.AlertType.DURATION_INCREASE);
        if (!config.getEnabled()) return;

        // compare to previous metrics
        Optional<PipelineMetrics> previousOpt = metricsRepository
                .findTopByPipelineIdOrderByCalculatedAtDesc(pipeline.getId());

        if (previousOpt.isEmpty() || metrics.getAvgDurationSeconds() == 0) return;

        PipelineMetrics previous = previousOpt.get();
        if (previous.getAvgDurationSeconds() == 0) return;

        double increasePercent = ((double)(metrics.getAvgDurationSeconds() - previous.getAvgDurationSeconds())
                / previous.getAvgDurationSeconds()) * 100;

        Double threshold = config.getWarningThreshold() != null ?
                config.getWarningThreshold() : 50.0;

        if (increasePercent > threshold) {
            String fingerprint = String.format("duration_%d", pipeline.getId());

            Optional<Alert> existing = alertRepository.findActiveByFingerprint(fingerprint);
            if (existing.isPresent() && !shouldRenotify(existing.get(), config)) {
                return;
            }

            Alert alert = Alert.builder()
                    .pipeline(pipeline)
                    .type(Alert.AlertType.DURATION_INCREASE)
                    .severity(increasePercent > 100 ?
                            Alert.AlertSeverity.CRITICAL : Alert.AlertSeverity.WARNING)
                    .status(Alert.AlertStatus.ACTIVE)
                    .title(String.format("Build duration increased for %s", pipeline.getName()))
                    .message(String.format(
                            "Average build duration increased by %.1f%% from %d to %d seconds. " +
                                    "This could indicate performance issues or resource constraints.",
                            increasePercent, previous.getAvgDurationSeconds(),
                            metrics.getAvgDurationSeconds()
                    ))
                    .recommendation(determineRecommendation(Alert.AlertType.DURATION_INCREASE, metrics))
                    .thresholdValue(threshold)
                    .actualValue(increasePercent)
                    .metric("duration_increase_percent")
                    .fingerprint(fingerprint)
                    .triggeredBy("metrics_check")
                    .build();

            saveAndNotify(alert, config);
        }
    }

    private void checkConsecutiveFailuresAlert(Pipeline pipeline, PipelineMetrics metrics) {
        AlertConfig config = getConfig(pipeline.getId(), Alert.AlertType.CONSECUTIVE_FAILURES);
        if (!config.getEnabled()) return;

        Integer threshold = config.getWarningThreshold() != null ?
                config.getWarningThreshold().intValue() : 3;

        if (metrics.getConsecutiveFailures() >= threshold) {
            String fingerprint = String.format("consecutive_%d", pipeline.getId());

            Optional<Alert> existing = alertRepository.findActiveByFingerprint(fingerprint);
            if (existing.isPresent() && !shouldRenotify(existing.get(), config)) {
                return;
            }

            Alert.AlertSeverity severity = metrics.getConsecutiveFailures() >= 5 ?
                    Alert.AlertSeverity.EMERGENCY : Alert.AlertSeverity.CRITICAL;

            Alert alert = Alert.builder()
                    .pipeline(pipeline)
                    .type(Alert.AlertType.CONSECUTIVE_FAILURES)
                    .severity(severity)
                    .status(Alert.AlertStatus.ACTIVE)
                    .title(String.format("Multiple consecutive failures for %s", pipeline.getName()))
                    .message(String.format(
                            "%d consecutive builds have failed. Last success was %s. " +
                                    "This indicates a systematic issue that needs immediate attention.",
                            metrics.getConsecutiveFailures(),
                            metrics.getLastSuccess() != null ?
                                    metrics.getLastSuccess().toString() : "unknown"
                    ))
                    .recommendation("Check recent commits, review error logs, verify external dependencies")
                    .thresholdValue(threshold.doubleValue())
                    .actualValue(metrics.getConsecutiveFailures().doubleValue())
                    .metric("consecutive_failures")
                    .fingerprint(fingerprint)
                    .triggeredBy("metrics_check")
                    .build();

            saveAndNotify(alert, config);
        }
    }

    private void checkStaleDataAlert(Pipeline pipeline, PipelineMetrics metrics) {
        AlertConfig config = getConfig(pipeline.getId(), Alert.AlertType.STALE_PIPELINE);  // ADD THIS LINE
        if (!config.getEnabled()) return;  // ADD THIS LINE

        if (metrics.getLastSuccess() == null && metrics.getLastFailure() == null) {
            return;
        }

        LocalDateTime lastActivity = metrics.getLastSuccess();
        if (metrics.getLastFailure() != null &&
                (lastActivity == null || metrics.getLastFailure().isAfter(lastActivity))) {
            lastActivity = metrics.getLastFailure();
        }

        if (lastActivity == null) return;

        long hoursSinceActivity = java.time.Duration.between(
                lastActivity, LocalDateTime.now()
        ).toHours();

        if (hoursSinceActivity > 24) {
            String fingerprint = String.format("stale_%d", pipeline.getId());

            Optional<Alert> existing = alertRepository.findActiveByFingerprint(fingerprint);
            if (existing.isPresent()) {
                return;
            }

            Alert alert = Alert.builder()
                    .pipeline(pipeline)
                    .type(Alert.AlertType.STALE_PIPELINE)
                    .severity(Alert.AlertSeverity.INFO)
                    .status(Alert.AlertStatus.ACTIVE)
                    .title(String.format("No recent activity for %s", pipeline.getName()))
                    .message(String.format(
                            "No builds detected for %d hours. Last activity was at %s.",
                            hoursSinceActivity, lastActivity
                    ))
                    .recommendation("Check if CI/CD is configured correctly or if the project is still active")
                    .thresholdValue(24.0)
                    .actualValue((double) hoursSinceActivity)
                    .metric("hours_since_activity")
                    .fingerprint(fingerprint)
                    .triggeredBy("metrics_check")
                    .build();

            saveAndNotify(alert, config);  // NOW config is defined and can be used
        }
    }

    private void autoResolveAlerts(Pipeline pipeline, PipelineMetrics metrics) {
        List<Alert> activeAlerts = alertRepository
                .findByPipelineIdAndStatusOrderByCreatedAtDesc(
                        pipeline.getId(), Alert.AlertStatus.ACTIVE
                );

        for (Alert alert : activeAlerts) {
            boolean shouldResolve = false;
            String resolveReason = "";

            switch (alert.getType()) {
                case SUCCESS_RATE_DROP:
                    if (metrics.getSuccessRate() >= 80) {
                        shouldResolve = true;
                        resolveReason = String.format("Success rate recovered to %.1f%%",
                                metrics.getSuccessRate());
                    }
                    break;

                case CONSECUTIVE_FAILURES:
                    if (metrics.getConsecutiveFailures() == 0) {
                        shouldResolve = true;
                        resolveReason = "Build succeeded, breaking failure streak";
                    }
                    break;

                case STALE_PIPELINE:
                    if (metrics.getBuildsToday() > 0) {
                        shouldResolve = true;
                        resolveReason = "New build activity detected";
                    }
                    break;
            }

            if (shouldResolve) {
                alert.setStatus(Alert.AlertStatus.RESOLVED);
                alert.setResolvedAt(LocalDateTime.now());
                alert.setResolvedBy("auto");
                alert.setNotes(resolveReason);
                alertRepository.save(alert);
                logger.info("Auto-resolved alert {} for pipeline {}: {}",
                        alert.getId(), pipeline.getName(), resolveReason);
            }
        }
    }

    private AlertConfig getConfig(Integer pipelineId, Alert.AlertType type) {
        // first check pipeline-specific config
        Optional<AlertConfig> config = configRepository
                .findByPipelineIdAndAlertType(pipelineId, type);

        if (config.isPresent()) {
            return config.get();
        }

        // fall back to global config
        config = configRepository.findByPipelineIsNullAndAlertType(type);

        if (config.isPresent()) {
            return config.get();
        }

        // return default config
        return getDefaultConfig();
    }

    private AlertConfig getDefaultConfig() {
        return AlertConfig.builder()
                .enabled(true)
                .warningThreshold(75.0)
                .criticalThreshold(50.0)
                .evaluationWindowMinutes(30)
                .cooldownMinutes(15)
                .build();
    }

    private boolean shouldRenotify(Alert existingAlert, AlertConfig config) {
        if (existingAlert.getLastNotificationSent() == null) {
            return true;
        }

        long minutesSinceNotification = java.time.Duration.between(
                existingAlert.getLastNotificationSent(),
                LocalDateTime.now()
        ).toMinutes();

        return minutesSinceNotification >= config.getCooldownMinutes();
    }

    private void saveAndNotify(Alert alert, AlertConfig config) {
        Alert saved = alertRepository.save(alert);
        logger.info("Created alert: {} - {}", saved.getType(), saved.getTitle());

        // send notifications
        notificationService.sendAlert(saved, config);

        // update notification tracking
        saved.setLastNotificationSent(LocalDateTime.now());
        saved.setNotificationCount(saved.getNotificationCount() + 1);
        alertRepository.save(saved);
    }

    private String determineRecommendation(Alert.AlertType type, PipelineMetrics metrics) {
        switch (type) {
            case SUCCESS_RATE_DROP:
                if (metrics.getMostCommonFailureTime() != null) {
                    return String.format(
                            "Failures commonly occur around %s. Check: recent code changes, " +
                                    "external dependencies, resource availability at peak times",
                            metrics.getMostCommonFailureTime()
                    );
                }
                return "Review recent commits, check test logs, verify environment configuration";

            case DURATION_INCREASE:
                return "Check for: new dependencies, increased test coverage, " +
                        "resource constraints, parallel job conflicts";

            default:
                return "Investigate recent changes and check system logs";
        }
    }

    // public methods for controller
    public List<Alert> getActiveAlerts() {
        return alertRepository.findByStatusOrderByCreatedAtDesc(Alert.AlertStatus.ACTIVE);
    }

    public List<Alert> getAlertsForPipeline(Integer pipelineId) {
        return alertRepository.findByPipelineIdAndStatusOrderByCreatedAtDesc(
                pipelineId, Alert.AlertStatus.ACTIVE
        );
    }

    public Optional<Alert> acknowledgeAlert(Long alertId, String acknowledgedBy) {
        Optional<Alert> alertOpt = alertRepository.findById(alertId);
        if (alertOpt.isPresent()) {
            Alert alert = alertOpt.get();
            alert.setStatus(Alert.AlertStatus.ACKNOWLEDGED);
            alert.setAcknowledgedAt(LocalDateTime.now());
            alert.setNotes("Acknowledged by " + acknowledgedBy);
            return Optional.of(alertRepository.save(alert));
        }
        return Optional.empty();
    }

    public Optional<Alert> resolveAlert(Long alertId, String resolvedBy, String notes) {
        Optional<Alert> alertOpt = alertRepository.findById(alertId);
        if (alertOpt.isPresent()) {
            Alert alert = alertOpt.get();
            alert.setStatus(Alert.AlertStatus.RESOLVED);
            alert.setResolvedAt(LocalDateTime.now());
            alert.setResolvedBy(resolvedBy);
            alert.setNotes(notes);
            return Optional.of(alertRepository.save(alert));
        }
        return Optional.empty();
    }
}