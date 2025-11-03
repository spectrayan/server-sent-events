# Makefile — local equivalent of .github/workflows/libs-release.yml
# Requires: Node.js 20+, npm, Git, Java 21, Maven 3.9+
# On Windows, use Git Bash or WSL to run `make`.

# Default target
.DEFAULT_GOAL := help

# Tools
NX := npx nx
MVN := mvn -B
NPM := npm

# Paths
NG_DIST := dist/libs/ng-sse-client
MVN_MODULE := libs/sse-server

.PHONY: help setup ci build-ng test-ng build-mvn test-mvn verify-mvn build-all test-all lint clean publish-npm publish-maven release-dryrun

help:
	@echo "Available targets:"
	@echo "  setup           - Install JS dependencies (npm ci)"
	@echo "  ci              - Run the same build/test steps as CI (Angular + Maven verify)"
	@echo "  build-ng        - Build Angular library (ng-sse-client)"
	@echo "  test-ng         - Run unit tests for Angular library"
	@echo "  build-mvn       - Build Java library (without tests)"
	@echo "  test-mvn        - Run Maven tests/verify for Java library"
	@echo "  verify-mvn      - Same as CI: mvn verify for libs/sse-server"
	@echo "  build-all       - Build Angular lib and run Maven verify"
	@echo "  test-all        - Test Angular lib and run Maven verify"
	@echo "  lint            - Lint workspace (if configured)"
	@echo "  clean           - Remove build artifacts"
	@echo "  publish-npm     - Publish Angular package from $(NG_DIST) (requires NPM_TOKEN)"
	@echo "  publish-maven   - Deploy Java library with '-P release' (requires OSSRH_* and GPG_* env vars)"
	@echo "  release-dryrun  - Build both libs in release-like mode without publishing"

setup:
	$(NPM) ci

ci: build-ng test-ng verify-mvn

build-ng:
	$(NX) build ng-sse-client

# --ci mirrors the GH workflow behavior; no coverage by default to keep it fast
test-ng:
	$(NX) test ng-sse-client --ci --codeCoverage=false

# Build Java lib without tests (package only). Useful for local iteration.
build-mvn:
	$(MVN) -pl $(MVN_MODULE) -DskipTests package

# Run full Maven lifecycle verify with tests (matches CI step)
verify-mvn test-mvn:
	$(MVN) -U -pl $(MVN_MODULE) verify

build-all: build-ng verify-mvn

test-all: test-ng verify-mvn

lint:
	$(NX) run-many -t lint || true

clean:
	rm -rf dist coverage
	find libs -name target -type d -prune -exec rm -rf {} + 2>/dev/null || true
	find samples -name target -type d -prune -exec rm -rf {} + 2>/dev/null || true

# --- Publishing (guarded) ---
# Note: These commands will publish REAL artifacts if creds are set. Use with care.
# NPM publish mirrors the GitHub Action: build first, then publish from dist/libs/ng-sse-client.
publish-npm: build-ng
	@if [ -z "$$NPM_TOKEN" ]; then \
		echo "ERROR: NPM_TOKEN not set. Aborting npm publish."; exit 1; \
	fi
	cd $(NG_DIST) && \
	echo "//registry.npmjs.org/:_authToken=$$NPM_TOKEN" > .npmrc && \
	$(NPM) publish --access public
	@echo "Published npm package from $(NG_DIST)"

# Maven Central publish (OSSRH) – mirrors CI. Requires env vars:
#   OSSRH_USERNAME, OSSRH_PASSWORD, GPG_PASSPHRASE and GPG key available in keyring.
publish-maven:
	@if [ -z "$$OSSRH_USERNAME" ] || [ -z "$$OSSRH_PASSWORD" ] || [ -z "$$GPG_PASSPHRASE" ]; then \
		echo "ERROR: Set OSSRH_USERNAME, OSSRH_PASSWORD, and GPG_PASSPHRASE before publishing."; exit 1; \
	fi
	$(MVN) -pl $(MVN_MODULE) -P release deploy

# Build both libraries like a tagged release, but do NOT publish.
release-dryrun: build-ng verify-mvn
	@echo "Release dry run completed (no artifacts published)."
