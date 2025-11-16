package com.spectrayan.sse.server.config;

import com.spectrayan.sse.server.error.SseExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class SseServerAutoConfigurationMoreTests {

    private ApplicationContextRunner baseRunner(String... props) {
        ApplicationContextRunner r = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
                .withPropertyValues("spectrayan.sse.server.enabled=true");
        if (props != null && props.length > 0) {
            r = r.withPropertyValues(props);
        }
        return r;
    }

    @Test
    void exceptionHandlerBeanExistsWhenErrorsEnabledAndCodecsPresent() {
        baseRunner("spectrayan.sse.server.errors.enabled=true")
                .withUserConfiguration(TestCodecs.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SseExceptionHandler.class);
                });
    }

    @Test
    void reactorMdcConfigurationConditional() {
        baseRunner("spectrayan.sse.server.mdc-bridge-enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ReactorMdcConfiguration.class));

        baseRunner("spectrayan.sse.server.mdc-bridge-enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ReactorMdcConfiguration.class));
    }

    @Configuration
    static class TestCodecs {
        @Bean
        ServerCodecConfigurer serverCodecConfigurer() {
            return ServerCodecConfigurer.create();
        }
    }
}
