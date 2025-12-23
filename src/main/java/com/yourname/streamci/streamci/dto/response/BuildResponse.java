package com.yourname.streamci.streamci.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * build response dto - matches the json structure that build entity serializes to
 * (pipeline field is excluded due to @JsonBackReference on entity)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildResponse {
    private Long buildId;
    private Integer pipelineId;  // included for convenience instead of full pipeline object
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private String commitHash;
    private String committer;
    private String branch;
}
