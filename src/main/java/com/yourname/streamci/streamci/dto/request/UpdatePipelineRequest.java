package com.yourname.streamci.streamci.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * request dto for updating an existing pipeline
 * all fields are optional - only provided fields will be updated
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePipelineRequest {
    private String name;
    private String status;
    private Integer duration;
}
