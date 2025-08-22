package com.yourname.streamci.streamci.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class QueueStatusChangedEvent extends ApplicationEvent {
    // Getters
    private final Integer pipelineId;
    private final int queuedCount;
    private final int runningCount;

    public QueueStatusChangedEvent(Object source, Integer pipelineId,
                                   int queuedCount, int runningCount) {
        super(source);
        this.pipelineId = pipelineId;
        this.queuedCount = queuedCount;
        this.runningCount = runningCount;
    }

}
