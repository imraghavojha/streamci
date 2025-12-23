package com.yourname.streamci.streamci.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * request dto for creating a new build
 * includes validation annotations to ensure data quality
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBuildRequest {

    @NotNull(message = "pipeline id is required")
    private Integer pipelineId;

    @NotBlank(message = "build status is required")
    private String status;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private String commitHash;
    private String committer;
    private String branch;
}
