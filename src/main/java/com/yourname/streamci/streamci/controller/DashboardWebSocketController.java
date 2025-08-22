package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.service.DashboardWebSocketService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
public class DashboardWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardWebSocketController.class);
    private final DashboardWebSocketService webSocketService;

    public DashboardWebSocketController(DashboardWebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    /**
     * handle client connection and subscription
     */
    @MessageMapping("/dashboard/subscribe")
    @SendTo("/topic/dashboard")
    public Map<String, Object> handleDashboardSubscription() {
        logger.info("Client subscribed to dashboard updates");

        Map<String, Object> response = new HashMap<>();
        response.put("type", "subscription_confirmed");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "Successfully subscribed to dashboard updates");

        return response;
    }

    /**
     * handle ping/pong for connection health
     */
    @MessageMapping("/dashboard/ping")
    @SendTo("/topic/dashboard")
    public Map<String, Object> handlePing() {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "pong");
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    /**
     * trigger manual dashboard refresh via HTTP
     */
    @GetMapping("/api/dashboard/refresh")
    @ResponseBody
    public Map<String, Object> triggerRefresh() {
        webSocketService.triggerDashboardRefresh();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Dashboard refresh triggered");
        response.put("timestamp", LocalDateTime.now());

        return response;
    }
}