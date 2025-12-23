package com.yourname.streamci.streamci.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * pipeline response dto - matches the json structure that pipeline entity serializes to
 * includes builds array (due to @JsonManagedReference on entity)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineResponse {
    private Integer id;
    private String name;
    private String status;
    private Integer duration;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<BuildResponse> builds;  // included in response
}
