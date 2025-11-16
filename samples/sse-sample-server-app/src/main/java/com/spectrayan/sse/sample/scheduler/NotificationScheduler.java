package com.spectrayan.sse.sample.scheduler;

import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.template.SseTemplate;
import com.spectrayan.sse.sample.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    //public static final String TOPIC = "notifications";

    private final SseEmitter sseEmitter;
    private final SseTemplate sseTemplate;

    public NotificationScheduler(SseEmitter sseEmitter, SseTemplate sseTemplate) {
        this.sseEmitter = sseEmitter;
        this.sseTemplate = sseTemplate;
    }

    // Emit a message every 5 seconds
    //@Scheduled(fixedRate = 5000, initialDelay = 2000)
    public void emitHeartbeatMessage() {
        try {
            String msg = "Hello at " + Instant.now();
            sseEmitter.emit(  msg);
            log.info("Scheduled message emitted to all topics");
        } catch (Exception ex) {
            log.warn("Failed to emit scheduled message: {}", ex.toString());
        }
    }

    // Emit a complex object every 15 seconds
    @Scheduled(fixedRate = 15000, initialDelay = 5000)
    public void emitComplexNotification() {
        try {
            Notification n = new Notification(UUID.randomUUID().toString(),
                    "Periodic notification",
                    Instant.now());
            sseEmitter.emit( n);
            log.info("Scheduled complex notification emitted to all topics id={}", n.id());
        } catch (Exception ex) {
            log.warn("Failed to emit complex notification: {}", ex.toString());
        }
    }

    // New: Emit using SseTemplate every 20 seconds (keeps emitter samples as-is)
    @Scheduled(fixedRate = 20000, initialDelay = 7000)
    public void emitTemplateNotification() {
        try {
            Notification n = new Notification(UUID.randomUUID().toString(),
                    "Template-based notification",
                    Instant.now());
            // Demonstrate targeting a specific topic via template API
            String topic = "notifications";
            sseTemplate.broadcast(  n);
            log.info("Template notification sent to topic={} id={}", topic, n.id());
        } catch (Exception ex) {
            log.warn("Failed to emit template-based notification: {}", ex.toString());
        }
    }
}
