package com.yourname.streamci.streamci.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    // calculate success rate trends over time (last 7 days)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSuccessRateTrends(List<Map<String, Object>> workflows) {
        List<Map<String, Object>> trends = new ArrayList<>();

        // group workflows by day
        Map<String, List<Map<String, Object>>> workflowsByDay = workflows.stream()
                .filter(w -> w.get("created_at") != null)
                .collect(Collectors.groupingBy(w -> {
                    String createdAt = (String) w.get("created_at");
                    return createdAt.split("T")[0]; // get date part only
                }));

        // calculate success rate per day
        workflowsByDay.forEach((date, dayWorkflows) -> {
            long total = dayWorkflows.size();
            long successful = dayWorkflows.stream()
                    .filter(w -> "success".equals(w.get("conclusion")))
                    .count();

            double successRate = total > 0 ? (successful * 100.0 / total) : 0.0;

            Map<String, Object> trend = new HashMap<>();
            trend.put("date", date);
            trend.put("success_rate", Math.round(successRate * 10.0) / 10.0); // round to 1 decimal
            trend.put("total_builds", total);
            trend.put("successful_builds", successful);
            trends.add(trend);
        });

        // sort by date
        return trends.stream()
                .sorted((a, b) -> ((String)a.get("date")).compareTo((String)b.get("date")))
                .collect(Collectors.toList());
    }

    // detect failure patterns (top failure causes)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getFailurePatterns(List<Map<String, Object>> workflows) {
        List<Map<String, Object>> patterns = new ArrayList<>();

        // group failed workflows by repository
        Map<String, Long> failuresByRepo = workflows.stream()
                .filter(w -> "failure".equals(w.get("conclusion")))
                .collect(Collectors.groupingBy(
                        w -> (String) w.get("repository"),
                        Collectors.counting()
                ));

        // convert to list and add failure rate
        failuresByRepo.forEach((repo, failures) -> {
            long totalForRepo = workflows.stream()
                    .filter(w -> repo.equals(w.get("repository")))
                    .count();

            double failureRate = totalForRepo > 0 ? (failures * 100.0 / totalForRepo) : 0.0;

            if (failureRate > 20.0) { // only show repos with >20% failure rate
                Map<String, Object> pattern = new HashMap<>();
                pattern.put("repository", repo);
                pattern.put("failure_count", failures);
                pattern.put("total_builds", totalForRepo);
                pattern.put("failure_rate", Math.round(failureRate * 10.0) / 10.0);
                patterns.add(pattern);
            }
        });

        // sort by failure rate descending
        return patterns.stream()
                .sorted((a, b) -> Double.compare(
                        (Double)b.get("failure_rate"),
                        (Double)a.get("failure_rate")
                ))
                .limit(5) // top 5 problem areas
                .collect(Collectors.toList());
    }

    // calculate basic build stats (simplified since no duration data in current structure)
    @SuppressWarnings("unchecked")
    public Map<String, Object> getBuildDurationStats(List<Map<String, Object>> workflows) {
        Map<String, Object> stats = new HashMap<>();

        // for now, just return basic stats since github api doesn't give duration directly
        long totalBuilds = workflows.size();
        long successfulBuilds = workflows.stream()
                .filter(w -> "success".equals(w.get("conclusion")))
                .count();
        long failedBuilds = workflows.stream()
                .filter(w -> "failure".equals(w.get("conclusion")))
                .count();
        long runningBuilds = workflows.stream()
                .filter(w -> "in_progress".equals(w.get("status")) || "queued".equals(w.get("status")))
                .count();

        stats.put("total_builds", totalBuilds);
        stats.put("successful_builds", successfulBuilds);
        stats.put("failed_builds", failedBuilds);
        stats.put("running_builds", runningBuilds);
        stats.put("builds_analyzed", totalBuilds);

        return stats;
    }
}