package com.yourname.streamci.streamci.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Build {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long buildId;

    @ManyToOne
    @JoinColumn(name = "pipeline_id")
    @JsonBackReference
    private Pipeline pipeline;

    private String status;


    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;

    private String commitHash;
    private String committer;
    private String branch;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            if (startTime != null) {
                createdAt = startTime;
            } else if (endTime != null) {
                createdAt = endTime;
            } else {
                createdAt = LocalDateTime.now();
            }
        }
    }

}
