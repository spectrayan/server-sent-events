import Foundation
import Testing
@testable import SpectrayanSSE

// MARK: - URLProtocol stub

/// Canned response for one request. `chunks` are delivered as separate `Data` callbacks.
struct StubResponse: Sendable {
    var status = 200
    var headers = ["Content-Type": "text/event-stream"]
    var chunks: [String] = []
    var error: URLError?
    /// Leave the connection open after sending `chunks`, until the client cancels it.
    var keepOpen = false
}

/// Serves stubs keyed by URL host so that tests can run in parallel.
final class StubProtocol: URLProtocol, @unchecked Sendable {
    private struct Route {
        let respond: @Sendable (_ attempt: Int) -> StubResponse
        var requests: [URLRequest] = []
        var stopped = 0
    }

    private static let lock = NSLock()
    nonisolated(unsafe) private static var routes: [String: Route] = [:]

    static func register(host: String, respond: @escaping @Sendable (_ attempt: Int) -> StubResponse) {
        lock.withLock { routes[host] = Route(respond: respond) }
    }

    static func requests(host: String) -> [URLRequest] {
        lock.withLock { routes[host]?.requests ?? [] }
    }

    static func stopCount(host: String) -> Int {
        lock.withLock { routes[host]?.stopped ?? 0 }
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let host = request.url!.host!
        let response: StubResponse? = Self.lock.withLock {
            guard var route = Self.routes[host] else { return nil }
            let attempt = route.requests.count
            route.requests.append(request)
            Self.routes[host] = route
            return route.respond(attempt)
        }
        guard let response, let client else { return }

        if let error = response.error {
            client.urlProtocol(self, didFailWithError: error)
            return
        }
        let http = HTTPURLResponse(url: request.url!, statusCode: response.status, httpVersion: "HTTP/1.1", headerFields: response.headers)!
        client.urlProtocol(self, didReceive: http, cacheStoragePolicy: .notAllowed)
        for chunk in response.chunks {
            client.urlProtocol(self, didLoad: Data(chunk.utf8))
        }
        if !response.keepOpen {
            client.urlProtocolDidFinishLoading(self)
        }
    }

    override func stopLoading() {
        let host = request.url!.host!
        Self.lock.withLock { Self.routes[host]?.stopped += 1 }
    }
}

// MARK: - Helpers

private let fastPolicy = ReconnectionPolicy(initialDelayMilliseconds: 1, maxDelayMilliseconds: 1)

/// Registers a stub under a unique host and returns a client pointed at it.
private func makeClient(
    configuration: SSEClientConfiguration = SSEClientConfiguration(reconnectionPolicy: fastPolicy),
    respond: @escaping @Sendable (_ attempt: Int) -> StubResponse
) -> (SpectrayanSSEClient, host: String) {
    let host = "stub-\(UUID().uuidString.lowercased()).test"
    StubProtocol.register(host: host, respond: respond)
    let session = URLSessionConfiguration.ephemeral
    session.protocolClasses = [StubProtocol.self]
    let client = SpectrayanSSEClient(url: URL(string: "https://\(host)/events")!, configuration: configuration, sessionConfiguration: session)
    return (client, host)
}

func collect<S: AsyncSequence>(_ sequence: S, limit: Int) async throws -> [S.Element] {
    var items: [S.Element] = []
    for try await item in sequence {
        items.append(item)
        if items.count == limit { break }
    }
    return items
}

func waitUntil(timeoutSeconds: TimeInterval = 2, _ condition: () -> Bool) async -> Bool {
    let deadline = Date().addingTimeInterval(timeoutSeconds)
    while !condition() {
        if Date() > deadline { return false }
        try? await Task.sleep(nanoseconds: 5_000_000)
    }
    return true
}

// MARK: - Client tests

@Suite("SpectrayanSSEClient")
struct SpectrayanSSEClientTests {

