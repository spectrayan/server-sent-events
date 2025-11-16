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

### Versioning Strategy

We follow a Spring-inspired versioning lifecycle for the Java libraries (`libs/sse-server`):

1. **SNAPSHOT** (`1.0.0-SNAPSHOT`)  
   - Active development version  
   - Published to [OSSRH Snapshots](https://s01.oss.sonatype.org/content/repositories/snapshots/)  
   - Can be overwritten multiple times  
   - Use profile: `-P snapshot`

2. **Milestones** (`1.0.0-M1`, `1.0.0-M2`, ...)  
   - Early preview releases  
   - Immutable once published  
   - Not production-ready  
   - Published to Maven Central via Central Publishing

3. **Release Candidates** (`1.0.0-RC1`, `1.0.0-RC2`, ...)  
   - Near-final, feature-complete  
   - Only critical fixes expected  
   - Published to Maven Central via Central Publishing

4. **GA/Release** (`1.0.0`)  
   - Production-ready, stable release  
   - Published to Maven Central via Central Publishing  
   - Use profile: `-P release`

Angular packages (`ng-sse-client`) follow standard npm semantic versioning and are published to npm on Git tags.

---

### Prerequisites for Releasing

This section documents how to publish the Java library (`libs/sse-server`) to OSSRH Snapshots and Maven Central using the Maven Release Plugin + Central Publishing, and what’s required beforehand.

#### 1) GPG key (required for Maven Central)

Maven Central requires artifacts to be signed.

- Install GnuPG (gpg) and generate a key pair (use the same name/email as your Git identity):
  ```bash
  gpg --full-generate-key
  # Choose: RSA and RSA (default), 4096 bits, non-expiring (or set an expiry), add passphrase
  ```
  List your secret key ID (last 16 hex chars):
  ```bash
  gpg --list-secret-keys --keyid-format=long
  # Example: sec   rsa4096/ABCDEF1234567890 2025-01-01
  ```
- Export and back up your public key (optional but recommended):
  ```bash
  gpg --armor --export ABCDEF1234567890 > public.key.asc
  ```

Environment variable used by the build:
- `GPG_PASSPHRASE` — your key’s passphrase (required when signing in headless CI and locally due to `--pinentry-mode loopback`).

You may also need to configure `~/.gnupg/gpg-agent.conf` with `allow-loopback-pinentry` and restart the agent:
```bash
echo 'allow-loopback-pinentry' >> ~/.gnupg/gpg-agent.conf
gpgconf --kill gpg-agent
```

#### 2) Maven settings.xml (servers + GPG)

Create/update `~/.m2/settings.xml` with the servers used by this repo:

```xml
<settings>
  <servers>
    <!-- Sonatype Central Publishing (token credentials) -->
    <server>
      <id>central</id>
      <username>${env.CENTRAL_USERNAME}</username>
      <password>${env.CENTRAL_PASSWORD}</password>
    </server>

    <!-- OSSRH Snapshots repository -->
    <server>
      <id>ossrh-snapshots</id>
      <username>${env.OSSRH_USERNAME}</username>
      <password>${env.OSSRH_PASSWORD}</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>gpg</id>
      <properties>
        <!-- Optional: if you maintain multiple keys, set signing key id explicitly -->
        <!-- <gpg.keyname>ABCDEF1234567890</gpg.keyname> -->
        <gpg.passphrase>${env.GPG_PASSPHRASE}</gpg.passphrase>
      </properties>
    </profile>
  </profiles>

  <activeProfiles>
    <activeProfile>gpg</activeProfile>
  </activeProfiles>
</settings>
```

Required environment variables (export in your shell or configure in CI):
- `GPG_PASSPHRASE`
- `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (Sonatype Central Publishing token credentials)
- `OSSRH_USERNAME`, `OSSRH_PASSWORD` (OSSRH account for snapshots)

#### 3) Git and build hygiene
- Ensure a clean working tree (no uncommitted changes).
- Ensure you are on `main` and up to date with `origin/main`.
- Java 21 and Maven 3.9+ installed.

---

### Maven commands (Java library)

The parent POM is configured with:
- `maven-release-plugin` — to manage versioning, tagging, and performing the release
- `central-publishing-maven-plugin` — to publish to Maven Central
- GPG signing during `deploy` phase

General rules:
- Always pass the appropriate profile with `-P` to select the target channel.
- Set both the release version and the next development version explicitly.
- For local runs while testing the release process, prefer “safe” flags so your working copy and remote aren’t changed automatically (see below).

#### Local release (safe defaults)

When running the Maven Release Plugin locally for milestones/RCs/GA, use these flags to avoid mutating your local working copy or pushing to the remote:

```
-DlocalCheckout=true \
-DupdateWorkingCopyVersions=false \
-DpushChanges=false
```

Notes:
- You do NOT need to run a separate `deploy` command during release. The plugin is already configured with `<goals>clean deploy central-publishing:publish</goals>`, so `release:perform` will build, sign, deploy, and trigger Central Publishing.
- In CI or when you actually want tags/commits to be created and pushed, omit the above flags so the release plugin can push changes.

#### A) Publish a SNAPSHOT (OSSRH Snapshots)
Use for ongoing development between milestones/RCs.
```bash
mvn -P snapshot -DskipTests=false clean deploy
```

#### B) Milestone release (e.g., 1.0.0-M1)
```bash
mvn -P milestone \
    release:prepare \
    release:perform \
    -DreleaseVersion=1.0.0-M1 \
    -DdevelopmentVersion=1.0.1-SNAPSHOT \
    -DlocalCheckout=true -DupdateWorkingCopyVersions=false -DpushChanges=false
```

#### C) Release Candidate (e.g., 1.0.0-RC1)
```bash
mvn -P rc \
    release:prepare \
    release:perform \
    -DreleaseVersion=1.0.0-RC1 \
    -DdevelopmentVersion=1.0.1-SNAPSHOT \
    -DlocalCheckout=true -DupdateWorkingCopyVersions=false -DpushChanges=false
```

#### D) GA / Final release (e.g., 1.0.0)
```bash
mvn -P release \
    release:prepare \
    release:perform \
    -DreleaseVersion=1.0.0 \
    -DdevelopmentVersion=1.0.1-SNAPSHOT \
    -DlocalCheckout=true -DupdateWorkingCopyVersions=false -DpushChanges=false
```

Tips:
- Dry run without pushing tags/commits:
  ```bash
  mvn -DdryRun=true -P milestone release:prepare
  ```
- If `release:prepare` fails, roll back changes:
  ```bash
  mvn release:rollback
  git reset --hard
  git clean -fd
  ```

After `release:perform`, Central Publishing may require a manual review/approve step depending on your account settings. Check Sonatype Central portal if the release is not visible after a few minutes.

---

### GitHub Actions workflows for releasing (Java)

Two dedicated workflows complement local Maven commands:

- `.github/workflows/libs-snapshot.yml`
  - Triggers: on push to `main` and manual dispatch
  - What it does: builds/tests `libs/sse-server` and deploys `-SNAPSHOT` versions to OSSRH Snapshots using profile `-P snapshot`
  - Secrets used: `OSSRH_USERNAME`, `OSSRH_PASSWORD` (mapped to the `ossrh-snapshots` server in Maven settings)

- `.github/workflows/libs-milestone.yml`
  - Trigger: manual dispatch only, with inputs: `channel` (`milestone` | `rc` | `release`), `releaseVersion`, `developmentVersion`, and optional `dryRun`
  - What it does: runs the Maven Release Plugin with the selected profile and performs Central Publishing (no separate deploy step needed thanks to `<goals>clean deploy central-publishing:publish</goals>`)
  - Secrets used: same as existing release workflow (Central Publishing): `MAVEN_SERVER_USERNAME`, `MAVEN_SERVER_PASSWORD`, `GPG_PRIVATE_KEY`, `MAVEN_GPG_PASSPHRASE`

Note: npm publishing for the Angular client remains handled by tags via the existing `libs-release.yml` workflow and is intentionally excluded from the snapshot/milestone workflows.

---

### npm package (Angular client)

Publishing the Angular library (`libs/ng-sse-client`) is orchestrated by Nx and npm when a Git tag matching the npm version is created. For a manual publish (maintainers only):

```bash
# Ensure you are logged in to npm with publish rights
npm whoami

# Build the library
npm run build ng-sse-client

# From the built dist directory, publish
(cd dist/libs/ng-sse-client && npm publish --access public)
```

## Contributing & support
- Please read `CONTRIBUTING.md` and `CODE_OF_CONDUCT.md`
- Security issues: see `SECURITY.md`
- Questions, issues, or support: support@spectrayan.com
