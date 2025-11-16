package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.template.SseConnectContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AllowAllClientFilterTest {

    @Test
    void allowsAnyContext() {
        AllowAllClientFilter f = new AllowAllClientFilter();
        SseConnectContext ctx = new SseConnectContext("topic","sid",null,"remote", java.util.Map.of(), java.util.Map.of());
        assertTrue(f.allow(ctx));
    }
}
