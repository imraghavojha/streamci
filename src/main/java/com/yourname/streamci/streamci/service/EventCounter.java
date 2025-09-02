package com.yourname.streamci.streamci.service;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class EventCounter {

    private final AtomicLong eventCount = new AtomicLong(0);
    private final Instant startTime = Instant.now();

    // called every time webhook is processed
    public void incrementEvents() {
        eventCount.incrementAndGet();
    }

    // calculate events per minute - this gives us the "1000+ events/minute" metric
    public long getEventsPerMinute() {
        long totalMinutes = Duration.between(startTime, Instant.now()).toMinutes();
        return totalMinutes > 0 ? eventCount.get() / totalMinutes : eventCount.get();
    }

    // get total events processed
    public long getTotalEvents() {
        return eventCount.get();
    }

    // get uptime in minutes
    public long getUptimeMinutes() {
        return Duration.between(startTime, Instant.now()).toMinutes();
    }
}