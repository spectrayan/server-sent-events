import Foundation
import Testing
@testable import SpectrayanSSE

// MARK: - Helpers

private extension SSEConnectionResult {
    var isCompleted: Bool {
        if case .completed = self { return true }
        return false
    }

    var isNoContent: Bool {
        if case .noContent = self { return true }
        return false
    }

    var error: (any Error)? {
        if case .failed(let error) = self { return error }
        return nil
    }

    var summary: String {
        switch self {
        case .completed: "completed"
        case .noContent: "noContent"
        case .failed(let error): "failed: \(error)"
        }
    }
}

/// A value shared with `@Sendable` closures in tests.
private final class Locked<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var _value: Value

    init(_ value: Value) { _value = value }

    var value: Value { lock.withLock { _value } }

    func mutate(_ body: (inout Value) -> Void) { lock.withLock { body(&_value) } }
}

/// Starts one stubbed connection whose delegate yields into `continuation`, attached as a
/// task-level delegate exactly as `SSEClientTask` does.
private func startConnection(
    continuation: AsyncThrowingStream<ServerSentEvent, Error>.Continuation,
    parser: SSEParser = SSEParser(),
    respond: @escaping @Sendable (_ attempt: Int) -> StubResponse
) -> (SSESessionDelegate, URLSessionDataTask, host: String) {
    let host = "delegate-\(UUID().uuidString.lowercased()).test"
    StubProtocol.register(host: host, respond: respond)
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [StubProtocol.self]

    let delegate = SSESessionDelegate(continuation: continuation, parser: parser)
    let task = URLSession(configuration: configuration).dataTask(with: URL(string: "https://\(host)/events")!)
    task.delegate = delegate
    task.resume()
    return (delegate, task, host)
}

private func httpResponse(status: Int, contentType: String? = "text/event-stream") -> HTTPURLResponse {
    HTTPURLResponse(
        url: URL(string: "https://example.test")!,
        statusCode: status,
        httpVersion: "HTTP/1.1",
        headerFields: contentType.map { ["Content-Type": $0] }
    )!
}

// MARK: - Tests

@Suite("SSESessionDelegate")
struct SSESessionDelegateTests {

    // MARK: Stream lifetime

