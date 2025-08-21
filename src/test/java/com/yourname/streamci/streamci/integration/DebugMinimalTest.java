package com.yourname.streamci.streamci.integration;

import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.model.QueueTracker;
import com.yourname.streamci.streamci.repository.PipelineRepository;
import com.yourname.streamci.streamci.repository.BuildRepository;
import com.yourname.streamci.streamci.repository.QueueTrackerRepository;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fixed version - no DirtiesContext, better async handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DebugMinimalTest {

    private static final Logger logger = LoggerFactory.getLogger(DebugMinimalTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    private QueueTrackerRepository queueTrackerRepository;

    @BeforeEach
    void setUp() {
        logger.info("=== SETUP: Cleaning database ===");
        // More aggressive cleanup
        try {
            queueTrackerRepository.deleteAll();
            queueTrackerRepository.flush();
            buildRepository.deleteAll();
            buildRepository.flush();
            pipelineRepository.deleteAll();
            pipelineRepository.flush();
        } catch (Exception e) {
            logger.warn("Cleanup error (expected): {}", e.getMessage());
        }

        // Wait for cleanup with shorter timeout
        await().atMost(3, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        boolean isEmpty = queueTrackerRepository.findAll().isEmpty() &&
                                buildRepository.findAll().isEmpty() &&
                                pipelineRepository.findAll().isEmpty();
                        logger.info("Database clean: {}", isEmpty);
                        return isEmpty;
                    } catch (Exception e) {
                        logger.warn("Database check error: {}", e.getMessage());
                        return false;
                    }
                });
        logger.info("=== SETUP COMPLETE ===");
    }

    @Test
    @Order(1)
    void testQueuedWorkflow() throws Exception {
        logger.info("=== TEST 1: Queued Workflow ===");

        String payload = loadJsonFixture("workflow_run_queued.json");

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Wait for async with progress logging
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<Pipeline> pipelines = pipelineRepository.findAll();
                    List<QueueTracker> queueEntries = queueTrackerRepository.findAll();

                    logger.info("Current state: Pipelines: {}, Queue: {}",
                            pipelines.size(), queueEntries.size());

                    assertThat(pipelines).hasSize(1);
                    assertThat(queueEntries).hasSize(1);

                    QueueTracker queueEntry = queueEntries.get(0);
                    assertThat(queueEntry.getBuildId()).isEqualTo("123456789");
                    assertThat(queueEntry.getStatus()).isEqualTo("queued");

                    logger.info("=== TEST 1 PASSED ===");
                });
    }

    @Test
    @Order(2)
    void testCompletedSuccessWorkflow() throws Exception {
        logger.info("=== TEST 2: Success Workflow ===");

        String payload = loadJsonFixture("workflow_run_success.json");

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Wait for async with detailed logging
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<Pipeline> pipelines = pipelineRepository.findAll();
                    List<Build> builds = buildRepository.findAll();
                    List<QueueTracker> queueEntries = queueTrackerRepository.findAll();

                    logger.info("Current state: Pipelines: {}, Builds: {}, Queue: {}",
                            pipelines.size(), builds.size(), queueEntries.size());

                    // Log details if we have entities
                    if (!builds.isEmpty()) {
                        builds.forEach(b -> logger.info("Build found: status={}, commit={}",
                                b.getStatus(), b.getCommitHash()));
                    }

                    assertThat(pipelines).hasSize(1);
                    assertThat(builds).hasSize(1);

                    Build build = builds.get(0);
                    assertThat(build.getStatus()).isEqualTo("success");
                    assertThat(build.getCommitHash()).isEqualTo("789abc123def456012345678901234567890abcd");

                    logger.info("=== TEST 2 PASSED ===");
                });
    }

    private String loadJsonFixture(String filename) throws Exception {
        ClassPathResource resource = new ClassPathResource("fixtures/" + filename);
        String content = Files.readString(resource.getFile().toPath());
        logger.info("Loaded fixture: {} ({} chars)", filename, content.length());
        return content;
    }
}