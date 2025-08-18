package com.yourname.streamci.streamci.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "queue_tracker")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueTracker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pipeline_id")
    private Pipeline pipeline;

    private String buildId; // github run id or build id
    private String status; // "queued", "running", "completed"

    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private Long waitTimeSeconds; // time in queue
    private Long runTimeSeconds;   // time running

    @PrePersist
    protected void onCreate() {
        if (queuedAt == null) {
            queuedAt = LocalDateTime.now();
        }
    }
}