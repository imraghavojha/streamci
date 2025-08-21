package com.yourname.streamci.streamci.integration;

import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.repository.PipelineRepository;
import com.yourname.streamci.streamci.repository.BuildRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simple webhook integration test to get started.
 * Tests basic webhook processing without all the complexity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SimpleWebhookTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        buildRepository.deleteAll();
        pipelineRepository.deleteAll();

        // Wait a moment to ensure cleanup is complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testBasicSuccessfulWebhook() throws Exception {
        // Load the success workflow JSON
        String payload = loadJsonFixture("workflow_run_success.json");

        // Send the webhook
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Wait for async processing to complete
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Check that pipeline was created
                    List<Pipeline> pipelines = pipelineRepository.findAll();
                    assertThat(pipelines).hasSize(1);

                    Pipeline pipeline = pipelines.get(0);
                    assertThat(pipeline.getName()).isEqualTo("Hello-World");
                    assertThat(pipeline.getStatus()).isEqualTo("success");

                    // Check that build was created
                    List<Build> builds = buildRepository.findAll();
                    assertThat(builds).hasSize(1);

                    Build build = builds.get(0);
                    assertThat(build.getStatus()).isEqualTo("success");
                    assertThat(build.getCommitHash()).isNotBlank();
                    assertThat(build.getCommitter()).isNotBlank();
                });
    }

    @Test
    void testBasicFailureWebhook() throws Exception {
        String payload = loadJsonFixture("workflow_run_failure.json");

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<Build> builds = buildRepository.findAll();
                    assertThat(builds).hasSize(1);
                    assertThat(builds.get(0).getStatus()).isEqualTo("failure");
                });
    }

    @Test
    void testIgnoresNonWorkflowEvents() throws Exception {
        // Send a non-workflow_run event
        String payload = """
            {
                "action": "opened",
                "pull_request": {
                    "number": 123
                }
            }
            """;

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Give it a moment to process
        Thread.sleep(1000);

        // Should not create anything
        assertThat(pipelineRepository.findAll()).isEmpty();
        assertThat(buildRepository.findAll()).isEmpty();
    }

    private String loadJsonFixture(String filename) throws Exception {
        ClassPathResource resource = new ClassPathResource("fixtures/" + filename);
        return Files.readString(resource.getFile().toPath());
    }
}