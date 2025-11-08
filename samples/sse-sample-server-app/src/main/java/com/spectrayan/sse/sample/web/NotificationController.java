package com.spectrayan.sse.sample.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Simple REST controller to acknowledge a notification has been read.
 *
 * Endpoint used by the Angular sample app when a user clicks a bell item:
 * POST /api/notifications/mark-read
 * Body: { "notificationId": "..." }
 *
 * Returns 200 OK with a tiny JSON payload. No persistence is performed; this is
 * purely a demo stub to show how UI-driven callbacks can notify the server.
 */
@RestController
@RequestMapping(path = "/api/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @PostMapping(path = "/mark-read", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> markRead(@RequestBody(required = false) MarkReadRequest body) {
        String id = body != null ? body.getNotificationId() : null;
        log.info("Mark-as-read received for notificationId={}", id);
        // No-op: in a real app you'd update storage here.
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "notificationId", id
        ));
    }

    /**
     * Minimal request DTO for mark-read.
     */
    public static class MarkReadRequest {
        private String notificationId;

        public MarkReadRequest() {}

        public String getNotificationId() {
            return notificationId;
        }

        public void setNotificationId(String notificationId) {
            this.notificationId = notificationId;
        }
    }
}
