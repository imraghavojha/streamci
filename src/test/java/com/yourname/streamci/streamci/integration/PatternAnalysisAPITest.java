package com.yourname.streamci.streamci.integration;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PatternAnalysisAPITest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Pipeline testPipeline;
    private String pipelineIdStr;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        testPipeline = Pipeline.builder()
                .name("APITestPipeline")
                .status("active")
                .duration(300)
                .build();
        testPipeline = pipelineRepository.save(testPipeline);

        // convert to string safely without calling methods on primitive
        pipelineIdStr = String.valueOf(testPipeline.getId());

        createTestData();
    }

    @Test
    @Order(1)
    void testPatternAnalysisEndpoint() throws Exception {
        System.out.println("\n=== API TEST 1: Pattern Analysis Endpoint ===");

        mockMvc.perform(get("/api/analysis/patterns")
                        .param("pipelineId", pipelineIdStr)
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pipeline_id", notNullValue()))
                .andExpect(jsonPath("$.analysis_period_days", is(7)))
                .andExpect(jsonPath("$.patterns_found", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.patterns", isA(java.util.List.class)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        System.out.println("✅ Pattern analysis endpoint working");
    }

    @Test
    @Order(2)
    void testFlakyTestsEndpoint() throws Exception {
        System.out.println("\n=== API TEST 2: Flaky Tests Endpoint ===");

        mockMvc.perform(get("/api/analysis/flaky-tests")
                        .param("pipelineId", pipelineIdStr))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pipeline_id", notNullValue()))
                .andExpect(jsonPath("$.flaky_tests_found", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.flaky_tests", isA(java.util.List.class)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        System.out.println("✅ Flaky tests endpoint working");
    }

    @Test
    @Order(3)
    void testTimeCorrelationsEndpoint() throws Exception {
        System.out.println("\n=== API TEST 3: Time Correlations Endpoint ===");

        mockMvc.perform(get("/api/analysis/correlations")
                        .param("pipelineId", pipelineIdStr)
                        .param("type", "time"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pipeline_id", notNullValue()))
                .andExpect(jsonPath("$.correlation_type", is("time")))
                .andExpect(jsonPath("$.correlations", isA(java.util.List.class)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        System.out.println("✅ Time correlations endpoint working");
    }

    @Test
    @Order(4)
    void testCommitterCorrelationsEndpoint() throws Exception {
        System.out.println("\n=== API TEST 4: Committer Correlations Endpoint ===");

        mockMvc.perform(get("/api/analysis/correlations")
                        .param("pipelineId", pipelineIdStr)
                        .param("type", "committer"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pipeline_id", notNullValue()))
                .andExpect(jsonPath("$.correlation_type", is("committer")))
                .andExpect(jsonPath("$.correlations", isA(java.util.List.class)));

        System.out.println("✅ Committer correlations endpoint working");
    }

    @Test
    @Order(5)
    void testSuccessPredictionEndpoint() throws Exception {
        System.out.println("\n=== API TEST 5: Success Prediction Endpoint ===");

        mockMvc.perform(get("/api/predictions/success")
                        .param("pipelineId", pipelineIdStr))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.probability", notNullValue()))
                .andExpect(jsonPath("$.confidence", notNullValue()))
                .andExpect(jsonPath("$.reasoning", notNullValue()))
                .andExpect(jsonPath("$.factors", notNullValue()));

        System.out.println("✅ Success prediction endpoint working");
    }

    @Test
    @Order(6)
    void testSuccessPredictionWithContext() throws Exception {
        System.out.println("\n=== API TEST 6: Success Prediction with Context ===");

        mockMvc.perform(get("/api/predictions/success")
                        .param("pipelineId", pipelineIdStr)
                        .param("committer", "good-dev")
                        .param("branch", "main"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.probability", notNullValue()))
                .andExpect(jsonPath("$.confidence", notNullValue()))
                .andExpect(jsonPath("$.reasoning", notNullValue()));

        System.out.println("✅ Contextual prediction endpoint working");
    }

    @Test
    @Order(7)
    void testAnalysisSummaryEndpoint() throws Exception {
        System.out.println("\n=== API TEST 7: Analysis Summary Endpoint ===");

        mockMvc.perform(get("/api/analysis/summary")
                        .param("pipelineId", pipelineIdStr))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pipeline_id", notNullValue()))
                .andExpect(jsonPath("$.patterns_count", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.flaky_tests_count", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.next_build_success_probability", notNullValue()))
                .andExpect(jsonPath("$.confidence", notNullValue()))
                .andExpect(jsonPath("$.top_issues", isA(java.util.List.class)))
                .andExpect(jsonPath("$.recommendations", isA(java.util.List.class)));

        System.out.println("✅ Analysis summary endpoint working");
    }

    @Test
    @Order(8)
    void testInvalidCorrelationType() throws Exception {
        System.out.println("\n=== API TEST 8: Invalid Correlation Type ===");

        mockMvc.perform(get("/api/analysis/correlations")
                        .param("pipelineId", pipelineIdStr)
                        .param("type", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", containsString("invalid correlation type")));

        System.out.println("✅ Error handling working correctly");
    }

    @Test
    @Order(9)
    void testFilesCorrelationType() throws Exception {
        System.out.println("\n=== API TEST 9: Files Correlation Type (Working Implementation) ===");

        mockMvc.perform(get("/api/analysis/correlations")
                        .param("pipelineId", pipelineIdStr)
                        .param("type", "files"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.correlation_type", is("files")))
                .andExpect(jsonPath("$.pipeline_id", notNullValue()))
                .andExpect(jsonPath("$.correlations", isA(java.util.List.class)))
                .andExpect(jsonPath("$.correlations", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.message", containsString("complete")));

        System.out.println("✅ File correlation analysis working correctly");
    }

    private void createTestData() {
        LocalDateTime now = LocalDateTime.now();

        // create time-based pattern: failures at 2 PM
        for (int i = 0; i < 3; i++) {
            createBuildAtTime("failure", 400L, now.withHour(14).withMinute(i * 10), "main", "testuser");
        }

        // create committer-based pattern
        createBuilds("good-dev", 8, "success", 300L);
        createBuilds("good-dev", 2, "failure", 400L);

        createBuilds("problematic-dev", 3, "success", 350L);
        createBuilds("problematic-dev", 5, "failure", 450L);

        // create flaky test pattern
        for (int i = 0; i < 6; i++) {
            String status = (i % 2 == 0) ? "success" : "failure";
            createBuild("flaky-branch", "flaky-dev", status, 300L);
        }

        // add some baseline data
        createBuilds("baseline-dev", 10, "success", 280L);
        createBuilds("baseline-dev", 2, "failure", 380L);
    }

    private void createBuilds(String committer, int count, String status, Long duration) {
        for (int i = 0; i < count; i++) {
            createBuild("main", committer, status, duration);
        }
    }

    private void createBuild(String branch, String committer, String status, Long duration) {
        LocalDateTime time = LocalDateTime.now().minusMinutes((long) (Math.random() * 60));
        createBuildAtTime(status, duration, time, branch, committer);
    }

    private void createBuildAtTime(String status, Long duration, LocalDateTime time, String branch, String committer) {
        Build build = Build.builder()
                .pipeline(testPipeline)
                .status(status)
                .duration(duration)
                .commitHash("commit" + Math.random())
                .committer(committer)
                .branch(branch)
                .startTime(time)
                .endTime(time.plusSeconds(duration != null ? duration : 300))
                .build();
        buildRepository.save(build);
    }

    private void cleanDatabase() {
        try {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("DELETE FROM build");
            jdbcTemplate.execute("DELETE FROM pipeline");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (Exception e) {
            try {
                buildRepository.deleteAll();
                pipelineRepository.deleteAll();
            } catch (Exception ignored) {}
        }
    }
}