    @Test func connectionCompletionDoesNotFinishPublicStream() async throws {
        let (stream, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let (delegate, _, _) = startConnection(continuation: continuation) { _ in
            StubResponse(chunks: ["data: from connection\n\n"])
        }
        #expect(await delegate.result.isCompleted)

        // If the delegate had finished the stream, this event would be dropped.
        continuation.yield(ServerSentEvent(data: "after completion"))
        continuation.finish()
        #expect(try await collect(stream, limit: 10).map(\.data) == ["from connection", "after completion"])
    }

    @Test func successiveConnectionsShareOneStream() async throws {
        let (stream, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()

        let (first, _, _) = startConnection(continuation: continuation) { _ in
            StubResponse(chunks: ["id: 1\ndata: a\n\n"])
        }
        _ = await first.result

        let (second, _, _) = startConnection(continuation: continuation, parser: first.parser) { _ in
            StubResponse(status: 503)
        }
        #expect(await second.result.error as? SSEClientError == .unexpectedStatusCode(503))

        let (third, _, _) = startConnection(continuation: continuation, parser: second.parser) { _ in
            StubResponse(chunks: ["data: b\n\n"])
        }
        _ = await third.result
        continuation.finish()

        let events = try await collect(stream, limit: 10)
        #expect(events.map(\.data) == ["a", "b"])
        #expect(events.map(\.id) == ["1", "1"])
    }

    // MARK: Response handling

    @Test func noContentIsReportedDistinctly() async throws {
        let (stream, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let (delegate, _, _) = startConnection(continuation: continuation) { _ in
            StubResponse(status: 204, headers: [:], chunks: ["data: ignored\n\n"])
        }
        #expect(await delegate.result.isNoContent)
        #expect(!delegate.didOpen)
        continuation.finish()
        #expect(try await collect(stream, limit: 10).isEmpty)
    }

    @Test(arguments: [400, 401, 404, 429, 500, 503])
    func httpErrorsAreReportedToOwner(status: Int) async throws {
        let (stream, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let (delegate, _, _) = startConnection(continuation: continuation) { _ in
            StubResponse(status: status, chunks: ["data: ignored\n\n"])
        }
        #expect(await delegate.result.error as? SSEClientError == .unexpectedStatusCode(status))
        #expect(!delegate.didOpen)

        // The owner, not the delegate, decides how the public stream ends.
        continuation.yield(ServerSentEvent(data: "still open"))
        continuation.finish()
        #expect(try await collect(stream, limit: 10).map(\.data) == ["still open"])
    }

    @Test func invalidContentTypeIsReportedAndBodyIgnored() async throws {
        let (stream, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let (delegate, _, _) = startConnection(continuation: continuation) { _ in
            StubResponse(headers: ["Content-Type": "text/plain"], chunks: ["data: ignored\n\n"])
        }
        #expect(await delegate.result.error as? SSEClientError == .invalidContentType("text/plain"))
        continuation.finish()
        #expect(try await collect(stream, limit: 10).isEmpty)
    }

    @Test func transportErrorIsReported() async {
        let (_, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let (delegate, _, _) = startConnection(continuation: continuation) { _ in
            StubResponse(error: URLError(.networkConnectionLost))
        }
        #expect((await delegate.result.error as? URLError)?.code == .networkConnectionLost)
        #expect(!delegate.didOpen)
    }

    @Test func errorAfterOpeningIsReportedWithDidOpen() async throws {
        let (stream, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let (delegate, task, host) = startConnection(continuation: continuation) { _ in
            StubResponse(chunks: ["data: before drop\n\n"], keepOpen: true)
        }
        #expect(await waitUntil { delegate.didOpen && StubProtocol.requests(host: host).count == 1 })
        task.cancel()
        #expect((await delegate.result.error as? URLError)?.code == .cancelled)
        #expect(delegate.didOpen)
        continuation.finish()
        #expect(try await collect(stream, limit: 10).map(\.data) == ["before drop"])
    }

    // MARK: Completion is signalled once

    @Test func rejectionThenCancellationCompletesOnce() async {
        let (_, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let delegate = SSESessionDelegate(continuation: continuation)
        let session = URLSession(configuration: .ephemeral)
        let task = session.dataTask(with: URL(string: "https://example.test")!)
        let dispositions = Locked<[URLSession.ResponseDisposition]>([])

        delegate.urlSession(session, dataTask: task, didReceive: httpResponse(status: 404)) { disposition in
            dispositions.mutate { $0.append(disposition) }
        }
        // URLSession reports the cancellation caused by the rejection, then may complete again.
        delegate.urlSession(session, task: task, didCompleteWithError: URLError(.cancelled))
        delegate.urlSession(session, task: task, didCompleteWithError: nil)

        #expect(dispositions.value == [.cancel])
        #expect(await delegate.result.error as? SSEClientError == .unexpectedStatusCode(404))
    }

    @Test func noContentThenCancellationCompletesOnce() async {
        let (_, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let delegate = SSESessionDelegate(continuation: continuation)
        let session = URLSession(configuration: .ephemeral)
        let task = session.dataTask(with: URL(string: "https://example.test")!)

        delegate.urlSession(session, dataTask: task, didReceive: httpResponse(status: 204, contentType: nil)) { _ in }
        delegate.urlSession(session, task: task, didCompleteWithError: URLError(.cancelled))

        #expect(await delegate.result.isNoContent)
    }

    @Test func dataAfterCompletionIsIgnored() async throws {
        let (stream, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let delegate = SSESessionDelegate(continuation: continuation)
        let session = URLSession(configuration: .ephemeral)
        let task = session.dataTask(with: URL(string: "https://example.test")!)

        delegate.urlSession(session, dataTask: task, didReceive: httpResponse(status: 200)) { _ in }
        delegate.urlSession(session, dataTask: task, didReceive: Data("data: a\n\n".utf8))
        delegate.urlSession(session, task: task, didCompleteWithError: nil)
        delegate.urlSession(session, dataTask: task, didReceive: Data("data: late\n\n".utf8))
        continuation.finish()

        #expect(await delegate.result.isCompleted)
        #expect(try await collect(stream, limit: 10).map(\.data) == ["a"])
    }

    @Test func manyConcurrentWaitersAllReceiveTheSameResult() async {
        let (_, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let delegate = SSESessionDelegate(continuation: continuation)
        let session = URLSession(configuration: .ephemeral)
        let task = session.dataTask(with: URL(string: "https://example.test")!)

        let observed = await withTaskGroup(of: String?.self) { group in
            for _ in 0..<50 {
                group.addTask { await delegate.result.summary }
            }
            // Racing terminal callbacks: only the first one to resolve may win.
            group.addTask {
                delegate.urlSession(session, dataTask: task, didReceive: httpResponse(status: 204, contentType: nil)) { _ in }
                return nil
            }
            group.addTask {
                delegate.urlSession(session, task: task, didCompleteWithError: URLError(.cancelled))
                return nil
            }
            return await group.reduce(into: [String]()) { results, summary in
                if let summary { results.append(summary) }
            }
        }

        let final = await delegate.result.summary
        #expect(observed.count == 50)
        #expect(Set(observed) == [final])
    }

    @Test func resultIsAvailableAfterCompletion() async {
        let (_, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let (delegate, _, _) = startConnection(continuation: continuation) { _ in StubResponse(status: 204, headers: [:]) }
        #expect(await delegate.result.isNoContent)
        // Awaiting again after resolution returns immediately with the same value.
        #expect(await delegate.result.isNoContent)
    }

    // MARK: Parser state

    @Test func parserStateSurvivesConnectionCompletion() async {
        let (_, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let (delegate, _, _) = startConnection(continuation: continuation) { _ in
            StubResponse(chunks: ["retry: 5000\nid: 1\ndata: done\n\nid: 2\ndata: incomplete"])
        }
        #expect(await delegate.result.isCompleted)
        #expect(delegate.didOpen)
        // The incomplete event's id is discarded, the dispatched one and retry: are kept.
        #expect(delegate.lastEventId == "1")
        #expect(delegate.reconnectionDelayMilliseconds == 5000)
        #expect(delegate.parser.lastEventId == "1")
        #expect(delegate.parser.reconnectionDelayMilliseconds == 5000)
    }

    @Test func parserStateSurvivesFailure() async {
        let (_, continuation) = AsyncThrowingStream<ServerSentEvent, Error>.makeStream()
        let seed = SSEParser(initialLastEventId: "41", initialReconnectionDelayMilliseconds: 750)
        let (delegate, _, _) = startConnection(continuation: continuation, parser: seed) { _ in
            StubResponse(status: 500)
        }
        #expect(await delegate.result.error != nil)
        #expect(delegate.lastEventId == "41")
        #expect(delegate.reconnectionDelayMilliseconds == 750)
    }
}
