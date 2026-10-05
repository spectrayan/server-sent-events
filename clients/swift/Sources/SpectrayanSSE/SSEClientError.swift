import Foundation

/// Errors raised by ``SSEClient`` for responses that cannot be consumed as an SSE stream.
///
/// Transport failures are surfaced as the underlying `URLError` instead.
public enum SSEClientError: Error, Sendable, Equatable {
    /// The response was not an HTTP response.
    case nonHTTPResponse
    /// The server answered with a status other than 2xx (204 ends the stream normally instead).
    /// 408, 429 and 5xx are retried; this error only ends the stream for them once retries run out.
    case unexpectedStatusCode(Int)
    /// The server answered with a `Content-Type` other than `text/event-stream`.
    case invalidContentType(String?)
}
