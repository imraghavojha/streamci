package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.Alert;
import com.yourname.streamci.streamci.model.AlertConfig;
import com.yourname.streamci.streamci.service.AlertService;
import com.yourname.streamci.streamci.repository.AlertRepository;
import com.yourname.streamci.streamci.repository.AlertConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final AlertRepository alertRepository;
    private final AlertConfigRepository configRepository;

    public AlertController(AlertService alertService,
                           AlertRepository alertRepository,
                           AlertConfigRepository configRepository) {
        this.alertService = alertService;
        this.alertRepository = alertRepository;
        this.configRepository = configRepository;
    }

    // get all active alerts
    @GetMapping
    public ResponseEntity<List<Alert>> getActiveAlerts(
            @RequestParam(required = false) String status) {

        if (status != null) {
            try {
                Alert.AlertStatus alertStatus = Alert.AlertStatus.valueOf(status.toUpperCase());
                return ResponseEntity.ok(
                        alertRepository.findByStatusOrderByCreatedAtDesc(alertStatus)
                );
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    // get alerts for specific pipeline
    @GetMapping("/pipeline/{pipelineId}")
    public ResponseEntity<List<Alert>> getPipelineAlerts(@PathVariable Integer pipelineId) {
        return ResponseEntity.ok(alertService.getAlertsForPipeline(pipelineId));
    }

    // acknowledge an alert
    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<?> acknowledgeAlert(@PathVariable Long alertId) {
        return alertService.acknowledgeAlert(alertId, "user")
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // resolve an alert
    @PostMapping("/{alertId}/resolve")
    public ResponseEntity<?> resolveAlert(
            @PathVariable Long alertId,
            @RequestBody Map<String, String> body) {

        String notes = body.getOrDefault("notes", "Manually resolved");
        return alertService.resolveAlert(alertId, "user", notes)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // get alert statistics
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAlertStats() {
        Map<String, Object> stats = new HashMap<>();

        List<Alert> activeAlerts = alertService.getActiveAlerts();

        // count by severity
        Map<String, Long> bySeverity = new HashMap<>();
        for (Alert.AlertSeverity severity : Alert.AlertSeverity.values()) {
            long count = activeAlerts.stream()
                    .filter(a -> a.getSeverity() == severity)
                    .count();
            bySeverity.put(severity.toString(), count);
        }

        // count by type
        Map<String, Long> byType = new HashMap<>();
        for (Alert.AlertType type : Alert.AlertType.values()) {
            long count = activeAlerts.stream()
                    .filter(a -> a.getType() == type)
                    .count();
            byType.put(type.toString(), count);
        }

        stats.put("total_active", activeAlerts.size());
        stats.put("by_severity", bySeverity);
        stats.put("by_type", byType);
        stats.put("critical_count", activeAlerts.stream()
                .filter(a -> a.getSeverity() == Alert.AlertSeverity.CRITICAL ||
                        a.getSeverity() == Alert.AlertSeverity.EMERGENCY)
                .count());

        return ResponseEntity.ok(stats);
    }

    // get alert configuration
    @GetMapping("/config")
    public ResponseEntity<List<AlertConfig>> getAlertConfigs() {
        return ResponseEntity.ok(configRepository.findAll());
    }

    // update alert configuration
    @PostMapping("/config")
    public ResponseEntity<AlertConfig> createOrUpdateConfig(@RequestBody AlertConfig config) {
        AlertConfig saved = configRepository.save(config);
        return ResponseEntity.ok(saved);
    }
}