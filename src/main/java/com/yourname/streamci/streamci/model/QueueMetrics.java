package com.yourname.streamci.streamci.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "queue_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pipeline_id")
    private Pipeline pipeline;

    private LocalDateTime timestamp;

    // current state
    private Integer currentQueueDepth;
    private Integer runningBuilds;
    private Integer waitingBuilds;

    // averages for this time window
    private Double avgWaitTimeSeconds;
    private Double avgProcessingTimeSeconds;

    // predictions
    private Integer predictedQueueDepth30Min;
    private LocalDateTime predictedPeakTime;
    private Integer predictedPeakDepth;
    private String bottleneckReason;

    // trend info
    private String trend; // "increasing", "decreasing", "stable"
    private Double trendSlope; // rate of change

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}