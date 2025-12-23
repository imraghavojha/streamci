package com.yourname.streamci.streamci.dto;

/**
 * standard response for webhook endpoints
 */
public class WebhookResponse {
    private String status;
    private String message;

    public WebhookResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public static WebhookResponse accepted() {
        return new WebhookResponse("accepted", "event received");
    }

    public static WebhookResponse error(String message) {
        return new WebhookResponse("error", message);
    }

    // getters and setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
