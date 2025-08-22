package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import com.yourname.streamci.streamci.repository.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PatternAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(PatternAnalysisService.class);

    final BuildService buildService;
    private final FailurePatternRepository patternRepository;

    public PatternAnalysisService(BuildService buildService,
                                  FailurePatternRepository patternRepository) {
        this.buildService = buildService;
        this.patternRepository = patternRepository;
    }

    public List<PatternDetectionResult> analyzeFailurePatterns(Integer pipelineId, int lookbackDays) {
        logger.info("analyzing failure patterns for pipeline {} over {} days", pipelineId, lookbackDays);

        List<Build> builds = buildService.getBuildsByPipelineId(pipelineId);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(lookbackDays);

        List<Build> recentBuilds = builds.stream()
                .filter(b -> getEffectiveTimestamp(b) != null && getEffectiveTimestamp(b).isAfter(cutoff))
                .collect(Collectors.toList());

        List<PatternDetectionResult> patterns = new ArrayList<>();

        patterns.addAll(findCommonFailureCauses(recentBuilds));

        return patterns.stream()
                .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                .limit(10)
                .collect(Collectors.toList());
    }

    public List<FlakyTestResult> detectFlakyTests(Integer pipelineId) {
        List<Build> builds = buildService.getBuildsByPipelineId(pipelineId);

        Map<String, List<Build>> buildsByScenario = builds.stream()
                .filter(b -> b.getBranch() != null && b.getCommitter() != null)
                .collect(Collectors.groupingBy(b -> b.getBranch() + ":" + b.getCommitter()));

        List<FlakyTestResult> flakyTests = new ArrayList<>();

        for (Map.Entry<String, List<Build>> entry : buildsByScenario.entrySet()) {
            List<Build> scenarioBuilds = entry.getValue();

            if (scenarioBuilds.size() < 5) continue;

            long failures = scenarioBuilds.stream()
                    .filter(b -> "failure".equals(b.getStatus()))
                    .count();

            double failureRate = (double) failures / scenarioBuilds.size();

            if (failureRate > 0.3 && failureRate < 0.7) {
                flakyTests.add(FlakyTestResult.builder()
                        .testIdentifier(entry.getKey())
                        .failureRate(Math.round(failureRate * 1000.0) / 10.0)
                        .totalRuns(scenarioBuilds.size())
                        .failures((int) failures)
                        .recommendation("investigate test stability on " + entry.getKey().split(":")[0] + " branch")
                        .build());
            }
        }

        return flakyTests.stream()
                .sorted((a, b) -> Double.compare(b.getFailureRate(), a.getFailureRate()))
                .collect(Collectors.toList());
    }

    public List<TimePatternResult> detectTimeBasedPatterns(List<Build> builds) {
        Map<Integer, List<Build>> buildsByHour = builds.stream()
                .filter(b -> getEffectiveTimestamp(b) != null)
                .collect(Collectors.groupingBy(b -> getEffectiveTimestamp(b).getHour()));

        List<TimePatternResult> patterns = new ArrayList<>();
        double overallFailureRate = calculateOverallFailureRate(builds);

        logger.debug("overall failure rate: {}, total builds: {}", overallFailureRate, builds.size());

        for (Map.Entry<Integer, List<Build>> entry : buildsByHour.entrySet()) {
            List<Build> hourBuilds = entry.getValue();

            // lower minimum threshold for testing
            if (hourBuilds.size() < 2) continue;

            long failures = hourBuilds.stream()
                    .filter(b -> "failure".equals(b.getStatus()))
                    .count();

            double hourFailureRate = (double) failures / hourBuilds.size();

            logger.debug("hour {}: {} builds, {} failures, {}% failure rate",
                    entry.getKey(), hourBuilds.size(), failures, hourFailureRate * 100);

            // more sensitive threshold for pattern detection
            boolean isPattern = hourFailureRate > Math.max(0.5, overallFailureRate * 1.2);

            if (isPattern) {
                String riskLevel = hourFailureRate > 0.7 ? "high" : "medium";

                TimePatternResult pattern = TimePatternResult.builder()
                        .timeSlot(String.valueOf(entry.getKey()))
                        .failureRate(Math.round(hourFailureRate * 1000.0) / 10.0)
                        .totalBuilds(hourBuilds.size())
                        .failures((int) failures)
                        .riskLevel(riskLevel)
                        .build();

                patterns.add(pattern);
                logger.info("detected time pattern: hour {} with {}% failure rate",
                        entry.getKey(), pattern.getFailureRate());
            }
        }

        return patterns;
    }

    public List<CommitterPatternResult> detectCommitterPatterns(List<Build> builds) {
        Map<String, List<Build>> buildsByCommitter = builds.stream()
                .filter(b -> b.getCommitter() != null && !b.getCommitter().equals("unknown"))
                .collect(Collectors.groupingBy(Build::getCommitter));

        List<CommitterPatternResult> patterns = new ArrayList<>();
        double overallFailureRate = calculateOverallFailureRate(builds);

        for (Map.Entry<String, List<Build>> entry : buildsByCommitter.entrySet()) {
            List<Build> committerBuilds = entry.getValue();

            if (committerBuilds.size() < 5) continue;

            long failures = committerBuilds.stream()
                    .filter(b -> "failure".equals(b.getStatus()))
                    .count();

            double committerFailureRate = (double) failures / committerBuilds.size();

            if (committerFailureRate > overallFailureRate * 1.3) {
                String riskLevel = committerFailureRate > 0.4 ? "high" : "medium";

                patterns.add(CommitterPatternResult.builder()
                        .committer(entry.getKey())
                        .failureRate(Math.round(committerFailureRate * 1000.0) / 10.0)
                        .totalBuilds(committerBuilds.size())
                        .failures((int) failures)
                        .riskLevel(riskLevel)
                        .build());
            }
        }

        return patterns.stream()
                .sorted((a, b) -> Double.compare(b.getFailureRate(), a.getFailureRate()))
                .collect(Collectors.toList());
    }

    private List<PatternDetectionResult> findCommonFailureCauses(List<Build> builds) {
        List<PatternDetectionResult> causes = new ArrayList<>();

        List<TimePatternResult> timePatterns = detectTimeBasedPatterns(builds);
        for (TimePatternResult pattern : timePatterns) {
            causes.add(PatternDetectionResult.builder()
                    .patternType("time_based")
                    .description("failures spike at " + pattern.getTimeSlot())
                    .confidence(pattern.getFailureRate() / 100.0)
                    .frequency(pattern.getFailures())
                    .recommendation("avoid deployments around " + pattern.getTimeSlot())
                    .lastOccurrence(LocalDateTime.now())
                    .details(Map.of("time_slot", pattern.getTimeSlot(), "risk_level", pattern.getRiskLevel()))
                    .build());
        }

        List<CommitterPatternResult> committerPatterns = detectCommitterPatterns(builds);
        for (CommitterPatternResult pattern : committerPatterns) {
            causes.add(PatternDetectionResult.builder()
                    .patternType("committer_based")
                    .description(pattern.getCommitter() + " has elevated failure rate")
                    .confidence(pattern.getFailureRate() / 100.0)
                    .frequency(pattern.getFailures())
                    .recommendation("review commits from " + pattern.getCommitter())
                    .lastOccurrence(LocalDateTime.now())
                    .details(Map.of("committer", pattern.getCommitter(), "risk_level", pattern.getRiskLevel()))
                    .build());
        }

        return causes;
    }

    private double calculateOverallFailureRate(List<Build> builds) {
        if (builds.isEmpty()) return 0.0;

        long failures = builds.stream()
                .filter(b -> "failure".equals(b.getStatus()))
                .count();

        return (double) failures / builds.size();
    }

    private LocalDateTime getEffectiveTimestamp(Build build) {
        if (build.getStartTime() != null) {
            return build.getStartTime();
        }
        if (build.getEndTime() != null) {
            return build.getEndTime();
        }
        if (build.getCreatedAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (build.getCreatedAt().isBefore(now.minusMinutes(5))) {
                return build.getCreatedAt();
            }
        }
        return null;
    }

    public List<TimePatternResult> getTimeBasedCorrelations(Integer pipelineId) {
        List<Build> builds = buildService.getBuildsByPipelineId(pipelineId);
        return detectTimeBasedPatterns(builds);
    }

    public List<CommitterPatternResult> getCommitterBasedCorrelations(Integer pipelineId) {
        List<Build> builds = buildService.getBuildsByPipelineId(pipelineId);
        return detectCommitterPatterns(builds);
    }
}