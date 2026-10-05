import Foundation

/// A Server-Sent Events client that streams events over a caller-provided `URLSession`.
///
/// ```swift
/// let client = SSEClient(url: URL(string: "https://example.com/sse")!)
///
/// for try await event in client.events() {
///     print(event.event, event.data)
/// }
/// ```
public final class SSEClient: Sendable {
    public let url: URL

    private let session: URLSession
    private let reconnectionPolicy: ReconnectionPolicy

    public init(
        url: URL,
        session: URLSession = .shared,
        reconnectionPolicy: ReconnectionPolicy = .init()
    ) {
        self.url = url
        self.session = session
        self.reconnectionPolicy = reconnectionPolicy
    }

    private func makeRequest(lastEventId: String? = nil) -> URLRequest {
        var request = URLRequest(url: url)

        request.httpMethod = "GET"
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

        if let lastEventId, !lastEventId.isEmpty {
            request.setValue(lastEventId, forHTTPHeaderField: "Last-Event-ID")
        }

        return request
    }

    /// Opens the stream and returns its events.
    ///
    /// Each call starts its own connection loop. When a connection ends, the client waits according
    /// to ``ReconnectionPolicy`` (or the server's `retry:`), then reconnects with `Last-Event-ID`;
    /// events from every connection arrive on the same stream. The stream finishes on HTTP 204, on a
    /// non-retryable error, when retries run out, or when the consuming task is cancelled, which
    /// also cancels the in-flight request.
    public func events() -> AsyncThrowingStream<ServerSentEvent, Error> {
        AsyncThrowingStream { continuation in
            let task = SSEClientTask(
                request: makeRequest(),
                session: session,
                reconnectionPolicy: reconnectionPolicy,
                continuation: continuation
            )

            task.start()

            continuation.onTermination = { @Sendable _ in
                task.cancel()
            }
        }
    }
}

extension SSEClient {
    /// Whether a failed connection should be retried.
    ///
    /// Network failures, timeouts and 408 / 429 / 5xx responses are transient and retried.
    /// Other HTTP statuses, invalid responses, cancellation, malformed URLs and TLS trust failures
    /// are permanent and end the stream.
    static func isRetryable(_ error: Error) -> Bool {
        switch error {
        case SSEClientError.unexpectedStatusCode(let status):
            return status == 408 || status == 429 || (500..<600).contains(status)
        case is SSEClientError:
            return false
        case let urlError as URLError:
            switch urlError.code {
            case .cancelled, .badURL, .unsupportedURL, .userAuthenticationRequired,
                 .appTransportSecurityRequiresSecureConnection,
                 .serverCertificateUntrusted, .serverCertificateHasBadDate,
                 .serverCertificateNotYetValid, .serverCertificateHasUnknownRoot,
                 .clientCertificateRejected, .clientCertificateRequired:
                return false
            default:
                return true
            }
        default:
            return true
        }
    }
}

/// Runs one ``SSEClient/events()`` stream: connects, parses, and reconnects with backoff until the
/// stream is cancelled, the server answers 204, a non-retryable error occurs, or retries run out.
///
/// `@unchecked Sendable`: the only mutable state (`task`, `isCancelled`) is guarded by `lock`.
final class SSEClientTask: @unchecked Sendable {
    private let request: URLRequest
    private let session: URLSession
    private let reconnectionPolicy: ReconnectionPolicy
    private let continuation: AsyncThrowingStream<ServerSentEvent, Error>.Continuation

    private let lock = NSLock()
    private var task: Task<Void, Never>?
    private var isCancelled = false

    init(
        request: URLRequest,
        session: URLSession,
        reconnectionPolicy: ReconnectionPolicy,
        continuation: AsyncThrowingStream<ServerSentEvent, Error>.Continuation
    ) {
        self.request = request
        self.session = session
        self.reconnectionPolicy = reconnectionPolicy
        self.continuation = continuation
    }

    /// Starts the connection loop. Calling it again, or after ``cancel()``, has no effect.
    func start() {
        lock.withLock {
            guard task == nil, !isCancelled else { return }
            task = Task { [self] in
                await run()
            }
        }
    }

    /// Stops the connection loop and closes any open connection.
    func cancel() {
        let task = lock.withLock {
            isCancelled = true
            return self.task
        }
        task?.cancel()
    }

    private func run() async {
        var parser = SSEParser(initialLastEventId: request.value(forHTTPHeaderField: "Last-Event-ID"))
        var attempt = 0

        while !Task.isCancelled {
            let (result, didOpen) = await connect(parser: &parser)
            if Task.isCancelled { break }

            var lastError: Error?
            switch result {
            case .noContent:
                continuation.finish()
                return
            case .completed:
                attempt = 0
            case .failed(let error):
                guard SSEClient.isRetryable(error) else {
                    continuation.finish(throwing: error)
                    return
                }
                if didOpen { attempt = 0 }
                lastError = error
            }

            if let maxRetries = reconnectionPolicy.maxRetries, attempt >= maxRetries {
                // Out of retries: surface the last failure, or end normally after a clean close.
                continuation.finish(throwing: lastError)
                return
            }

            let delay = reconnectionPolicy.delayMilliseconds(
                forAttempt: attempt,
                serverDelayMilliseconds: parser.reconnectionDelayMilliseconds
            )
            attempt += 1

            do {
                try await Task.sleep(nanoseconds: UInt64(delay) * 1_000_000)
            } catch {
                break
            }
        }

        continuation.finish()
    }

    /// Runs one HTTP connection to completion. Events go straight to the shared `continuation`;
    /// on return, `parser` holds the connection's final state (`lastEventId`, `retry:`).
    private func connect(parser: inout SSEParser) async -> (SSEConnectionResult, didOpen: Bool) {
        var request = self.request
        if let lastEventId = parser.lastEventId, !lastEventId.isEmpty {
            request.setValue(lastEventId, forHTTPHeaderField: "Last-Event-ID")
        }

        let delegate = SSESessionDelegate(continuation: continuation, parser: parser)
        let task = session.dataTask(with: request)
        // A task-level delegate works with any session, including `URLSession.shared`.
        task.delegate = delegate

        let result = await withTaskCancellationHandler {
            task.resume()
            return await delegate.result
        } onCancel: {
            task.cancel()
        }

        parser = delegate.parser
        return (result, delegate.didOpen)
    }
}
