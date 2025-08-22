package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.event.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardWebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardWebSocketService.class);
    private final SimpMessagingTemplate messagingTemplate;

    public DashboardWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }


    // send live dashboard updates to all connected clients

    public void broadcastDashboardUpdate(String type, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("timestamp", LocalDateTime.now());
        message.put("data", data);

        logger.debug("Broadcasting dashboard update: {}", type);
        messagingTemplate.convertAndSend("/topic/dashboard", message);
    }


     // send pipeline-specific updates

    public void broadcastPipelineUpdate(Integer pipelineId, String type, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("pipeline_id", pipelineId);
        message.put("timestamp", LocalDateTime.now());
        message.put("data", data);

        logger.debug("Broadcasting pipeline {} update: {}", pipelineId, type);
        messagingTemplate.convertAndSend("/topic/pipeline/" + pipelineId, message);
    }


    // listen for build completion events and broadcast updates

    @EventListener
    public void handleBuildCompleted(BuildCompletedEvent event) {
        logger.info("Build completed: {} for pipeline {}",
                event.getBuildId(), event.getPipelineId());

        // Create build completion data
        Map<String, Object> buildData = new HashMap<>();
        buildData.put("build_id", event.getBuildId());
        buildData.put("pipeline_id", event.getPipelineId());
        buildData.put("status", event.getStatus());
        buildData.put("duration", event.getDuration());
        buildData.put("completed_at", event.getCompletedAt());

        // Broadcast to dashboard
        broadcastDashboardUpdate("build_completed", buildData);

        // Broadcast to specific pipeline
        broadcastPipelineUpdate(event.getPipelineId(), "build_completed", buildData);
    }


    // listen for queue status changes

    @EventListener
    public void handleQueueStatusChanged(QueueStatusChangedEvent event) {
        logger.info("Queue status changed for pipeline {}", event.getPipelineId());

        Map<String, Object> queueData = new HashMap<>();
        queueData.put("pipeline_id", event.getPipelineId());
        queueData.put("queued_builds", event.getQueuedCount());
        queueData.put("running_builds", event.getRunningCount());

        broadcastDashboardUpdate("queue_status_changed", queueData);
        broadcastPipelineUpdate(event.getPipelineId(), "queue_status_changed", queueData);
    }


    // listen for metrics updates

    @EventListener
    public void handleMetricsCalculated(MetricsCalculatedEvent event) {
        logger.info("Metrics calculated for pipeline {}", event.getPipeline().getId());

        Map<String, Object> metricsData = new HashMap<>();
        metricsData.put("pipeline_id", event.getPipeline().getId());
        metricsData.put("success_rate", event.getMetrics().getSuccessRate());
        metricsData.put("total_builds", event.getMetrics().getTotalBuilds());
        metricsData.put("calculated_at", event.getMetrics().getCalculatedAt());

        broadcastDashboardUpdate("metrics_updated", metricsData);
        broadcastPipelineUpdate(event.getPipeline().getId(), "metrics_updated", metricsData);
    }


     // listen for new alerts

    @EventListener
    public void handleAlertCreated(AlertCreatedEvent event) {
        Integer pipelineId = event.getAlert().getPipeline() != null ?
                event.getAlert().getPipeline().getId() : null;

        logger.info("New alert created: {} for pipeline {}",
                event.getAlert().getType(), pipelineId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alert_id", event.getAlert().getId());
        alertData.put("pipeline_id", pipelineId);
        alertData.put("type", event.getAlert().getType().toString());
        alertData.put("severity", event.getAlert().getSeverity().toString());
        alertData.put("message", event.getAlert().getMessage());
        alertData.put("created_at", event.getAlert().getCreatedAt());

        broadcastDashboardUpdate("alert_created", alertData);

        if (pipelineId != null) {
            broadcastPipelineUpdate(pipelineId, "alert_created", alertData);
        }
    }


    //  Manual method to trigger dashboard refresh

    public void triggerDashboardRefresh() {
        Map<String, Object> refreshData = new HashMap<>();
        refreshData.put("reason", "manual_refresh");

        broadcastDashboardUpdate("dashboard_refresh", refreshData);
        logger.info("Dashboard refresh triggered");
    }


    // Send system status updates

    public void broadcastSystemStatus(String status, String message) {
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("status", status);
        statusData.put("message", message);

        broadcastDashboardUpdate("system_status", statusData);
    }
}