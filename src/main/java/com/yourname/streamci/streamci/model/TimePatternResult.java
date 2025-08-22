package com.yourname.streamci.streamci.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimePatternResult {
    private String timeSlot;
    private Double failureRate;
    private Integer totalBuilds;
    private Integer failures;
    private String riskLevel;
}
