import Foundation

/// Represents a single Server-Sent Event received from an SSE stream.
public struct ServerSentEvent: Sendable, Equatable {

    /// Event identifier used for stream resumption through `Last-Event-ID`.
    public let id: String?

    /// Event type. Defaults to `"message"` when the server does not provide one.
    public let event: String

    /// Event payload. Multiple `data:` lines are joined using newline characters.
    public let data: String

    /// Server-provided reconnection delay from a `retry:` field in this event, in milliseconds
    /// (the unit used on the wire).
    ///
    /// Stored as an integer because `Duration` requires iOS 16 / macOS 13, above this package's
    /// deployment targets. Use ``retryDuration`` where `Duration` is available.
    public let retryMilliseconds: Int?

    /// SSE comment lines received before this event.
    public let comments: [String]

    public init(
        id: String? = nil,
        event: String = "message",
        data: String = "",
        retryMilliseconds: Int? = nil,
        comments: [String] = []
    ) {
        self.id = id
        self.event = event
        self.data = data
        self.retryMilliseconds = retryMilliseconds
        self.comments = comments
    }
}

extension ServerSentEvent {
    /// ``retryMilliseconds`` as a `Duration`.
    @available(iOS 16, macOS 13, watchOS 9, tvOS 16, *)
    public var retryDuration: Duration? {
        retryMilliseconds.map { .milliseconds($0) }
    }
}
