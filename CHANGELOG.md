### Changelog

All notable changes to this project will be documented in this file.

#### [Unreleased]
- Router-only SSE endpoint: functional router registered at `${spectrayan.sse.server.base-path}/{topic}` (default `/sse/{topic}`).
- Introduced `SseExceptionHandler` (WebFlux global handler) producing RFC7807 Problem Details.
  - Configurable via `spectrayan.sse.server.errors.enabled` and `spectrayan.sse.server.errors.scope` (GLOBAL|SSE).
  - Custom mappings can be added through `SseWebFluxConfigurer#configureExceptionHandling`.
- New SPI: `SseWebFluxConfigurer` to customize codecs, CORS, headers, exception mappings, and contribute customizers.
- Properties:
  - New top-level `spectrayan.sse.server.base-path`.
  - New `spectrayan.sse.server.errors.*` section.
- CORS support enhanced: library registers a `CorsWebFilter` (opt-in) scoped to base path; user configurers can adjust.
- MDC bridge and header handling consolidated in `SseHeaderHandler`.
- Sample app:
  - Added `SampleSseWebFluxConfigurer` demonstrating custom exception mapping (400 for `IllegalArgumentException`) and CORS tweaks.
  - Added `DemoErrorController` at `GET /api/demo/error` to showcase ProblemDetails mapping.
- Documentation: Overhauled `libs/sse-server/README.md` to reflect router-only design and new extension points.

#### Breaking Changes
- Removed controller mode and nested `controller.*` properties.
- Replaced `GlobalExceptionHandler` with `SseExceptionHandler`.

#### Migration Notes
- Replace any `spectrayan.sse.server.controller.*` properties with `spectrayan.sse.server.base-path`.
- If you had custom exception handling, migrate to `SseWebFluxConfigurer#configureExceptionHandling` or provide your own `SseExceptionHandler` bean.
