package com.yourname.streamci.streamci.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "failure_patterns")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailurePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pipeline_id")
    private Pipeline pipeline;

    private String patternType;
    private String description;
    private Double confidence;
    private Integer frequency;
    private String recommendation;
    private String details;

    private LocalDateTime detectedAt;
    private LocalDateTime lastOccurrence;

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
    }
}