package com.yourname.streamci.streamci.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncResult {
    private boolean success;
    private String message;
    private int pipelinesSynced;
    private int buildsSynced;
}