package com.yourname.streamci.streamci.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pipeline_id")
    private Pipeline pipeline;

    private LocalDateTime calculatedAt;

    // core metrics
    private Integer totalBuilds;
    private Integer successfulBuilds;
    private Integer failedBuilds;
    private Double successRate;

    // duration metrics
    private Long avgDurationSeconds;
    private Long minDurationSeconds;
    private Long maxDurationSeconds;

    // time patterns
    private String peakHour; // e.g., "14" for 2 PM
    private String peakDay;  // e.g., "MONDAY"
    private Integer buildsToday;
    private Integer buildsThisWeek;

    // failure analysis
    private String mostCommonFailureTime;
    private Integer consecutiveFailures;
    private LocalDateTime lastSuccess;
    private LocalDateTime lastFailure;

    // trend data (compared to previous period)
    private Double successRateChange; // percentage change
    private Long avgDurationChange;    // seconds change
}