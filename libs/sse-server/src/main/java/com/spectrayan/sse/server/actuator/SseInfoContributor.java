package com.spectrayan.sse.server.actuator;

import com.spectrayan.sse.server.bridge.NoOpBroadcastBridge;
import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.config.SseServerProperties;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Spring Boot Actuator {@link InfoContributor} exposing SSE library metadata,
 * bridge configuration, and stream defaults under {@code /actuator/info}.
 *
 * @since 2.1.0
 */
public class SseInfoContributor implements InfoContributor {

    private static final String DEFAULT_VERSION = "2.1.0";
    private final SseServerProperties properties;
    private final SseBroadcastBridge broadcastBridge;
    private final String libraryVersion;

    public SseInfoContributor(SseServerProperties properties, SseBroadcastBridge broadcastBridge) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.broadcastBridge = broadcastBridge;
        this.libraryVersion = resolveLibraryVersion();
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> sseDetails = new LinkedHashMap<>();
        sseDetails.put("version", libraryVersion);
        sseDetails.put("basePath", properties.getBasePath() != null ? properties.getBasePath() : "/sse");

        String bridgeName = broadcastBridge != null
                ? broadcastBridge.getClass().getSimpleName()
                : "None";
        boolean isClustered = broadcastBridge != null && !(broadcastBridge instanceof NoOpBroadcastBridge);
        sseDetails.put("bridge", bridgeName);
        sseDetails.put("clustered", isClustered);

        Map<String, Object> streamDetails = new LinkedHashMap<>();
        if (properties.getStream() != null) {
            streamDetails.put("heartbeatEnabled", properties.getStream().isHeartbeatEnabled());
            if (properties.getStream().getHeartbeatInterval() != null) {
                streamDetails.put("heartbeatInterval", properties.getStream().getHeartbeatInterval().toString());
            }
            streamDetails.put("retryEnabled", properties.getStream().isRetryEnabled());
            if (properties.getStream().getRetry() != null) {
                streamDetails.put("retryInterval", properties.getStream().getRetry().toString());
            }
            streamDetails.put("mapErrorsToSse", properties.getStream().isMapErrorsToSse());
        }
        sseDetails.put("stream", streamDetails);

        Map<String, Object> metricsDetails = new LinkedHashMap<>();
        if (properties.getMetrics() != null) {
            metricsDetails.put("enabled", properties.getMetrics().isEnabled());
            metricsDetails.put("perTopic", properties.getMetrics().isPerTopic());
        }
        sseDetails.put("metrics", metricsDetails);

        builder.withDetail("sse", sseDetails);
    }

    private String resolveLibraryVersion() {
        // 1. Try Package implementation version
        String pkgVersion = getClass().getPackage().getImplementationVersion();
        if (pkgVersion != null && !pkgVersion.isBlank()) {
            return pkgVersion;
        }

        // 2. Try reading pom.properties from jar
        try (InputStream in = getClass().getResourceAsStream("/META-INF/maven/com.spectrayan.sse/sse-server/pom.properties")) {
            if (in != null) {
                Properties pomProps = new Properties();
                pomProps.load(in);
                String ver = pomProps.getProperty("version");
                if (ver != null && !ver.isBlank()) {
                    return ver;
                }
            }
        } catch (Exception ignored) {
            // fallback
        }

        return DEFAULT_VERSION;
    }
}
