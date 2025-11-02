package com.spectrayan.sse.server.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.MDC;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import org.reactivestreams.Subscription;
import reactor.util.context.Context;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registers a global Reactor operator hook that bridges values from Reactor Context into SLF4J MDC
 * for every reactive signal. This centralizes MDC handling so individual components (like
 * SseEmitter) don't need to manually copy values.
 *
 * Scoping: the bridge only activates for reactive chains that include a context marker key
 * (see {@link SseServerProperties#getMdcContextKey()}). This ensures host applications using
 * this library as a dependency don't get their unrelated Reactor pipelines affected.
 *
 * Keys bridged:
 * - topic (set by controller when subscribing)
 * - any keys configured via {@link SseServerProperties#getLogHeaders()} values (MDC key names)
 *
 * Note: Correlation ID handling has been removed from the bridge.
 */
public class ReactorMdcConfiguration {

    public static final String HOOK_KEY = "mdcContextLifter";

    private final Set<String> mdcKeys = new LinkedHashSet<>();
    private final boolean enabled;
    private final String contextMarkerKey;

    public ReactorMdcConfiguration(SseServerProperties properties) {
        this.enabled = properties == null || properties.isMdcBridgeEnabled();
        this.contextMarkerKey = (properties != null ? properties.getMdcContextKey() : "sseMdc");
        // Include topic
        mdcKeys.add("topic");
        // Add configured MDC keys from properties (values of the header->HeaderRule map)
        if (properties != null) {
            Map<String, SseServerProperties.HeaderRule> map = properties.getLogHeaders();
            if (map != null && !map.isEmpty()) {
                for (SseServerProperties.HeaderRule rule : map.values()) {
                    if (rule != null) {
                        String mdcKey = rule.getMdcKey();
                        if (mdcKey != null && !mdcKey.isBlank()) {
                            mdcKeys.add(mdcKey);
                        }
                    }
                }
            }
        }
    }

    @PostConstruct
    public void registerHook() {
        if (!enabled) {
            return;
        }
        Hooks.onEachOperator(HOOK_KEY, Operators.lift((sc, actual) -> new CoreSubscriber<Object>() {
            Subscription s;

            @Override
            public void onSubscribe(Subscription s) {
                this.s = s;
                actual.onSubscribe(s);
            }

            @Override
            public void onNext(Object o) {
                withContextMdc(actual.currentContext(), () -> actual.onNext(o));
            }

            @Override
            public void onError(Throwable t) {
                withContextMdc(actual.currentContext(), () -> actual.onError(t));
            }

            @Override
            public void onComplete() {
                withContextMdc(actual.currentContext(), actual::onComplete);
            }

            @Override
            public Context currentContext() {
                return actual.currentContext();
            }
        }));
    }

    @PreDestroy
    public void removeHook() {
        Hooks.resetOnEachOperator(HOOK_KEY);
    }

    private void withContextMdc(Context ctx, Runnable action) {
        if (!enabled) {
            action.run();
            return;
        }
        boolean activated = false;
        try {
            if (ctx != null && !ctx.isEmpty() && ctx.hasKey(contextMarkerKey)) {
                activated = true;
                for (String key : mdcKeys) {
                    if (ctx.hasKey(key)) {
                        Object val = ctx.get(key);
                        MDC.put(key, String.valueOf(val));
                    }
                }
            }
            action.run();
        } finally {
            if (activated) {
                for (String key : mdcKeys) {
                    MDC.remove(key);
                }
            }
        }
    }
}
