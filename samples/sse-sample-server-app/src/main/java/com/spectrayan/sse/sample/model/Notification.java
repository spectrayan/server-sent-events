package com.spectrayan.sse.sample.model;

import java.time.Instant;

public record Notification(
        String id,
        String message,
        Instant timestamp
) {
}
