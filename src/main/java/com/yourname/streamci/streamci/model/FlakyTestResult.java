package com.yourname.streamci.streamci.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlakyTestResult {
    private String testIdentifier;
    private Double failureRate;
    private Integer totalRuns;
    private Integer failures;
    private String recommendation;
}
