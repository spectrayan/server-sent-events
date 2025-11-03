# Contributing to Spectrayan SSE

Thanks for your interest in contributing! This repository hosts a multi-language workspace for Server‑Sent Events (SSE):
- Angular library: `libs/ng-sse-client`
- Java Spring WebFlux library: `libs/sse-server`
- Samples: `samples/*`

Before you start, please read this guide to set up your environment and understand the workflow.

## Table of contents
- Code of Conduct
- Getting started
- Development workflow
- Project layout
- Commit style and branches
- Pull requests
- Issue triage
- Release process (maintainers)

## Code of Conduct
This project follows our Code of Conduct. By participating, you agree to abide by it.
If you witness or experience unacceptable behavior, contact: support@spectrayan.com

## Getting started
Prerequisites:
- Node.js 20+ and npm (for Nx + Angular library)
- Java 21 and Maven 3.9+ (for Spring library and sample)
- Git
- On Windows, use Git Bash or WSL for running Makefile targets

Install JavaScript dependencies:
```
make setup
```

Build and test everything locally (equivalent to CI):
```
make ci
```

## Development workflow
Common tasks:
- Build Angular lib: `make build-ng`
- Test Angular lib: `make test-ng`
- Maven verify (Java lib): `make verify-mvn`
- Lint (if configured): `make lint`
- Clean artifacts: `make clean`

Run sample server (after building/installing modules as needed):
- See `samples/sse-sample-server-app/README.md`
- See `samples/ng-sse-client-app/README.md`

## Project layout
- `libs/ng-sse-client`: Angular SSE client; built with Nx and ng-packagr
- `libs/sse-server`: Spring WebFlux SSE utilities, auto-configured for Spring Boot
- `samples/ng-sse-client-app`: Example Angular app consuming SSE
- `samples/sse-sample-server-app`: Example Spring Boot app emitting SSE

## Commit style and branches
- Small, focused commits
- Conventional Commit style appreciated (e.g., `feat:`, `fix:`, `docs:`, `chore:`)
- Create feature branches from `main`

## Pull requests
- Discuss large changes in an issue first
- Include tests where practical
- Update docs and READMEs when behavior or public APIs change
- Ensure `make ci` passes locally before opening PR

## Issue triage
- Provide minimal reproduction steps or link to a demo
- Share environment info (OS, Node, Java, Angular, Spring versions)
- Logs and stack traces help a lot

## Release process (maintainers)
- Tag version with `vX.Y.Z` to trigger CI publish jobs
- npm publish: `libs/ng-sse-client` (built artifact under `dist/libs/ng-sse-client`)
- Maven Central publish: `libs/sse-server` using `-P release` and required OSSRH/GPG creds
- For local/manual release, guarded Make targets exist: `make publish-npm`, `make publish-maven`

For questions, reach us at: support@spectrayan.com
