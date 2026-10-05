import Foundation

/// Incremental streaming parser conforming to the W3C EventSource (Server-Sent Events) specification.
///
/// Buffers raw bytes so it can be fed directly with the `Data` chunks delivered by
/// `URLSessionDataDelegate`. Handles arbitrary chunk boundaries (including ones that split a
/// multi-byte UTF-8 character or a CRLF pair), a leading UTF-8 BOM, CR / LF / CRLF line endings,
/// multiline data fields, event names, id tracking, retry directives, and comment lines such as
/// `:keepalive`, which never produce an event on their own.
///
/// ``lastEventId`` and ``reconnectionDelayMilliseconds`` persist across events and across
/// ``finish()``, so one parser can be reused for every reconnection of the same stream.
struct SSEParser: Sendable {
    private static let defaultEventType = "message"
    private static let lineFeed: UInt8 = 0x0A
    private static let carriageReturn: UInt8 = 0x0D
    private static let byteOrderMark = Data([0xEF, 0xBB, 0xBF])

    /// Bytes of the current, not yet terminated line. Line terminators are ASCII, so splitting on
    /// them never cuts a multi-byte UTF-8 sequence; each line is decoded only once it is complete.
    private var lineBuffer = Data()
    private var dataLines: [String] = []
    private var comments: [String] = []
    private var currentEvent: String = defaultEventType
    private var currentId: String?
    /// The `retry:` value received within the current event block, reported on that event.
    private var currentRetry: Int?

    /// Set when a chunk ended in `\r`, so a `\n` at the start of the next chunk completes a CRLF
    /// pair instead of being read as an extra empty line.
    private var pendingCarriageReturn = false

    /// Whether the start of the stream has been checked for a UTF-8 byte order mark.
    private var checkedByteOrderMark = false

    /// The last successfully dispatched event ID, to be sent as `Last-Event-ID` on reconnection.
    private(set) var lastEventId: String?

    /// The stream's reconnection delay in milliseconds, from the most recent valid `retry:` field.
    ///
    /// Per spec this takes effect as soon as the field is parsed, whether or not the surrounding
    /// event is ever dispatched, and stays in effect until the server sends a new one.
    private(set) var reconnectionDelayMilliseconds: Int?

    /// ``reconnectionDelayMilliseconds`` as a `Duration`.
    @available(iOS 16, macOS 13, watchOS 9, tvOS 16, *)
    var reconnectionDelay: Duration? {
        reconnectionDelayMilliseconds.map { .milliseconds($0) }
    }

    init(initialLastEventId: String? = nil, initialReconnectionDelayMilliseconds: Int? = nil) {
        self.lastEventId = initialLastEventId
        self.reconnectionDelayMilliseconds = initialReconnectionDelayMilliseconds
    }

    /// Feeds an incoming chunk of raw bytes, returning any completed events.
    mutating func feed(_ chunk: Data) -> [ServerSentEvent] {
        var events: [ServerSentEvent] = []

        for byte in chunk {
            if pendingCarriageReturn {
                pendingCarriageReturn = false
                if byte == Self.lineFeed { continue }
            }

            if byte == Self.lineFeed || byte == Self.carriageReturn {
                pendingCarriageReturn = byte == Self.carriageReturn
                checkedByteOrderMark = true
                if let event = processLine(takeLine()) {
                    events.append(event)
                }
            } else {
                lineBuffer.append(byte)
                stripByteOrderMarkIfNeeded()
            }
        }

        return events
    }

    /// Processes a single SSE text line according to W3C parsing rules.
    mutating func processLine(_ line: String) -> ServerSentEvent? {
        // Empty line indicates event boundary
        if line.isEmpty {
            return dispatchEvent()
        }

        // Comment line
        if line.hasPrefix(":") {
            comments.append(Self.stripLeadingSpace(line.dropFirst()))
            return nil
        }

        let field: Substring
        let value: String

        if let colonIndex = line.firstIndex(of: ":") {
            field = line[..<colonIndex]
            // Strip a single leading space if present
            value = Self.stripLeadingSpace(line[line.index(after: colonIndex)...])
        } else {
            field = line[...]
            value = ""
        }

        switch field {
        case "data":
            dataLines.append(value)
        case "event":
            currentEvent = value
        case "id":
            // W3C spec: if value contains null character (U+0000), ignore it
            if !value.unicodeScalars.contains("\u{0000}") {
                currentId = value
            }
        case "retry":
            // W3C spec: only ASCII digits are valid; the value is in milliseconds.
            if !value.isEmpty, value.unicodeScalars.allSatisfy({ ("0"..."9").contains($0) }),
               let milliseconds = Int(value) {
                currentRetry = milliseconds
                reconnectionDelayMilliseconds = milliseconds
            }
        default:
            // Unknown fields are ignored.
            break
        }

        return nil
    }

    /// Ends the current stream (EOF or disconnect).
    ///
    /// Per spec, an event that was not terminated by a blank line is incomplete and is discarded,
    /// together with any unterminated line; its `id:` does not update ``lastEventId``.
    /// ``lastEventId`` and ``reconnectionDelayMilliseconds`` are kept for the next connection,
    /// which starts a new stream and so may begin with its own BOM.
    mutating func finish() {
        lineBuffer.removeAll()
        pendingCarriageReturn = false
        checkedByteOrderMark = false
        reset()
    }

    private mutating func dispatchEvent() -> ServerSentEvent? {
        defer { reset() }

        if let currentId {
            lastEventId = currentId
        }

        // No data accumulated: per spec, nothing is dispatched.
        guard !dataLines.isEmpty else { return nil }

        return ServerSentEvent(
            id: currentId ?? lastEventId,
            event: currentEvent.isEmpty ? Self.defaultEventType : currentEvent,
            data: dataLines.joined(separator: "\n"),
            retryMilliseconds: currentRetry,
            comments: comments
        )
    }

    private mutating func reset() {
        dataLines.removeAll()
        comments.removeAll()
        currentEvent = Self.defaultEventType
        currentId = nil
        currentRetry = nil
    }

    /// Decodes and clears the buffered line. Invalid UTF-8 is replaced with U+FFFD, as the spec requires.
    private mutating func takeLine() -> String {
        let line = String(decoding: lineBuffer, as: UTF8.self)
        lineBuffer.removeAll(keepingCapacity: true)
        return line
    }

    /// Drops a UTF-8 byte order mark at the very start of the stream, per spec.
    private mutating func stripByteOrderMarkIfNeeded() {
        guard !checkedByteOrderMark else { return }
        if lineBuffer.count < Self.byteOrderMark.count {
            // Not enough bytes yet to decide, unless they already diverge from the BOM.
            checkedByteOrderMark = !Self.byteOrderMark.starts(with: lineBuffer)
            return
        }
        checkedByteOrderMark = true
        if lineBuffer.starts(with: Self.byteOrderMark) {
            lineBuffer.removeFirst(Self.byteOrderMark.count)
        }
    }

    private static func stripLeadingSpace(_ value: Substring) -> String {
        String(value.hasPrefix(" ") ? value.dropFirst() : value)
    }
}
