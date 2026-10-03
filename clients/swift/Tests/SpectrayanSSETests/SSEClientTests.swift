import Foundation
import Testing
@testable import SpectrayanSSE

/// Registers a stub under a unique host and returns an `SSEClient` pointed at it.
private func makeSSEClient(
    reconnectionPolicy: ReconnectionPolicy = ReconnectionPolicy(initialDelayMilliseconds: 1, maxDelayMilliseconds: 1),
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

    @Test func receivesEventsAndSendsHeaders() async throws {
        let (client, host) = makeSSEClient { _ in
            StubResponse(chunks: ["id: 1\nevent: tok", "en\ndata: hel", "lo\n\ndata: x\n\n"], keepOpen: true)
        }
        let events = try await collect(client.events(), limit: 2)
        #expect(events == [ServerSentEvent(id: "1", event: "token", data: "hello"), ServerSentEvent(id: "1", data: "x")])

        let request = try #require(StubProtocol.requests(host: host).first)
        #expect(request.httpMethod == "GET")
        #expect(request.value(forHTTPHeaderField: "Accept") == "text/event-stream")
        #expect(request.value(forHTTPHeaderField: "Cache-Control") == "no-cache")
        #expect(request.value(forHTTPHeaderField: "Last-Event-ID") == nil)
    }

    @Test func reconnectsWithLastEventId() async throws {
        let (client, host) = makeSSEClient { attempt in
            attempt == 0
                ? StubResponse(chunks: ["id: 7\ndata: first\n\nid: 8\ndata: incomplete"])
                : StubResponse(chunks: ["data: second\n\n"], keepOpen: true)
        }
        let events = try await collect(client.events(), limit: 2)
        #expect(events.map(\.data) == ["first", "second"])
        #expect(StubProtocol.requests(host: host).map { $0.value(forHTTPHeaderField: "Last-Event-ID") } == [nil, "7"])
    }

    @Test func retriesNetworkErrors() async throws {
        let (client, host) = makeSSEClient { attempt in
            attempt == 0 ? StubResponse(error: URLError(.networkConnectionLost)) : StubResponse(chunks: ["data: ok\n\n"], keepOpen: true)
        }
        #expect(try await collect(client.events(), limit: 1).map(\.data) == ["ok"])
        #expect(StubProtocol.requests(host: host).count == 2)
    }

    @Test func noContentEndsStream() async throws {
        let (client, host) = makeSSEClient { _ in StubResponse(status: 204, headers: [:]) }
        #expect(try await collect(client.events(), limit: 10).isEmpty)
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func clientErrorFailsImmediately() async {
        let (client, host) = makeSSEClient { _ in StubResponse(status: 404) }
        await #expect(throws: SSEClientError.unexpectedStatusCode(404)) {
            _ = try await collect(client.events(), limit: 10)
        }
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func invalidContentTypeFailsImmediately() async {
        let (client, _) = makeSSEClient { _ in StubResponse(headers: ["Content-Type": "text/html"]) }
        await #expect(throws: SSEClientError.invalidContentType("text/html")) {
            _ = try await collect(client.events(), limit: 10)
        }
    }

    @Test func serverErrorsRetriedUntilMaxRetries() async {
        let policy = ReconnectionPolicy(initialDelayMilliseconds: 1, maxDelayMilliseconds: 1, maxRetries: 2)
        let (client, host) = makeSSEClient(reconnectionPolicy: policy) { _ in StubResponse(status: 503) }
        await #expect(throws: SSEClientError.unexpectedStatusCode(503)) {
            _ = try await collect(client.events(), limit: 10)
        }
        #expect(StubProtocol.requests(host: host).count == 3)
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
