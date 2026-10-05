import Foundation

/// How a single HTTP/SSE connection ended. This describes one connection only; whether the public
/// event stream continues (by reconnecting) is decided by the connection's owner.
enum SSEConnectionResult: Sendable {
    /// The server closed a stream it had accepted with 2xx and `text/event-stream`.
    case completed
    /// HTTP 204: the server asked the client to stop reconnecting.
    case noContent
    /// The connection could not be opened or broke: a transport error (including `URLError.cancelled`),
    /// or an ``SSEClientError`` for an unacceptable response.
    case failed(any Error)
}

/// `URLSessionDataDelegate` for one SSE connection.
///
/// It parses incoming bytes with ``SSEParser`` and yields events to `continuation`, which may be
/// shared by many successive connections. It never finishes `continuation`: when the connection
/// ends it resolves ``result`` instead, exactly once, and leaves the decision to reconnect or to
/// finish the public stream to its owner.
///
/// Typical use, attaching it to a single task so that any `URLSession` (including `.shared`) works:
///
/// ```swift
/// let delegate = SSESessionDelegate(continuation: continuation, parser: parser)
/// let task = session.dataTask(with: request)
/// task.delegate = delegate
/// let result = await withTaskCancellationHandler {
///     task.resume()
///     return await delegate.result
/// } onCancel: {
///     task.cancel()
/// }
/// parser = delegate.parser // carries lastEventId and retry: into the next connection
/// ```
///
/// `@unchecked Sendable`: URLSession calls the delegate on its own queue while the owner reads
/// ``result`` and the parser state from other tasks. All mutable state lives in `state`, which is
/// only accessed while holding `lock`; continuations are resumed and events yielded after the lock
/// is released.
final class SSESessionDelegate: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private struct State {
        var parser: SSEParser
        var didOpen = false
        var result: SSEConnectionResult?
        var waiters: [CheckedContinuation<SSEConnectionResult, Never>] = []
    }

    private let continuation: AsyncThrowingStream<ServerSentEvent, Error>.Continuation
    private let lock = NSLock()
    private var state: State

    init(
        continuation: AsyncThrowingStream<ServerSentEvent, Error>.Continuation,
        parser: SSEParser = SSEParser()
    ) {
        self.continuation = continuation
        self.state = State(parser: parser)
    }

    // MARK: Connection state

    /// How the connection ended. Suspends until it does; every caller receives the same value.
    ///
    /// Only resolves once the delegate's task completes, so the task must have been resumed (or
    /// cancelled) for this to return.
    var result: SSEConnectionResult {
        get async {
            await withCheckedContinuation { waiter in
                let result: SSEConnectionResult? = lock.withLock {
                    if let result = state.result { return result }
                    state.waiters.append(waiter)
                    return nil
                }
                if let result {
                    waiter.resume(returning: result)
                }
            }
        }
    }

    /// Whether the server accepted the stream (2xx with `text/event-stream`). Lets the owner tell a
    /// connection that broke after opening from one that never opened, e.g. to reset its backoff.
    var didOpen: Bool {
        lock.withLock { state.didOpen }
    }

    /// A snapshot of the parser, e.g. to seed the delegate of the next connection.
    ///
    /// After ``result`` resolves, any incomplete event has been discarded, while ``SSEParser/lastEventId``
    /// and ``SSEParser/reconnectionDelayMilliseconds`` are kept.
    var parser: SSEParser {
        lock.withLock { state.parser }
    }

    /// The last dispatched event ID, to be sent as `Last-Event-ID` when reconnecting.
    var lastEventId: String? {
        lock.withLock { state.parser.lastEventId }
    }

    /// The server's most recent `retry:` value, in milliseconds.
    var reconnectionDelayMilliseconds: Int? {
        lock.withLock { state.parser.reconnectionDelayMilliseconds }
    }

    // MARK: URLSessionDataDelegate

    func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive response: URLResponse,
        completionHandler: @escaping @Sendable (URLSession.ResponseDisposition) -> Void
    ) {
        let rejection: SSEConnectionResult?
        if let http = response as? HTTPURLResponse {
            if http.statusCode == 204 {
                rejection = .noContent
            } else if !(200..<300).contains(http.statusCode) {
                rejection = .failed(SSEClientError.unexpectedStatusCode(http.statusCode))
            } else if http.mimeType?.lowercased() != "text/event-stream" {
                rejection = .failed(SSEClientError.invalidContentType(http.mimeType))
            } else {
                rejection = nil
            }
        } else {
            rejection = .failed(SSEClientError.nonHTTPResponse)
        }

        if let rejection {
            // Resolve before cancelling: the cancellation's didCompleteWithError is then ignored.
            resolve(rejection)
            completionHandler(.cancel)
        } else {
            lock.withLock { state.didOpen = true }
            completionHandler(.allow)
        }
    }

    func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive data: Data
    ) {
        let events: [ServerSentEvent] = lock.withLock {
            guard state.didOpen, state.result == nil else { return [] }
            return state.parser.feed(data)
        }
        for event in events {
            continuation.yield(event)
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        if let error {
            resolve(.failed(error))
        } else if didOpen {
            resolve(.completed)
        } else {
            // Finished without ever delivering an acceptable response.
            resolve(.failed(SSEClientError.nonHTTPResponse))
        }
    }

    // MARK: Completion

    /// Records the connection's result and wakes every waiter. Only the first call has any effect.
    private func resolve(_ result: SSEConnectionResult) {
        let waiters: [CheckedContinuation<SSEConnectionResult, Never>] = lock.withLock {
            guard state.result == nil else { return [] }
            state.result = result
            // Discard any incomplete event; keeps lastEventId and the server's retry delay.
            state.parser.finish()
            defer { state.waiters.removeAll() }
            return state.waiters
        }
        for waiter in waiters {
            waiter.resume(returning: result)
        }
    }
}
