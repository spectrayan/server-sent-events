package com.spectrayan.sse.server.actuator;

import com.spectrayan.sse.server.bridge.NoOpBroadcastBridge;
import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.config.SseServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SseInfoContributorTest {

    @Test
    void contributeWithDefaultPropertiesAndNoOpBridge() {
        SseServerProperties properties = new SseServerProperties();
        properties.setBasePath("/custom-sse");

        SseInfoContributor contributor = new SseInfoContributor(properties, new NoOpBroadcastBridge());

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);

        Info info = builder.build();
        assertThat(info.getDetails()).containsKey("sse");

        @SuppressWarnings("unchecked")
        Map<String, Object> sse = (Map<String, Object>) info.getDetails().get("sse");
        assertThat(sse)
                .containsEntry("basePath", "/custom-sse")
                .containsEntry("bridge", "NoOpBroadcastBridge")
                .containsEntry("clustered", false);

        assertThat(sse.get("version")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> stream = (Map<String, Object>) sse.get("stream");
        assertThat(stream)
                .containsEntry("heartbeatEnabled", true)
                .containsEntry("retryEnabled", true)
                .containsEntry("mapErrorsToSse", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) sse.get("metrics");
        assertThat(metrics)
                .containsEntry("enabled", true)
                .containsEntry("perTopic", true);
    }

    @Test
    void contributeWithClusteredBridge() {
        SseServerProperties properties = new SseServerProperties();
        SseBroadcastBridge mockBridge = mock(SseBroadcastBridge.class);

        SseInfoContributor contributor = new SseInfoContributor(properties, mockBridge);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);

        Info info = builder.build();
        @SuppressWarnings("unchecked")
        Map<String, Object> sse = (Map<String, Object>) info.getDetails().get("sse");
        assertThat(sse).containsEntry("clustered", true);
    }
}
