package com.yourname.streamci.streamci.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuccessPrediction {
    private Double probability;
    private String confidence;
    private String reasoning;
    private Map<String, Double> factors;
}
