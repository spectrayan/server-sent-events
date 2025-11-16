package com.spectrayan.sse.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@org.springframework.boot.context.properties.EnableConfigurationProperties({
        com.spectrayan.sse.sample.config.AppCorsProperties.class
})
public class SseSampleServerApp {
    public static void main(String[] args) {
        SpringApplication.run(SseSampleServerApp.class, args);
    }
}
