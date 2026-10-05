# Acknowledgments

The Server-Sent Events project stands on the shoulders of an active open-source community, reactive systems research, and modern real-time streaming standards. This file gratefully credits the human contributors, frameworks, and tools whose work shaped the project.

If you believe something here is mis-attributed or missing, please open an issue or pull request — it will be updated promptly.

---

## Open Source Contributors

This project is built in collaboration with a global community of developers. We gratefully recognize and celebrate the contributors whose commits, pull requests, and feedback drive this ecosystem forward:

### Subsystem & Client Contributors

- **Timothy Kim ([@timothytkim](https://github.com/timothytkim))**
  - PR #69: Authored the complete, idiomatic Swift SSE client (`clients/swift`), bringing W3C streaming byte parsing, Swift 6 strict concurrency (`AsyncThrowingStream`), and resilient jittered reconnection to iOS, macOS, watchOS, tvOS, and visionOS (resolving #59).

---

## Open-Source Frameworks & Standards

The Server-Sent Events toolkit is built upon and inspired by industry-standard reactive and streaming specifications:

| Technology | Usage | License |
|:---|:---|:---|
| [W3C Server-Sent Events](https://html.spec.whatwg.org/multipage/server-sent-events.html) | Protocol standard for server-to-client unidirectional push | W3C / WHATWG |
| [Spring WebFlux & Project Reactor](https://spring.io/projects/spring-framework) | Reactive non-blocking I/O foundation for `sse-server` | Apache-2.0 |
| [Spring Boot](https://spring.io/projects/spring-boot) | Auto-configuration and starter infrastructure | Apache-2.0 |
| [Angular](https://angular.dev/) | Client library (`ng-sse-client`) and demonstration frontend | MIT |
| [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream) | Multi-pod event broadcasting bridge (`sse-server-bridge-cloud-stream`) | Apache-2.0 |
| [Redis Pub/Sub](https://redis.io/) | Lightweight distributed multi-pod fan-out (`sse-server-bridge-redis`) | RSALv2 / SSPLv1 |
| [NATS](https://nats.io/) | High-performance distributed messaging bridge (`sse-server-bridge-nats`) | Apache-2.0 |
| [Nx](https://nx.dev/) | Smart monorepo build system and Angular library tooling | MIT |
| [Swift Package Manager](https://swift.org/package-manager/) | Distribution and testing for the Swift client SDK | Apache-2.0 |

---

## AI Coding Agents & Tooling

Development and governance across this repository are accelerated by modern agentic development tools:

- **[Antigravity](https://deepmind.google/)** (Google DeepMind) — AI coding assistant used for architectural modeling, test suites, multi-language client alignment, and governance automation.

---

*If you have contributed to the Server-Sent Events ecosystem and are not listed here, please [open a pull request](https://github.com/spectrayan/server-sent-events/pulls) — we want to make sure every contributor is celebrated.*
