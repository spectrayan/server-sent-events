# Contributing to Spectrayan Server-Sent Events

Thanks for your interest in contributing! This repository hosts a polyglot, multi-module workspace for Server‑Sent Events (SSE):

- **Core Server**: `libs/sse-server` (Spring WebFlux SSE utilities auto-configured for Spring Boot)
- **Broadcast Bridges**:
  - `libs/sse-server-bridge-redis` (Redis Pub/Sub distributed fan-out)
  - `libs/sse-server-bridge-cloud-stream` (Spring Cloud Stream for Kafka, RabbitMQ, Pulsar)
  - `libs/sse-server-bridge-nats` (NATS high-performance messaging bridge)
- **Client Libraries**:
  - Angular: `libs/ng-sse-client` (Nx + Angular library)
  - Go: `clients/go` (idiomatic Go SSE client)
  - Kotlin / Android: `clients/kotlin` (Kotlin Coroutines `Flow<ServerSentEvent>`)
  - Python: `clients/python/spectrayan-sse-client` (Python asyncio client)
  - Swift: `clients/swift` (Swift 6 Concurrency & `AsyncSequence`)
- **Samples**: `samples/*`

Before you start, please read this guide to set up your environment, understand our governance, and follow the contribution workflow.

---

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Governance & Meritocracy](#governance--meritocracy)
- [Developer Certificate of Origin (DCO 1.1)](#developer-certificate-of-origin-dco-11)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Commit Style & Standards](#commit-style--standards)
- [Pull Request & Review Process](#pull-request--review-process)
- [Issue Triage](#issue-triage)
- [Release Process (Maintainers)](#release-process-maintainers)

---

## Code of Conduct

This project follows our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to abide by it.
If you witness or experience unacceptable behavior, contact: `support@spectrayan.com`.

---

## Governance & Meritocracy

This repository is governed as an open-source meritocracy under [GOVERNANCE.md](GOVERNANCE.md). 
- Project participants act in their individual capacity under defined open-source roles: *Project Lead*, *Technical Lead*, *Architecture Working Group*, *Maintainers*, *Committers*, and *Contributors*.
- Routine contributions operate under **Lazy Consensus** (72 hours without objection + 1 Maintainer approval).
- Active contributors who reach **3+ merged pull requests** are eligible for nomination to Tier 2 Committer / Reviewer status. See the Contributor Ladder in [GOVERNANCE.md](GOVERNANCE.md) §3.

---

## Developer Certificate of Origin (DCO 1.1)

To ensure legal integrity under the Apache License 2.0 while keeping contributions streamlined, all contributions require a **Developer Certificate of Origin (DCO 1.1)** sign-off trailer on every commit:

```bash
git commit -s -m "feat(scope): concise description"
```

This appends your identity:
```text
Signed-off-by: Full Name <user@example.com>
```

PRs lacking DCO sign-offs cannot be merged. See [DCO.md](DCO.md) for the full certificate text.

### How to Fix Unsigned Commits
If a commit on your pull request is missing the DCO trailer:

```bash
# For multiple commits on your branch:
git rebase --signoff origin/main
git push --force-with-lease

# For only the most recent commit:
git commit --amend -s --no-edit
git push --force-with-lease
```

---

## Getting Started

### Prerequisites:
- **Node.js 20+** and npm (for Nx + Angular library)
- **Java 21** and **Maven 3.9+** (for Spring libraries and samples)
- **Go 1.22+** (for Go client)
- **Python 3.10+** (for Python client)
- **Swift 6.0+** (for Swift client)
- **Git**

Install JavaScript dependencies:
```bash
make setup
```

Build and test everything locally:
```bash
make ci
```

---

## Development Workflow

Common targets:
- Build Angular library: `make build-ng`
- Test Angular library: `make test-ng`
- Maven verify (Java server & bridges): `make verify-mvn`
- Test Go client: `cd clients/go && go test -v -race ./...`
- Test Kotlin client: `cd clients/kotlin && ./gradlew test`
- Test Python client: `cd clients/python/spectrayan-sse-client && pytest`
- Test Swift client: `cd clients/swift && swift test`
- Clean artifacts: `make clean`

Running sample applications:
- See `samples/sse-sample-server-app/README.md`
- See `samples/ng-sse-client-app/README.md`

---

## Commit Style & Standards

- Follow Conventional Commits: `type(scope): imperative description`
  - Types: `feat`, `fix`, `docs`, `test`, `chore`, `refactor`, `perf`, `deps`
  - Scopes: `sse-server`, `bridge-redis`, `bridge-cloud-stream`, `bridge-nats`, `ng-sse-client`, `client-go`, `client-kotlin`, `client-python`, `client-swift`, `samples`
- Keep commits small, focused, and ordered logically.
- Always include `Signed-off-by:` via `git commit -s`.

---

## Pull Request & Review Process

1. Fork the repo and create your feature branch from `main`.
2. Ensure tests pass locally for the affected modules.
3. Open a Pull Request using the [PR Template](.github/PULL_REQUEST_TEMPLATE.md).
4. Automated CI will run module-specific test suites and verify DCO 1.1 sign-off.
5. Code reviews are conducted with a collaborative, coaching tone.
6. Once approved and 72 hours have elapsed under Lazy Consensus (or with explicit Maintainer approval), a Maintainer will squash-merge your PR into `main`.
7. First-time and substantial contributors will be celebrated in [ACKNOWLEDGMENTS.md](ACKNOWLEDGMENTS.md).

---

## Issue Triage

- When reporting bugs, provide minimal reproduction steps or link to a demonstration repository.
- Share your runtime environment (OS, language/runtime versions, Spring Boot version).
- Good first issues are labeled `good first issue` and `help wanted`.

---

## Release Process (Maintainers)

- Tagging a version with `vX.Y.Z` triggers automated release workflows:
  - npm publish: `libs/ng-sse-client`
  - Maven Central publish: `libs/sse-server` and bridges
  - PyPI publish: Python client
- See [GOVERNANCE.md](GOVERNANCE.md) §2.5 for release authority.

For any questions, reach out via GitHub Discussions or email `support@spectrayan.com`.
