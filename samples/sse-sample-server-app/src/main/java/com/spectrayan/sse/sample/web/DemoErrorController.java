package com.spectrayan.sse.sample.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Small demo controller to intentionally trigger an error so you can see
 * how SseExceptionHandler and the SampleSseWebFluxConfigurer mapping work.
 *
 * Try: GET /api/demo/error
 */
@RestController
@RequestMapping(path = "/api/demo", produces = MediaType.APPLICATION_JSON_VALUE)
public class DemoErrorController {

    private static final Logger log = LoggerFactory.getLogger(DemoErrorController.class);

    @GetMapping("/error")
    public String error() {
        log.info("Demo error endpoint invoked; throwing IllegalArgumentException to demonstrate ProblemDetails mapping");
        throw new IllegalArgumentException("Example invalid argument from demo endpoint");
    }
}
