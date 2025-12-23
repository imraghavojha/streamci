package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.dto.WebhookResponse;
import com.yourname.streamci.streamci.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * unit tests for webhook controller
 * tests dto usage and response handling
 */
class WebhookControllerTest {

    @Mock
    private WebhookService webhookService;

    private WebhookController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new WebhookController(webhookService);
    }

    @Test
    void testHandleGitHubWebhook_ValidSignature_ReturnsAccepted() {
        // arrange
        when(webhookService.verifySignature(anyString(), anyString())).thenReturn(true);

        // act
        ResponseEntity<WebhookResponse> response = controller.handleGitHubWebhookLegacy(
                "workflow_run",
                "sha256=test",
                "{\"action\":\"completed\"}"
        );

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("accepted");
        assertThat(response.getBody().getMessage()).isEqualTo("event received");

        verify(webhookService).verifySignature(anyString(), anyString());
        verify(webhookService).processWebhookAsync(eq("workflow_run"), anyString());
    }

    @Test
    void testHandleGitHubWebhook_InvalidSignature_ReturnsUnauthorized() {
        // arrange
        when(webhookService.verifySignature(anyString(), anyString())).thenReturn(false);

        // act
        ResponseEntity<WebhookResponse> response = controller.handleGitHubWebhookLegacy(
                "workflow_run",
                "sha256=wrong",
                "{\"action\":\"completed\"}"
        );

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("error");
        assertThat(response.getBody().getMessage()).isEqualTo("invalid signature");

        verify(webhookService).verifySignature(anyString(), anyString());
        verify(webhookService, never()).processWebhookAsync(anyString(), anyString());
    }

    @Test
    void testHandleGitHubWebhook_UserSpecific_ValidSignature() {
        // arrange
        when(webhookService.verifySignatureForUser(anyString(), anyString(), eq("user123")))
                .thenReturn(true);

        // act
        ResponseEntity<WebhookResponse> response = controller.handleGitHubWebhook(
                "user123",
                "workflow_run",
                "sha256=test",
                "{\"action\":\"completed\"}"
        );

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("accepted");

        verify(webhookService).verifySignatureForUser(anyString(), anyString(), eq("user123"));
        verify(webhookService).processWebhookAsync(eq("workflow_run"), anyString());
    }

    @Test
    void testHandleGitHubWebhook_UserSpecific_InvalidSignature() {
        // arrange
        when(webhookService.verifySignatureForUser(anyString(), anyString(), eq("user123")))
                .thenReturn(false);

        // act
        ResponseEntity<WebhookResponse> response = controller.handleGitHubWebhook(
                "user123",
                "workflow_run",
                "sha256=wrong",
                "{\"action\":\"completed\"}"
        );

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("error");

        verify(webhookService).verifySignatureForUser(anyString(), anyString(), eq("user123"));
        verify(webhookService, never()).processWebhookAsync(anyString(), anyString());
    }
}
