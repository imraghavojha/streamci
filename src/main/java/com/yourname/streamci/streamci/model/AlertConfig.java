package com.yourname.streamci.streamci.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "alert_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pipeline_id", nullable = true)
    private Pipeline pipeline; // null means global config

    @Enumerated(EnumType.STRING)
    private Alert.AlertType alertType;

    private Boolean enabled;

    // thresholds
    private Double warningThreshold;
    private Double criticalThreshold;

    // time windows
    private Integer evaluationWindowMinutes; // look back this many minutes
    private Integer cooldownMinutes; // don't alert again for this many minutes

    // smart thresholds
    private Boolean useAdaptiveThreshold; // adjust based on historical patterns
    private Double adaptiveMultiplier; // e.g., 1.5x the normal average

    // notification settings
    private Boolean notifyEmail;
    private Boolean notifySlack;
    private Boolean notifyWebhook;
    private String notificationEndpoint;

    @PrePersist
    protected void setDefaults() {
        if (enabled == null) enabled = true;
        if (evaluationWindowMinutes == null) evaluationWindowMinutes = 30;
        if (cooldownMinutes == null) cooldownMinutes = 15;
        if (useAdaptiveThreshold == null) useAdaptiveThreshold = false;
        if (adaptiveMultiplier == null) adaptiveMultiplier = 1.5;
        if (notifyEmail == null) notifyEmail = false;
        if (notifySlack == null) notifySlack = false;
        if (notifyWebhook == null) notifyWebhook = false;
    }
}