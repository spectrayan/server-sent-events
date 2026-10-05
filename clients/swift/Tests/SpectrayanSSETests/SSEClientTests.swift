import Foundation
import Testing
@testable import SpectrayanSSE

private let fastPolicy = ReconnectionPolicy(initialDelayMilliseconds: 1, maxDelayMilliseconds: 1)

/// Registers a stub under a unique host and returns an `SSEClient` pointed at it.
private func makeSSEClient(
    reconnectionPolicy: ReconnectionPolicy = fastPolicy,
    respond: @escaping @Sendable (_ attempt: Int) -> StubResponse
) -> (SSEClient, host: String) {
    let host = "sse-\(UUID().uuidString.lowercased()).test"
    StubProtocol.register(host: host, respond: respond)
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [StubProtocol.self]
    let client = SSEClient(
        url: URL(string: "https://\(host)/events")!,
        session: URLSession(configuration: configuration),
        reconnectionPolicy: reconnectionPolicy
    )
    return (client, host)
}

@Suite("SSEClient")
struct SSEClientTests {

    // MARK: Delivery

    @Test func receivesEventsAcrossChunksAndSendsHeaders() async throws {
        let (client, host) = makeSSEClient { _ in
            StubResponse(chunks: ["id: 1\nevent: tok", "en\ndata: hel", "lo\n\ndata: wor", "ld\n\n"], keepOpen: true)
        }
        let events = try await collect(client.events(), limit: 2)
        #expect(events == [
            ServerSentEvent(id: "1", event: "token", data: "hello"),
            ServerSentEvent(id: "1", data: "world"),
        ])

        let request = try #require(StubProtocol.requests(host: host).first)
        #expect(request.httpMethod == "GET")
        #expect(request.value(forHTTPHeaderField: "Accept") == "text/event-stream")
        #expect(request.value(forHTTPHeaderField: "Cache-Control") == "no-cache")
        #expect(request.value(forHTTPHeaderField: "Last-Event-ID") == nil)
    }

    @Test func contentTypeParametersAreAccepted() async throws {
        let (client, _) = makeSSEClient { _ in
            StubResponse(headers: ["Content-Type": "Text/Event-Stream; charset=utf-8"], chunks: ["data: x\n\n"], keepOpen: true)
        }
        #expect(try await collect(client.events(), limit: 1).map(\.data) == ["x"])
    }

    // MARK: Reconnection

    @Test func reconnectsAfterCloseWithLastEventId() async throws {
        let (client, host) = makeSSEClient { attempt in
            attempt == 0
                ? StubResponse(chunks: ["retry: 1\nid: 7\ndata: first\n\nid: 8\ndata: incomplete"])
                : StubResponse(chunks: ["data: second\n\n"], keepOpen: true)
        }
        let events = try await collect(client.events(), limit: 2)
        #expect(events.map(\.data) == ["first", "second"])
        #expect(events.map(\.id) == ["7", "7"])
        #expect(StubProtocol.requests(host: host).map { $0.value(forHTTPHeaderField: "Last-Event-ID") } == [nil, "7"])
    }

    @Test func retriesNetworkErrors() async throws {
        let (client, host) = makeSSEClient { attempt in
            attempt < 2
                ? StubResponse(error: URLError(.networkConnectionLost))
                : StubResponse(chunks: ["data: recovered\n\n"], keepOpen: true)
        }
        #expect(try await collect(client.events(), limit: 1).map(\.data) == ["recovered"])
        #expect(StubProtocol.requests(host: host).count == 3)
    }

    @Test func serverErrorsAreRetriedUntilMaxRetries() async {
        let policy = ReconnectionPolicy(initialDelayMilliseconds: 1, maxDelayMilliseconds: 1, maxRetries: 2)
        let (client, host) = makeSSEClient(reconnectionPolicy: policy) { _ in StubResponse(status: 503) }
        await #expect(throws: SSEClientError.unexpectedStatusCode(503)) {
            _ = try await collect(client.events(), limit: 10)
        }
        #expect(StubProtocol.requests(host: host).count == 3)
    }

    @Test func cleanCloseWithReconnectionDisabledFinishesNormally() async throws {
        let policy = ReconnectionPolicy(initialDelayMilliseconds: 1, maxDelayMilliseconds: 1, maxRetries: 0)
        let (client, host) = makeSSEClient(reconnectionPolicy: policy) { _ in
            StubResponse(chunks: ["data: only\n\n"])
        }
        #expect(try await collect(client.events(), limit: 10).map(\.data) == ["only"])
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    // MARK: Terminal responses

    @Test func noContentEndsStreamWithoutReconnecting() async throws {
        let (client, host) = makeSSEClient { _ in StubResponse(status: 204, headers: [:]) }
        #expect(try await collect(client.events(), limit: 10).isEmpty)
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test(arguments: [400, 401, 403, 404, 410])
    func clientErrorsFailImmediately(status: Int) async {
        let (client, host) = makeSSEClient { _ in StubResponse(status: status) }
        await #expect(throws: SSEClientError.unexpectedStatusCode(status)) {
            _ = try await collect(client.events(), limit: 10)
        }
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func invalidContentTypeFailsImmediately() async {
        let (client, host) = makeSSEClient { _ in
            StubResponse(headers: ["Content-Type": "application/json"], chunks: ["{}"])
        }
        await #expect(throws: SSEClientError.invalidContentType("application/json")) {
            _ = try await collect(client.events(), limit: 10)
        }
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    // MARK: Cancellation and lifecycle

    @Test func stoppingIterationClosesConnection() async throws {
        let (client, host) = makeSSEClient { _ in StubResponse(chunks: ["data: x\n\n"], keepOpen: true) }
        _ = try await collect(client.events(), limit: 1)
        #expect(await waitUntil { StubProtocol.stopCount(host: host) == 1 })
        try await Task.sleep(nanoseconds: 50_000_000)
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func cancellingTaskClosesConnection() async {
        let (client, host) = makeSSEClient { _ in StubResponse(keepOpen: true) }
        let task = Task {
            for try await _ in client.events() {}
        }
        #expect(await waitUntil { StubProtocol.requests(host: host).count == 1 })
        task.cancel()
        _ = await task.result
        #expect(await waitUntil { StubProtocol.stopCount(host: host) == 1 })
    }

    @Test func eachCallOpensItsOwnConnection() async throws {
        let (client, host) = makeSSEClient { _ in StubResponse(chunks: ["data: x\n\n"], keepOpen: true) }
        _ = try await collect(client.events(), limit: 1)
        _ = try await collect(client.events(), limit: 1)
        #expect(StubProtocol.requests(host: host).count == 2)
    }

    @Test func cancelBeforeStartIsSafe() async throws {
        let (stream, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let task = SSEClientTask(
            request: URLRequest(url: URL(string: "https://unused.test")!),
            session: .shared,
            reconnectionPolicy: ReconnectionPolicy(),
            continuation: continuation
        )
        task.cancel()
        task.start()
        continuation.finish()
        #expect(try await collect(stream, limit: 1).isEmpty)
    }
}

// MARK: - Retry classification

@Suite("SSEClient retry classification")
struct RetryClassificationTests {

    @Test(arguments: [408, 429, 500, 502, 503, 599])
    func retryableStatuses(status: Int) {
        #expect(SSEClient.isRetryable(SSEClientError.unexpectedStatusCode(status)))
    }

    @Test(arguments: [300, 400, 401, 403, 404, 410])
    func nonRetryableStatuses(status: Int) {
        #expect(!SSEClient.isRetryable(SSEClientError.unexpectedStatusCode(status)))
    }

    @Test func invalidResponsesAreNotRetried() {
        #expect(!SSEClient.isRetryable(SSEClientError.invalidContentType("text/html")))
        #expect(!SSEClient.isRetryable(SSEClientError.invalidContentType(nil)))
        #expect(!SSEClient.isRetryable(SSEClientError.nonHTTPResponse))
    }

    @Test(arguments: [URLError.Code.timedOut, .networkConnectionLost, .notConnectedToInternet, .cannotConnectToHost, .dnsLookupFailed])
    func transientURLErrorsAreRetried(code: URLError.Code) {
        #expect(SSEClient.isRetryable(URLError(code)))
    }

    @Test(arguments: [URLError.Code.cancelled, .badURL, .unsupportedURL, .serverCertificateUntrusted, .appTransportSecurityRequiresSecureConnection])
    func permanentURLErrorsAreNotRetried(code: URLError.Code) {
        #expect(!SSEClient.isRetryable(URLError(code)))
    }
}
