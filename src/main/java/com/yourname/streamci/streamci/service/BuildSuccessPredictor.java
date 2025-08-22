package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BuildSuccessPredictor {

    private static final Logger logger = LoggerFactory.getLogger(BuildSuccessPredictor.class);

    private final BuildService buildService;
    private final PatternAnalysisService patternAnalysisService;

    public BuildSuccessPredictor(BuildService buildService,
                                 PatternAnalysisService patternAnalysisService) {
        this.buildService = buildService;
        this.patternAnalysisService = patternAnalysisService;
    }

    public SuccessPrediction predictNextBuildSuccess(Integer pipelineId) {
        return predictNextBuildSuccess(pipelineId, null, null);
    }

    public SuccessPrediction predictNextBuildSuccess(Integer pipelineId, String committer, String branch) {
        logger.info("predicting success for pipeline {}, committer: {}, branch: {}",
                pipelineId, committer, branch);

        List<Build> builds = buildService.getBuildsByPipelineId(pipelineId);

        if (builds.size() < 5) {
            return SuccessPrediction.builder()
                    .probability(50.0)
                    .confidence("low")
                    .reasoning("insufficient historical data")
                    .factors(Map.of("data_points", (double) builds.size()))
                    .build();
        }

        Map<String, Double> factors = new HashMap<>();

        double baseSuccessRate = calculateBaseSuccessRate(builds);
        factors.put("base_success_rate", baseSuccessRate);

        double timeFactor = calculateTimeFactor(builds);
        factors.put("time_factor", timeFactor);

        double committerFactor = calculateCommitterFactor(builds, committer);
        factors.put("committer_factor", committerFactor);

        double trendFactor = calculateTrendFactor(builds);
        factors.put("trend_factor", trendFactor);

        double branchFactor = calculateBranchFactor(builds, branch);
        factors.put("branch_factor", branchFactor);

        double finalScore = (baseSuccessRate * 0.3) +
                (timeFactor * 0.25) +
                (committerFactor * 0.2) +
                (trendFactor * 0.15) +
                (branchFactor * 0.1);

        finalScore = Math.max(5.0, Math.min(95.0, finalScore));

        String confidence = getConfidence(builds.size(), finalScore);
        String reasoning = buildReasoning(factors, finalScore);

        return SuccessPrediction.builder()
                .probability(Math.round(finalScore * 10.0) / 10.0)
                .confidence(confidence)
                .reasoning(reasoning)
                .factors(factors)
                .build();
    }

    private double calculateBaseSuccessRate(List<Build> builds) {
        List<Build> recent = builds.stream()
                .limit(20)
                .collect(Collectors.toList());

        long successes = recent.stream()
                .filter(b -> "success".equals(b.getStatus()))
                .count();

        return (double) successes / recent.size() * 100.0;
    }

    private double calculateTimeFactor(List<Build> builds) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        List<Build> sameHourBuilds = builds.stream()
                .filter(b -> getEffectiveTimestamp(b) != null)
                .filter(b -> getEffectiveTimestamp(b).getHour() == currentHour)
                .collect(Collectors.toList());

        if (sameHourBuilds.size() < 3) {
            return 70.0;
        }

        long successes = sameHourBuilds.stream()
                .filter(b -> "success".equals(b.getStatus()))
                .count();

        return (double) successes / sameHourBuilds.size() * 100.0;
    }

    private double calculateCommitterFactor(List<Build> builds, String committer) {
        if (committer == null || committer.equals("unknown")) {
            return 70.0;
        }

        List<Build> committerBuilds = builds.stream()
                .filter(b -> committer.equals(b.getCommitter()))
                .limit(10)
                .collect(Collectors.toList());

        if (committerBuilds.size() < 3) {
            return 70.0;
        }

        long successes = committerBuilds.stream()
                .filter(b -> "success".equals(b.getStatus()))
                .count();

        return (double) successes / committerBuilds.size() * 100.0;
    }

    private double calculateTrendFactor(List<Build> builds) {
        if (builds.size() < 10) return 70.0;

        List<Build> recent5 = builds.stream().limit(5).collect(Collectors.toList());
        List<Build> previous5 = builds.stream().skip(5).limit(5).collect(Collectors.toList());

        double recentSuccess = recent5.stream()
                .filter(b -> "success".equals(b.getStatus()))
                .count() / 5.0;

        double previousSuccess = previous5.stream()
                .filter(b -> "success".equals(b.getStatus()))
                .count() / 5.0;

        double trend = (recentSuccess - previousSuccess) * 100.0;

        return Math.max(20.0, Math.min(100.0, 70.0 + trend));
    }

    private double calculateBranchFactor(List<Build> builds, String branch) {
        if (branch == null || branch.equals("unknown")) {
            return 70.0;
        }

        List<Build> branchBuilds = builds.stream()
                .filter(b -> branch.equals(b.getBranch()))
                .limit(10)
                .collect(Collectors.toList());

        if (branchBuilds.size() < 3) {
            if ("main".equals(branch) || "master".equals(branch)) {
                return 80.0;
            }
            return 60.0;
        }

        long successes = branchBuilds.stream()
                .filter(b -> "success".equals(b.getStatus()))
                .count();

        return (double) successes / branchBuilds.size() * 100.0;
    }

    private String getConfidence(int dataPoints, double score) {
        if (dataPoints < 10) return "low";
        if (dataPoints < 25) return "medium";
        return "high";
    }

    private String buildReasoning(Map<String, Double> factors, double finalScore) {
        List<String> reasons = new ArrayList<>();

        if (factors.get("base_success_rate") > 80) {
            reasons.add("strong historical performance");
        } else if (factors.get("base_success_rate") < 50) {
            reasons.add("recent builds struggling");
        }

        if (factors.get("trend_factor") > 80) {
            reasons.add("improving trend");
        } else if (factors.get("trend_factor") < 50) {
            reasons.add("declining trend");
        }

        if (factors.get("committer_factor") > 85) {
            reasons.add("reliable committer");
        } else if (factors.get("committer_factor") < 50) {
            reasons.add("committer has recent failures");
        }

        if (reasons.isEmpty()) {
            if (finalScore > 70) {
                return "moderate confidence based on average performance";
            } else {
                return "elevated risk based on recent patterns";
            }
        }

        return String.join(", ", reasons);
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
}