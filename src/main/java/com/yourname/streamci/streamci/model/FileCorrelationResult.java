package com.yourname.streamci.streamci.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileCorrelationResult {

    private String filePattern;          // e.g. "*.yml", "package.json"
    private Double failureRate;          // percentage of failures when this file type changes
    private String description;          // human readable explanation
    private String recommendation;       // what to do about it
    private Integer affectedBuilds;      // how many builds were affected
    private Double confidence;           // how confident we are (0.0 to 1.0)
}