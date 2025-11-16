package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.template.ClientFilter;
import com.spectrayan.sse.server.template.SseConnectContext;

/**
 * Default client filter that allows all connections.
 */
public class AllowAllClientFilter implements ClientFilter {
    @Override
    public boolean allow(SseConnectContext ctx) { return true; }
}
