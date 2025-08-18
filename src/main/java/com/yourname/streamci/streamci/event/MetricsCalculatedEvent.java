package com.yourname.streamci.streamci.event;

import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.model.PipelineMetrics;
import org.springframework.context.ApplicationEvent;

public class MetricsCalculatedEvent extends ApplicationEvent {

    private final Pipeline pipeline;
    private final PipelineMetrics metrics;

    public MetricsCalculatedEvent(Object source, Pipeline pipeline, PipelineMetrics metrics) {
        super(source);
        this.pipeline = pipeline;
        this.metrics = metrics;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public PipelineMetrics getMetrics() {
        return metrics;
    }
}