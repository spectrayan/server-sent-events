# Spectrayan SSE — Server‑Sent Events toolkit

Spectrayan SSE is a multi-language toolkit for building Server‑Sent Events (SSE) systems:
- Angular client library: `libs/ng-sse-client` — a typed, zone-aware SSE client for Angular apps
- Spring WebFlux server library: `libs/sse-server` — an opinionated SSE emitter + auto-config for Spring Boot
- Samples: `samples/*` — runnable examples for both client and server

This repository uses Nx for the JavaScript workspace and Maven for Java modules.

## Contents
- Features
- Quickstart
- Local build & test (Makefile)
- Libraries
- Samples
- Releasing (maintainers)
- Contributing & support

## Features
- Angular client
  - Strongly-typed streams with `parse` hook
  - Automatic reconnection with backoff + jitter
  - Support for named events and `lastEventId` propagation
  - Runs outside Angular zone for performance and re-enters on emissions
- Spring WebFlux server
  - Simple `SseEmitter` service to emit payloads to topics (and broadcast)
  - Heartbeat events to keep connections alive
  - Auto-configured controller exposing `/{topic}` stream endpoints
  - Configurable headers and MDC propagation

## Quickstart
Prerequisites:
- Node.js 20+, npm
- Java 21, Maven 3.9+
- Git
- On Windows, use Git Bash or WSL to run `make`

Install dependencies:
```
make setup
```

Run the CI-equivalent build locally (Angular build/test + Maven verify):
```
make ci
```

## Local build & test (Makefile)
The Makefile mirrors the GitHub Actions workflow (`.github/workflows/libs-release.yml`). Common targets:
- `make setup` — npm ci
- `make build-ng` — build Angular library (`ng-sse-client`)
- `make test-ng` — unit tests for Angular library
- `make verify-mvn` — Maven verify for `libs/sse-server`
- `make build-all` — build Angular + Maven verify
- `make test-all` — test Angular + Maven verify
- `make clean` — clean JS and Java build artifacts
- Guarded release tasks (use with care): `make publish-npm`, `make publish-maven`

## Libraries
- Angular: see `libs/ng-sse-client/README.md` for installation, usage and API
- Spring WebFlux: see `libs/sse-server/README.md` for features and how to use in a Spring Boot app

## Samples
- `samples/ng-sse-client-app`: Angular app demonstrating consumption of an SSE endpoint
- `samples/sse-sample-server-app`: Spring Boot app emitting periodic events via `SseEmitter`
Each sample includes its own README with run instructions.

## Releasing (maintainers)
CI publishes on annotated tags `v*`:
- npm (Angular): built artifact at `dist/libs/ng-sse-client`
- Maven Central (Java): `libs/sse-server` with `-P release`
Local guarded commands exist: `make publish-npm`, `make publish-maven` (require credentials)

## Contributing & support
- Please read `CONTRIBUTING.md` and `CODE_OF_CONDUCT.md`
- Security issues: see `SECURITY.md`
- Questions, issues, or support: support@spectrayan.com
