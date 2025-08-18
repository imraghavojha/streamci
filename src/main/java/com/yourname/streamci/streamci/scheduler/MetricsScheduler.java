package com.yourname.streamci.streamci.scheduler;

import com.yourname.streamci.streamci.service.MetricsService;
import com.yourname.streamci.streamci.service.PipelineService;
import com.yourname.streamci.streamci.model.Pipeline;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class MetricsScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MetricsScheduler.class);
    private final MetricsService metricsService;
    private final PipelineService pipelineService;

    public MetricsScheduler(MetricsService metricsService, PipelineService pipelineService) {
        this.metricsService = metricsService;
        this.pipelineService = pipelineService;
    }

    // run every 5 minutes
    @Scheduled(fixedDelay = 300000, initialDelay = 10000)
    public void calculateMetrics() {
        logger.info("Starting scheduled metrics calculation at {}", LocalDateTime.now());

        try {
            List<Pipeline> pipelines = pipelineService.getAllPipelines();
            logger.info("Found {} pipelines to calculate metrics for", pipelines.size());

            for (Pipeline pipeline : pipelines) {
                try {
                    metricsService.calculateMetricsForPipeline(pipeline.getId());
                } catch (Exception e) {
                    logger.error("Failed to calculate metrics for pipeline {}: {}",
                            pipeline.getId(), e.getMessage());
                }
            }

            logger.info("Completed metrics calculation for {} pipelines", pipelines.size());

        } catch (Exception e) {
            logger.error("Error in metrics scheduler: {}", e.getMessage());
        }
    }

    // also run at specific times for better patterns
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void hourlyMetrics() {
        logger.info("Running hourly metrics calculation");
        calculateMetrics();
    }

    @Scheduled(cron = "0 0 9 * * MON-FRI") // 9 AM on weekdays
    public void morningReport() {
        logger.info("Running morning metrics calculation");
        calculateMetrics();
    }
}