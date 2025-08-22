package com.yourname.streamci.streamci.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatternDetectionResult {

    private String patternType;
    private String description;
    private Double confidence;
    private Integer frequency;
    private String recommendation;
    private LocalDateTime lastOccurrence;
    private Map<String, Object> details;
}

