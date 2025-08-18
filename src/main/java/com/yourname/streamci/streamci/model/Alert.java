package com.yourname.streamci.streamci.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pipeline_id")
    private Pipeline pipeline;

    @Enumerated(EnumType.STRING)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    private AlertStatus status;

    private String title;

    @Column(length = 1000)
    private String message;

    @Column(length = 500)
    private String recommendation;

    // threshold that was violated
    private Double thresholdValue;
    private Double actualValue;
    private String metric;

    // timing
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime escalatedAt;

    // prevent duplicate alerts
    private String fingerprint; // unique identifier for this alert type
    private LocalDateTime lastNotificationSent;
    private Integer notificationCount;

    // context
    private String triggeredBy; // "scheduler", "webhook", "manual"
    private String resolvedBy;  // username or "auto"
    private String notes;

    public enum AlertType {
        SUCCESS_RATE_DROP,
        DURATION_INCREASE,
        CONSECUTIVE_FAILURES,
        QUEUE_BACKUP,
        HIGH_FAILURE_RATE,
        STALE_PIPELINE,
        PERFORMANCE_DEGRADATION,
        UNUSUAL_ACTIVITY
    }

    public enum AlertSeverity {
        INFO,      // FYI, no action needed
        WARNING,   // should investigate
        CRITICAL,  // immediate action needed
        EMERGENCY  // everything is on fire
    }

    public enum AlertStatus {
        ACTIVE,       // alert is current
        ACKNOWLEDGED, // someone saw it
        RESOLVED,     // problem fixed
        ESCALATED,    // sent to higher level
        SUPPRESSED    // muted temporarily
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = AlertStatus.ACTIVE;
        }
        if (notificationCount == null) {
            notificationCount = 0;
        }
    }
}