    @Test func receivesEventsAcrossChunks() async throws {
        let (client, _) = makeClient { _ in
            StubResponse(chunks: ["id: 1\nevent: tok", "en\ndata: hel", "lo\n\ndata: wor", "ld\n\n"], keepOpen: true)
        }
        let events = try await collect(client.events, limit: 2)
        #expect(events == [
            ServerSentEvent(id: "1", event: "token", data: "hello"),
            ServerSentEvent(id: "1", data: "world"),
        ])
    }

    @Test func sendsConfiguredRequest() async throws {
        let configuration = SSEClientConfiguration(
            method: "POST",
            headers: ["Authorization": "Bearer secret", "Accept": "overridden"],
            lastEventId: "41",
            reconnectionPolicy: fastPolicy
        )
        let (client, host) = makeClient(configuration: configuration) { _ in
            StubResponse(chunks: ["data: x\n\n"], keepOpen: true)
        }
        _ = try await collect(client.events, limit: 1)

        let request = try #require(StubProtocol.requests(host: host).first)
        #expect(request.httpMethod == "POST")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer secret")
        #expect(request.value(forHTTPHeaderField: "Accept") == "text/event-stream")
        #expect(request.value(forHTTPHeaderField: "Cache-Control") == "no-cache")
        #expect(request.value(forHTTPHeaderField: "Last-Event-ID") == "41")
    }

    @Test func reconnectsAfterCloseWithLastEventId() async throws {
        let (client, host) = makeClient { attempt in
            attempt == 0
                ? StubResponse(chunks: ["retry: 1\nid: 7\ndata: first\n\nid: 8\ndata: incomplete"])
                : StubResponse(chunks: ["data: second\n\n"], keepOpen: true)
        }
        let events = try await collect(client.events, limit: 2)
        #expect(events.map(\.data) == ["first", "second"])
        #expect(events.map(\.id) == ["7", "7"])

        let requests = StubProtocol.requests(host: host)
        #expect(requests.count == 2)
        #expect(requests[0].value(forHTTPHeaderField: "Last-Event-ID") == nil)
        #expect(requests[1].value(forHTTPHeaderField: "Last-Event-ID") == "7")
    }

    @Test func retriesNetworkErrors() async throws {
        let (client, host) = makeClient { attempt in
            attempt < 2
                ? StubResponse(error: URLError(.networkConnectionLost))
                : StubResponse(chunks: ["data: recovered\n\n"], keepOpen: true)
        }
        #expect(try await collect(client.events, limit: 1).map(\.data) == ["recovered"])
        #expect(StubProtocol.requests(host: host).count == 3)
    }

    @Test func noContentEndsStreamWithoutReconnecting() async throws {
        let (client, host) = makeClient { _ in StubResponse(status: 204, headers: [:]) }
        #expect(try await collect(client.events, limit: 10).isEmpty)
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test(arguments: [400, 401, 403, 404])
    func clientErrorsFailImmediately(status: Int) async {
        let (client, host) = makeClient { _ in StubResponse(status: status) }
        await #expect(throws: SSEClientError.unexpectedStatusCode(status)) {
            _ = try await collect(client.events, limit: 10)
        }
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func invalidContentTypeFailsImmediately() async {
        let (client, host) = makeClient { _ in
            StubResponse(headers: ["Content-Type": "application/json"], chunks: ["{}"])
        }
        await #expect(throws: SSEClientError.invalidContentType("application/json")) {
            _ = try await collect(client.events, limit: 10)
        }
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func contentTypeParametersAreAccepted() async throws {
        let (client, _) = makeClient { _ in
            StubResponse(headers: ["Content-Type": "Text/Event-Stream; charset=utf-8"], chunks: ["data: x\n\n"], keepOpen: true)
        }
        #expect(try await collect(client.events, limit: 1).map(\.data) == ["x"])
    }

    @Test func serverErrorsAreRetriedUntilMaxRetries() async {
        let policy = ReconnectionPolicy(initialDelayMilliseconds: 1, maxDelayMilliseconds: 1, maxRetries: 2)
        let (client, host) = makeClient(configuration: SSEClientConfiguration(reconnectionPolicy: policy)) { _ in
            StubResponse(status: 503)
        }
        await #expect(throws: SSEClientError.unexpectedStatusCode(503)) {
            _ = try await collect(client.events, limit: 10)
        }
        #expect(StubProtocol.requests(host: host).count == 3)
    }

    @Test func cleanCloseWithReconnectionDisabledFinishesNormally() async throws {
        let policy = ReconnectionPolicy(initialDelayMilliseconds: 1, maxDelayMilliseconds: 1, maxRetries: 0)
        let (client, host) = makeClient(configuration: SSEClientConfiguration(reconnectionPolicy: policy)) { _ in
            StubResponse(chunks: ["data: only\n\n"])
        }
        #expect(try await collect(client.events, limit: 10).map(\.data) == ["only"])
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func filtersByEventType() async throws {
        let (client, _) = makeClient { _ in
            StubResponse(chunks: ["event: a\ndata: 1\n\nevent: b\ndata: 2\n\nevent: a\ndata: 3\n\n"], keepOpen: true)
        }
        #expect(try await collect(client.events(ofType: "a"), limit: 2).map(\.data) == ["1", "3"])
    }

    @Test func stoppingIterationClosesConnection() async throws {
        let (client, host) = makeClient { _ in
            StubResponse(chunks: ["data: x\n\n"], keepOpen: true)
        }
        _ = try await collect(client.events, limit: 1)
        #expect(await waitUntil { StubProtocol.stopCount(host: host) == 1 })
        try await Task.sleep(nanoseconds: 50_000_000)
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func cancellingTaskClosesConnection() async throws {
        let (client, host) = makeClient { _ in StubResponse(keepOpen: true) }
        let task = Task {
            for try await _ in client.events {}
        }
        #expect(await waitUntil { StubProtocol.requests(host: host).count == 1 })
        task.cancel()
        _ = await task.result
        #expect(await waitUntil { StubProtocol.stopCount(host: host) == 1 })
    }

    @Test func eachIterationOpensItsOwnConnection() async throws {
        let (client, host) = makeClient { _ in StubResponse(chunks: ["data: x\n\n"], keepOpen: true) }
        let events = client.events
        #expect(StubProtocol.requests(host: host).isEmpty)
        _ = try await collect(events, limit: 1)
        _ = try await collect(events, limit: 1)
        #expect(StubProtocol.requests(host: host).count == 2)
    }
}

// MARK: - Retry classification

@Suite("Retry classification")
struct RetryClassificationTests {

    @Test(arguments: [408, 429, 500, 502, 503, 599])
    func retryableStatuses(status: Int) {
        #expect(SpectrayanSSEClient.isRetryable(SSEClientError.unexpectedStatusCode(status)))
    }

    @Test(arguments: [300, 400, 401, 404, 410])
    func nonRetryableStatuses(status: Int) {
        #expect(!SpectrayanSSEClient.isRetryable(SSEClientError.unexpectedStatusCode(status)))
    }

    @Test func invalidResponsesAreNotRetried() {
        #expect(!SpectrayanSSEClient.isRetryable(SSEClientError.invalidContentType("text/html")))
        #expect(!SpectrayanSSEClient.isRetryable(SSEClientError.nonHTTPResponse))
    }

    @Test(arguments: [URLError.Code.timedOut, .networkConnectionLost, .notConnectedToInternet, .cannotConnectToHost, .dnsLookupFailed])
    func transientURLErrorsAreRetried(code: URLError.Code) {
        #expect(SpectrayanSSEClient.isRetryable(URLError(code)))
    }

    @Test(arguments: [URLError.Code.cancelled, .badURL, .unsupportedURL, .serverCertificateUntrusted, .appTransportSecurityRequiresSecureConnection])
    func permanentURLErrorsAreNotRetried(code: URLError.Code) {
        #expect(!SpectrayanSSEClient.isRetryable(URLError(code)))
    }
}

// MARK: - Backoff

@Suite("Backoff with full jitter")
struct BackoffTests {
    let policy = ReconnectionPolicy(initialDelayMilliseconds: 1_000, maxDelayMilliseconds: 30_000, multiplier: 2)

    @Test(arguments: [(0, 1_000), (1, 2_000), (2, 4_000), (4, 16_000), (5, 30_000), (100, 30_000), (10_000, 30_000)])
    func upperBoundGrowsExponentiallyUntilCap(attempt: Int, expected: Int) {
        #expect(policy.delayMilliseconds(forAttempt: attempt, randomFactor: 1) == expected)
    }

    @Test func jitterScalesBetweenZeroAndUpperBound() {
        #expect(policy.delayMilliseconds(forAttempt: 2, randomFactor: 0) == 0)
        #expect(policy.delayMilliseconds(forAttempt: 2, randomFactor: 0.25) == 1_000)
    }

    @Test func randomDelaysStayInRange() {
        for attempt in 0..<10 {
            let upper = policy.delayMilliseconds(forAttempt: attempt, randomFactor: 1)
            for _ in 0..<50 {
                #expect((0...upper).contains(policy.delayMilliseconds(forAttempt: attempt)))
            }
        }
    }

    @Test func serverRetryReplacesBaseAndRaisesCap() {
        #expect(policy.delayMilliseconds(forAttempt: 0, serverDelayMilliseconds: 5_000, randomFactor: 1) == 5_000)
        #expect(policy.delayMilliseconds(forAttempt: 1, serverDelayMilliseconds: 5_000, randomFactor: 1) == 10_000)
        #expect(policy.delayMilliseconds(forAttempt: 0, serverDelayMilliseconds: 60_000, randomFactor: 1) == 60_000)
        #expect(policy.delayMilliseconds(forAttempt: 3, serverDelayMilliseconds: 60_000, randomFactor: 1) == 60_000)
    }

    @Test func outOfRangeInputsAreClamped() {
        #expect(policy.delayMilliseconds(forAttempt: -1, randomFactor: 1) == 1_000)
        #expect(policy.delayMilliseconds(forAttempt: 1, randomFactor: 5) == 2_000)
        #expect(policy.delayMilliseconds(forAttempt: 1, randomFactor: -1) == 0)
        let shrinking = ReconnectionPolicy(initialDelayMilliseconds: 1_000, multiplier: 0.5)
        #expect(shrinking.delayMilliseconds(forAttempt: 3, randomFactor: 1) == 1_000)
    }
}
