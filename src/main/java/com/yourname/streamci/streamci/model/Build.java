package com.yourname.streamci.streamci.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Duration;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Entity
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
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


    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;

    private String commitHash;
    private String committer;
    private String branch;

}
