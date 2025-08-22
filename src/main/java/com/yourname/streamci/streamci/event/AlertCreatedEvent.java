package com.yourname.streamci.streamci.event;

import com.yourname.streamci.streamci.model.Alert;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AlertCreatedEvent extends ApplicationEvent {
    private final Alert alert;

    public AlertCreatedEvent(Object source, Alert alert) {
        super(source);
        this.alert = alert;
    }

}
