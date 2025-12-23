package com.yourname.streamci.streamci.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * request dto for creating a new pipeline
 * includes validation annotations to ensure data quality
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePipelineRequest {

    @NotBlank(message = "pipeline name is required")
    private String name;

    private String status;
    private Integer duration;
}
