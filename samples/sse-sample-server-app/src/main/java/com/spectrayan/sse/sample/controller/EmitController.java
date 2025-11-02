package com.spectrayan.sse.sample.controller;

import com.spectrayan.sse.server.emitter.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/emit")
public class EmitController {

    private static final Logger log = LoggerFactory.getLogger(EmitController.class);

    private final SseEmitter emitter;

    public EmitController(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @PostMapping(path = "/{topic}")
    public ResponseEntity<Map<String, Object>> emit(@PathVariable String topic, @RequestBody(required = false) Map<String, Object> body) {
        Object payload = (body == null || body.isEmpty()) ? Map.of("msg", "manual emit") : body;
        emitter.emit(topic, "manual", payload);
        log.info("Manually emitted event to topic {}", topic);
        return ResponseEntity.accepted().body(Map.of("topic", topic, "status", "emitted"));
    }
}
