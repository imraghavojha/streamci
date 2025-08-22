package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PatternAnalysisServiceTest {

    @Autowired
    private PatternAnalysisService patternService;

    @Autowired
    private BuildSuccessPredictor successPredictor;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private BuildRepository buildRepository;

    @Autowired
    private FailurePatternRepository patternRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Pipeline testPipeline;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        testPipeline = Pipeline.builder()
                .name("PatternTestPipeline")
                .status("active")
                .duration(300)
                .build();
        testPipeline = pipelineRepository.save(testPipeline);

        System.out.println("🔧 Test setup complete - Pipeline: " + testPipeline.getId());
    }

    @Test
    @Order(1)
    void testTimeBasedPatternDetection() {
        System.out.println("\n=== TEST 1: Time-Based Pattern Detection ===");

        LocalDateTime now = LocalDateTime.now();

        // create failures clustered at 2 PM (14:00)
        for (int i = 0; i < 5; i++) {
            createBuildAtTime("failure", 400L, now.withHour(14).withMinute(i * 10));
        }

        // create mixed results at other times
        createBuildAtTime("success", 300L, now.withHour(10).withMinute(0));
        createBuildAtTime("success", 300L, now.withHour(16).withMinute(0));
        createBuildAtTime("failure", 400L, now.withHour(12).withMinute(0));

        List<TimePatternResult> timePatterns = patternService.getTimeBasedCorrelations(testPipeline.getId());

        System.out.println("📊 Time patterns found: " + timePatterns.size());
        timePatterns.forEach(p ->
                System.out.println("  " + p.getTimeSlot() + ": " + p.getFailureRate() + "% failure rate")
        );

        assertThat(timePatterns).hasSize(1);
        TimePatternResult pattern = timePatterns.get(0);
        assertThat(pattern.getTimeSlot()).isEqualTo("14");
        assertThat(pattern.getFailureRate()).isGreaterThan(80.0);

        System.out.println("✅ Time pattern detection working correctly");
    }

    @Test
    @Order(2)
    void testCommitterBasedPatternDetection() {
        System.out.println("\n=== TEST 2: Committer-Based Pattern Detection ===");

        // create builds by different committers
        createBuilds("good-dev", 8, "success", 300L);
        createBuilds("good-dev", 2, "failure", 400L);

        createBuilds("problematic-dev", 3, "success", 350L);
        createBuilds("problematic-dev", 7, "failure", 450L);

        createBuilds("new-dev", 5, "success", 280L);
        createBuilds("new-dev", 1, "failure", 380L);

        List<CommitterPatternResult> committerPatterns = patternService.getCommitterBasedCorrelations(testPipeline.getId());

        System.out.println("📊 Committer patterns found: " + committerPatterns.size());
        committerPatterns.forEach(p ->
                System.out.println("  " + p.getCommitter() + ": " + p.getFailureRate() + "% failure rate")
        );

        assertThat(committerPatterns).hasSize(1);
        CommitterPatternResult pattern = committerPatterns.get(0);
        assertThat(pattern.getCommitter()).isEqualTo("problematic-dev");
        assertThat(pattern.getFailureRate()).isGreaterThan(50.0);

        System.out.println("✅ Committer pattern detection working correctly");
    }

    @Test
    @Order(3)
    void testFlakyTestDetection() {
        System.out.println("\n=== TEST 3: Flaky Test Detection ===");

        // create flaky scenario: same branch/committer with 50% failure rate
        for (int i = 0; i < 10; i++) {
            String status = (i % 2 == 0) ? "success" : "failure";
            createBuild("flaky-branch", "test-dev", status, 300L);
        }

        // create stable scenario: same branch/committer with consistent success
        for (int i = 0; i < 8; i++) {
            createBuild("stable-branch", "reliable-dev", "success", 300L);
        }

        List<FlakyTestResult> flakyTests = patternService.detectFlakyTests(testPipeline.getId());

        System.out.println("📊 Flaky tests found: " + flakyTests.size());
        flakyTests.forEach(f ->
                System.out.println("  " + f.getTestIdentifier() + ": " + f.getFailureRate() + "% failure rate")
        );

        assertThat(flakyTests).hasSize(1);
        FlakyTestResult flaky = flakyTests.get(0);
        assertThat(flaky.getTestIdentifier()).isEqualTo("flaky-branch:test-dev");
        assertThat(flaky.getFailureRate()).isBetween(40.0, 60.0);

        System.out.println("✅ Flaky test detection working correctly");
    }

    @Test
    @Order(4)
    void testSuccessPredictionBaseline() {
        System.out.println("\n=== TEST 4: Success Prediction Baseline ===");

        // create mostly successful builds
        createBuilds("reliable-dev", 8, "success", 300L);
        createBuilds("reliable-dev", 2, "failure", 400L);

        SuccessPrediction prediction = successPredictor.predictNextBuildSuccess(testPipeline.getId());

        System.out.println("📊 Prediction results:");
        System.out.println("  Probability: " + prediction.getProbability() + "%");
        System.out.println("  Confidence: " + prediction.getConfidence());
        System.out.println("  Reasoning: " + prediction.getReasoning());

        assertThat(prediction.getProbability()).isBetween(60.0, 95.0);
        assertThat(prediction.getConfidence()).isNotNull();
        assertThat(prediction.getReasoning()).isNotNull();

        System.out.println("✅ Success prediction baseline working");
    }

    @Test
    @Order(5)
    void testSuccessPredictionWithContext() {
        System.out.println("\n=== TEST 5: Success Prediction with Context ===");

        // create different patterns for different committers
        createBuilds("good-dev", 9, "success", 300L);
        createBuilds("good-dev", 1, "failure", 400L);

        createBuilds("bad-dev", 3, "success", 350L);
        createBuilds("bad-dev", 7, "failure", 450L);

        SuccessPrediction goodPrediction = successPredictor.predictNextBuildSuccess(
                testPipeline.getId(), "good-dev", "main"
        );

        SuccessPrediction badPrediction = successPredictor.predictNextBuildSuccess(
                testPipeline.getId(), "bad-dev", "feature"
        );

        System.out.println("📊 Good dev prediction: " + goodPrediction.getProbability() + "%");
        System.out.println("📊 Bad dev prediction: " + badPrediction.getProbability() + "%");

        assertThat(goodPrediction.getProbability()).isGreaterThan(badPrediction.getProbability());

        System.out.println("✅ Contextual predictions working correctly");
    }

    @Test
    @Order(6)
    void testPatternAnalysisIntegration() {
        System.out.println("\n=== TEST 6: Full Pattern Analysis Integration ===");

        LocalDateTime now = LocalDateTime.now();

        // create complex scenario with multiple patterns
        // time pattern: failures at 2 PM
        for (int i = 0; i < 4; i++) {
            createBuildAtTime("failure", 400L, now.withHour(14).withMinute(i * 5));
        }

        // committer pattern: one problematic developer
        createBuilds("problem-dev", 2, "success", 300L);
        createBuilds("problem-dev", 6, "failure", 500L);

        // good baseline
        createBuilds("good-dev", 15, "success", 280L);
        createBuilds("good-dev", 3, "failure", 380L);

        List<PatternDetectionResult> patterns = patternService.analyzeFailurePatterns(testPipeline.getId(), 7);

        System.out.println("📊 Patterns detected: " + patterns.size());
        patterns.forEach(p ->
                System.out.println("  " + p.getPatternType() + ": " + p.getDescription() +
                        " (confidence: " + String.format("%.2f", p.getConfidence()) + ")")
        );

        assertThat(patterns).hasSizeGreaterThan(0);

        boolean hasTimePattern = patterns.stream()
                .anyMatch(p -> "time_based".equals(p.getPatternType()));
        boolean hasCommitterPattern = patterns.stream()
                .anyMatch(p -> "committer_based".equals(p.getPatternType()));

        assertThat(hasTimePattern || hasCommitterPattern).isTrue();

        System.out.println("✅ Full pattern analysis integration working");
    }

    @Test
    @Order(7)
    void testInsufficientDataHandling() {
        System.out.println("\n=== TEST 7: Insufficient Data Handling ===");

        // create minimal data
        createBuild("main", "dev", "success", 300L);
        createBuild("main", "dev", "failure", 400L);

        SuccessPrediction prediction = successPredictor.predictNextBuildSuccess(testPipeline.getId());

        System.out.println("📊 Low data prediction: " + prediction.getProbability() + "%");
        System.out.println("📊 Confidence: " + prediction.getConfidence());

        assertThat(prediction.getConfidence()).isEqualTo("low");
        assertThat(prediction.getReasoning()).contains("insufficient");

        System.out.println("✅ Insufficient data handling working");
    }

    // helper methods
    private void createBuilds(String committer, int count, String status, Long duration) {
        for (int i = 0; i < count; i++) {
            createBuild("main", committer, status, duration);
        }
    }

    private void createBuild(String branch, String committer, String status, Long duration) {
        LocalDateTime time = LocalDateTime.now().minusMinutes((long) (Math.random() * 60));
        createBuildAtTime(status, duration, time, branch, committer);
    }

    private void createBuildAtTime(String status, Long duration, LocalDateTime time) {
        createBuildAtTime(status, duration, time, "main", "testuser");
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
            jdbcTemplate.execute("DELETE FROM failure_patterns");
            jdbcTemplate.execute("DELETE FROM build");
            jdbcTemplate.execute("DELETE FROM pipeline");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (Exception e) {
            try {
                patternRepository.deleteAll();
                buildRepository.deleteAll();
                pipelineRepository.deleteAll();
            } catch (Exception ignored) {}
        }
    }
}