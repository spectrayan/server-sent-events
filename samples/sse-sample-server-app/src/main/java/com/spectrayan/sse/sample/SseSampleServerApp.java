package com.spectrayan.sse.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SseSampleServerApp {
    public static void main(String[] args) {
        SpringApplication.run(SseSampleServerApp.class, args);
    }
}
