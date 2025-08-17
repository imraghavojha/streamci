package com.yourname.streamci.streamci.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunsResponse {
    @JsonProperty("workflow_runs")
    private List<WorkflowRun> workflowRuns;
}