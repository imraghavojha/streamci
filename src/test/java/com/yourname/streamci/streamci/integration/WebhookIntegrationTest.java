package com.yourname.streamci.streamci.integration;

import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.model.QueueTracker;
import com.yourname.streamci.streamci.repository.BuildRepository;
import com.yourname.streamci.streamci.repository.PipelineRepository;
import com.yourname.streamci.streamci.repository.QueueTrackerRepository;
import com.yourname.streamci.streamci.util.WebhookTestHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * comprehensive webhook integration tests
 * consolidated from multiple test files with proper signature handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebhookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    private QueueTrackerRepository queueTrackerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    @Order(1)
    void testQueuedWorkflow_CreatesQueueTracker() throws Exception {
        // arrange
        String payload = loadJsonFixture("workflow_run_queued.json");
        String signature = WebhookTestHelper.generateSignature(payload, WebhookTestHelper.TEST_SECRET);

        // act
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        // assert - wait for async processing
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<Pipeline> pipelines = pipelineRepository.findAll();
                    List<QueueTracker> queueEntries = queueTrackerRepository.findAll();

                    assertThat(pipelines).hasSize(1);
                    assertThat(queueEntries).hasSize(1);

                    Pipeline pipeline = pipelines.get(0);
                    assertThat(pipeline.getName()).isEqualTo("Hello-World");

                    QueueTracker queueEntry = queueEntries.get(0);
                    assertThat(queueEntry.getBuildId()).isEqualTo("123456789");
                    assertThat(queueEntry.getStatus()).isEqualTo("queued");
                });
    }

    @Test
    @Order(2)
    void testSuccessfulWorkflow_CreatesBuildAndPipeline() throws Exception {
        // arrange
        String payload = loadJsonFixture("workflow_run_success.json");
        String signature = WebhookTestHelper.generateSignature(payload, WebhookTestHelper.TEST_SECRET);

        // act
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // assert
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<Pipeline> pipelines = pipelineRepository.findAll();
                    List<Build> builds = buildRepository.findAll();

                    assertThat(pipelines).hasSize(1);
                    assertThat(builds).hasSize(1);

                    Build build = builds.get(0);
                    assertThat(build.getStatus()).isEqualTo("success");
                    assertThat(build.getCommitHash()).isEqualTo("789abc123def456012345678901234567890abcd");
                    assertThat(build.getCommitter()).isEqualTo("octocat");
                });
    }

    @Test
    @Order(3)
    void testFailedWorkflow_CreatesBuildWithFailureStatus() throws Exception {
        // arrange
        String payload = loadJsonFixture("workflow_run_failure.json");
        String signature = WebhookTestHelper.generateSignature(payload, WebhookTestHelper.TEST_SECRET);

        // act
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // assert
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<Build> builds = buildRepository.findAll();

                    assertThat(builds).hasSize(1);

                    Build build = builds.get(0);
                    assertThat(build.getStatus()).isEqualTo("failure");
                    assertThat(build.getCommitHash()).isEqualTo("fed987cba654321098765432109876543210abcd");
                    assertThat(build.getBranch()).isEqualTo("bugfix/critical-fix");
                    assertThat(build.getCommitter()).isEqualTo("developer-user");
                });
    }

    @Test
    @Order(4)
    void testInvalidSignature_ReturnsUnauthorized() throws Exception {
        // arrange
        String payload = loadJsonFixture("workflow_run_success.json");
        String invalidSignature = "sha256=invalid";

        // act & assert
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", invalidSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("invalid signature"));

        // verify nothing was created
        assertThat(pipelineRepository.findAll()).isEmpty();
        assertThat(buildRepository.findAll()).isEmpty();
    }

    @Test
    @Order(5)
    void testMissingSignature_ReturnsUnauthorized() throws Exception {
        // arrange
        String payload = loadJsonFixture("workflow_run_success.json");

        // act & assert - no signature header
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void testNonWorkflowEvent_IgnoresEvent() throws Exception {
        // arrange
        String payload = """
            {
                "action": "opened",
                "pull_request": {
                    "number": 123
                }
            }
            """;
        String signature = WebhookTestHelper.generateSignature(payload, WebhookTestHelper.TEST_SECRET);

        // act
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // wait a bit to ensure async processing completes
        Thread.sleep(2000);

        // assert - should not create anything
        assertThat(pipelineRepository.findAll()).isEmpty();
        assertThat(buildRepository.findAll()).isEmpty();
        assertThat(queueTrackerRepository.findAll()).isEmpty();
    }

    // helper methods

    private void cleanDatabase() {
        try {
            // use jdbc to bypass constraints
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("DELETE FROM queue_tracker");
            jdbcTemplate.execute("DELETE FROM build");
            jdbcTemplate.execute("DELETE FROM pipeline_metrics");
            jdbcTemplate.execute("DELETE FROM alerts");
            jdbcTemplate.execute("DELETE FROM alert_configs");
            jdbcTemplate.execute("DELETE FROM queue_metrics");
            jdbcTemplate.execute("DELETE FROM pipeline");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (Exception e) {
            // fallback to repository deletion
            try {
                queueTrackerRepository.deleteAll();
                buildRepository.deleteAll();
                pipelineRepository.deleteAll();
            } catch (Exception ignored) {
            }
        }
    }

    private String loadJsonFixture(String filename) throws Exception {
        ClassPathResource resource = new ClassPathResource("fixtures/" + filename);
        return Files.readString(resource.getFile().toPath());
    }
}
