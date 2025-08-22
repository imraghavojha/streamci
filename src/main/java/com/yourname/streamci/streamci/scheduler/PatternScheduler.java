package com.yourname.streamci.streamci.scheduler;

import com.yourname.streamci.streamci.service.PatternAnalysisService;
import com.yourname.streamci.streamci.service.PipelineService;
import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.model.PatternDetectionResult;
import com.yourname.streamci.streamci.model.FailurePattern;
import com.yourname.streamci.streamci.repository.FailurePatternRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PatternScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PatternScheduler.class);

    private final PatternAnalysisService patternService;
    private final PipelineService pipelineService;
    private final FailurePatternRepository patternRepository;

    public PatternScheduler(PatternAnalysisService patternService,
                            PipelineService pipelineService,
                            FailurePatternRepository patternRepository) {
        this.patternService = patternService;
        this.pipelineService = pipelineService;
        this.patternRepository = patternRepository;
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 60000)
    public void analyzePatterns() {
        logger.info("starting scheduled pattern analysis");

        try {
            List<Pipeline> pipelines = pipelineService.getAllPipelines();

            for (Pipeline pipeline : pipelines) {
                try {
                    analyzeAndStorePatternsForPipeline(pipeline);
                } catch (Exception e) {
                    logger.error("failed to analyze patterns for pipeline {}: {}",
                            pipeline.getId(), e.getMessage());
                }
            }

            logger.info("completed pattern analysis for {} pipelines", pipelines.size());

        } catch (Exception e) {
            logger.error("error in pattern scheduler: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldPatterns() {
        logger.info("cleaning up old pattern data");

        try {
            List<Pipeline> pipelines = pipelineService.getAllPipelines();
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

            for (Pipeline pipeline : pipelines) {
                patternRepository.deleteByPipelineIdAndDetectedAtBefore(pipeline.getId(), cutoff);
            }

            logger.info("cleaned up pattern data older than 30 days");

        } catch (Exception e) {
            logger.error("error cleaning up patterns: {}", e.getMessage());
        }
    }

    private void analyzeAndStorePatternsForPipeline(Pipeline pipeline) {
        List<PatternDetectionResult> patterns = patternService.analyzeFailurePatterns(pipeline.getId(), 7);

        logger.debug("found {} patterns for pipeline {}", patterns.size(), pipeline.getName());

        for (PatternDetectionResult pattern : patterns) {
            if (pattern.getConfidence() > 0.6) {
                saveOrUpdatePattern(pipeline, pattern);
            }
        }
    }

    private void saveOrUpdatePattern(Pipeline pipeline, PatternDetectionResult pattern) {
        var existing = patternRepository.findByPipelineIdAndPatternTypeAndDescription(
                pipeline.getId(), pattern.getPatternType(), pattern.getDescription());

        if (existing.isPresent()) {
            FailurePattern existingPattern = existing.get();
            existingPattern.setConfidence(pattern.getConfidence());
            existingPattern.setFrequency(pattern.getFrequency());
            existingPattern.setLastOccurrence(pattern.getLastOccurrence());
            existingPattern.setRecommendation(pattern.getRecommendation());
            patternRepository.save(existingPattern);
        } else {
            FailurePattern newPattern = FailurePattern.builder()
                    .pipeline(pipeline)
                    .patternType(pattern.getPatternType())
                    .description(pattern.getDescription())
                    .confidence(pattern.getConfidence())
                    .frequency(pattern.getFrequency())
                    .recommendation(pattern.getRecommendation())
                    .lastOccurrence(pattern.getLastOccurrence())
                    .details(pattern.getDetails() != null ? pattern.getDetails().toString() : "")
                    .build();

            patternRepository.save(newPattern);
        }
    }
}