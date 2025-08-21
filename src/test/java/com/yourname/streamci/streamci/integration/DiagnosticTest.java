package com.yourname.streamci.streamci.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.model.QueueTracker;
import com.yourname.streamci.streamci.repository.PipelineRepository;
import com.yourname.streamci.streamci.repository.BuildRepository;
import com.yourname.streamci.streamci.repository.QueueTrackerRepository;
import com.yourname.streamci.streamci.service.WebhookService;
import com.yourname.streamci.streamci.service.PipelineService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DIAGNOSTIC TEST - Find out exactly what's broken
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DiagnosticTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    private QueueTrackerRepository queueTrackerRepository;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    void step1_TestDirectServiceCall() {
        System.out.println("\n=== STEP 1: TEST DIRECT SERVICE CALLS ===");

        // Test 1.1: Can we create a pipeline directly?
        try {
            Pipeline testPipeline = Pipeline.builder()
                    .name("Test-Pipeline")
                    .status("active")
                    .duration(100)
                    .build();

            Pipeline saved = pipelineService.savePipeline(testPipeline);
            System.out.println("✅ Direct pipeline creation works: " + saved.getId());

            List<Pipeline> all = pipelineRepository.findAll();
            System.out.println("✅ Database contains: " + all.size() + " pipelines");

        } catch (Exception e) {
            System.out.println("❌ Direct pipeline creation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void step2_TestWebhookServiceDirectly() throws Exception {
        System.out.println("\n=== STEP 2: TEST WEBHOOK SERVICE DIRECTLY ===");

        String payload = loadJsonFixture("workflow_run_success.json");
        System.out.println("✅ Loaded JSON payload: " + payload.length() + " characters");

        try {
            // Call webhook service directly (not through HTTP)
            webhookService.processWebhookAsync("workflow_run", payload);
            System.out.println("✅ Webhook service called without errors");

            // Wait a bit
            Thread.sleep(3000);

            // Check what was created
            List<Pipeline> pipelines = pipelineRepository.findAll();
            List<Build> builds = buildRepository.findAll();
            List<QueueTracker> queue = queueTrackerRepository.findAll();

            System.out.println("📊 After direct service call:");
            System.out.println("   Pipelines: " + pipelines.size());
            System.out.println("   Builds: " + builds.size());
            System.out.println("   Queue entries: " + queue.size());

            if (!pipelines.isEmpty()) {
                System.out.println("✅ Pipeline created: " + pipelines.get(0));
            }
            if (!builds.isEmpty()) {
                System.out.println("✅ Build created: " + builds.get(0));
            }

        } catch (Exception e) {
            System.out.println("❌ Webhook service failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void step3_TestHttpEndpoint() throws Exception {
        System.out.println("\n=== STEP 3: TEST HTTP ENDPOINT ===");

        String payload = loadJsonFixture("workflow_run_success.json");

        // Send HTTP request
        var result = mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "test-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        System.out.println("✅ HTTP request successful");
        System.out.println("📨 Response: " + result.getResponse().getContentAsString());

        // Wait and check multiple times
        for (int i = 1; i <= 5; i++) {
            Thread.sleep(2000);

            List<Pipeline> pipelines = pipelineRepository.findAll();
            List<Build> builds = buildRepository.findAll();
            List<QueueTracker> queue = queueTrackerRepository.findAll();

            System.out.println("📊 Check #" + i + " (after " + (i*2) + "s):");
            System.out.println("   Pipelines: " + pipelines.size());
            System.out.println("   Builds: " + builds.size());
            System.out.println("   Queue: " + queue.size());

            if (!pipelines.isEmpty() || !builds.isEmpty() || !queue.isEmpty()) {
                System.out.println("🎉 SUCCESS! Entities created via HTTP");
                break;
            }
        }
    }

    @Test
    void step4_CheckAsyncConfiguration() {
        System.out.println("\n=== STEP 4: CHECK ASYNC CONFIGURATION ===");

        // Check if @EnableAsync is working
        try {
            // This is a hacky way to check if async is enabled
            System.out.println("Spring context active: " + (webhookService != null));
            System.out.println("Webhook service class: " + webhookService.getClass().getName());

            // Check if it's a proxy (indicates async is enabled)
            if (webhookService.getClass().getName().contains("$")) {
                System.out.println("✅ Webhook service appears to be proxied (async likely enabled)");
            } else {
                System.out.println("⚠️ Webhook service NOT proxied (async might be disabled)");
            }

        } catch (Exception e) {
            System.out.println("❌ Error checking async config: " + e.getMessage());
        }
    }

    @Test
    void step5_TestJsonParsing() throws Exception {
        System.out.println("\n=== STEP 5: TEST JSON PARSING ===");

        String payload = loadJsonFixture("workflow_run_success.json");

        try {
            // Try to manually parse the JSON using the same ObjectMapper
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(payload);

            System.out.println("✅ JSON parsing successful");
            System.out.println("📋 Event type: " + root.path("action").asText());
            System.out.println("📋 Repository: " + root.path("repository").path("name").asText());
            System.out.println("📋 Workflow status: " + root.path("workflow_run").path("status").asText());
            System.out.println("📋 Workflow conclusion: " + root.path("workflow_run").path("conclusion").asText());

            // Check if required fields exist
            JsonNode workflowRun = root.path("workflow_run");
            if (workflowRun.isMissingNode()) {
                System.out.println("❌ Missing workflow_run node!");
            } else {
                System.out.println("✅ workflow_run node exists");
            }

        } catch (Exception e) {
            System.out.println("❌ JSON parsing failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanDatabase() {
        try {
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
            try {
                queueTrackerRepository.deleteAll();
                buildRepository.deleteAll();
                pipelineRepository.deleteAll();
            } catch (Exception ignored) {}
        }
    }

    private String loadJsonFixture(String filename) throws Exception {
        ClassPathResource resource = new ClassPathResource("fixtures/" + filename);
        return Files.readString(resource.getFile().toPath());
    }
}