package com.yourname.streamci.streamci.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

@Getter
public class BuildCompletedEvent extends ApplicationEvent {
    // Getters
    private final String buildId;
    private final Integer pipelineId;
    private final String status;
    private final Long duration;
    private final LocalDateTime completedAt;

    public BuildCompletedEvent(Object source, String buildId, Integer pipelineId,
                               String status, Long duration, LocalDateTime completedAt) {
        super(source);
        this.buildId = buildId;
        this.pipelineId = pipelineId;
        this.status = status;
        this.duration = duration;
        this.completedAt = completedAt;
    }

}

