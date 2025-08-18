package com.yourname.streamci.streamci.scheduler;

import com.yourname.streamci.streamci.service.QueueService;
import com.yourname.streamci.streamci.service.PipelineService;
import com.yourname.streamci.streamci.model.Pipeline;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Component
public class QueueScheduler {

    private static final Logger logger = LoggerFactory.getLogger(QueueScheduler.class);

    private final QueueService queueService;
    private final PipelineService pipelineService;

    public QueueScheduler(QueueService queueService, PipelineService pipelineService) {
        this.queueService = queueService;
        this.pipelineService = pipelineService;
    }

    // calculate queue metrics every 5 minutes
    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    public void calculateQueueMetrics() {
        logger.info("calculating queue metrics for all pipelines");

        try {
            List<Pipeline> pipelines = pipelineService.getAllPipelines();

            for (Pipeline pipeline : pipelines) {
                try {
                    queueService.calculateQueueMetrics(pipeline.getId());
                    logger.debug("calculated queue metrics for pipeline {}", pipeline.getId());
                } catch (Exception e) {
                    logger.error("failed to calculate queue for pipeline {}: {}",
                            pipeline.getId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("error in queue scheduler: {}", e.getMessage());
        }
    }
}