import Foundation
import Testing
@testable import SpectrayanSSE

// MARK: - Helpers

private extension SSEParser {
    mutating func feed(_ text: String) -> [ServerSentEvent] {
        feed(Data(text.utf8))
    }

    mutating func feed(_ bytes: [UInt8]) -> [ServerSentEvent] {
        feed(Data(bytes))
    }
}

/// Parses a complete stream in a single chunk with a fresh parser.
private func parse(_ stream: String) -> [ServerSentEvent] {
    var parser = SSEParser()
    return parser.feed(stream)
}

/// Feeds `bytes` to a fresh parser in chunks of `size` bytes.
private func parse(_ bytes: [UInt8], chunkSize size: Int) -> [ServerSentEvent] {
    var parser = SSEParser()
    var events: [ServerSentEvent] = []
    var start = 0
    while start < bytes.count {
        let end = min(start + size, bytes.count)
        events += parser.feed(Array(bytes[start..<end]))
        start = end
    }
    return events
}

private let byteOrderMark: [UInt8] = [0xEF, 0xBB, 0xBF]

// MARK: - Suites

@Suite("SSEParser")
struct SSEParserTests {

    // MARK: Dispatch

    @Suite("Event dispatch")
    struct Dispatch {

        @Test func minimalEventUsesDefaults() {
            #expect(parse("data: hello\n\n") == [
                ServerSentEvent(id: nil, event: "message", data: "hello", retryMilliseconds: nil, comments: [])
            ])
        }

        @Test func parsesAllFieldsTogether() {
            var parser = SSEParser()
            let events = parser.feed(": hello\nid: 7\nevent: update\nretry: 1500\ndata: a\ndata: b\n\n")
            #expect(events == [
                ServerSentEvent(id: "7", event: "update", data: "a\nb", retryMilliseconds: 1500, comments: ["hello"])
            ])
            #expect(parser.lastEventId == "7")
            #expect(parser.reconnectionDelayMilliseconds == 1500)
        }

        @Test func fieldOrderDoesNotMatter() {
            #expect(parse("data: x\nevent: e\nid: 1\n\n") == parse("id: 1\nevent: e\ndata: x\n\n"))
        }

        @Test func eventIsNotDispatchedUntilBlankLine() {
            var parser = SSEParser()
            #expect(parser.feed("data: a\n").isEmpty)
            #expect(parser.feed("data: b\n").isEmpty)
            #expect(parser.feed("\n").map(\.data) == ["a\nb"])
        }

        @Test func multipleEventsInOneChunk() {
            #expect(parse("data: 1\n\ndata: 2\n\ndata: 3\n\n").map(\.data) == ["1", "2", "3"])
        }

        @Test func consecutiveBlankLinesDoNotProduceExtraEvents() {
            #expect(parse("\n\n\ndata: x\n\n\n\n").map(\.data) == ["x"])
        }

        @Test(arguments: [
            "event: update\n\n",
            "id: 1\n\n",
            "retry: 1000\n\n",
            ": comment\n\n",
            "unknown: value\n\n",
            "\n",
        ])
        func blockWithoutDataIsNotDispatched(stream: String) {
            #expect(parse(stream).isEmpty)
        }

        @Test func perEventStateResetsAfterDispatch() {
            let events = parse(": c\nevent: custom\nid: 1\nretry: 10\ndata: first\n\ndata: second\n\n")
            #expect(events.count == 2)
            #expect(events[1] == ServerSentEvent(id: "1", event: "message", data: "second", retryMilliseconds: nil, comments: []))
        }

        @Test func stateFromUndispatchedBlockDoesNotLeak() {
            // The `event:` and comment belong to a block with no data, so they are dropped.
            let events = parse(": heartbeat\nevent: ignored\n\ndata: x\n\n")
            #expect(events == [ServerSentEvent(data: "x")])
        }

        @Test func realisticTokenStream() {
            let stream = """
            : connected

            id: 1
            event: token
            data: {"text":"Hel"}

            id: 2
            event: token
            data: {"text":"lo"}

            :keepalive

            id: 3
            event: done
            data: [DONE]


            """
            let events = parse(stream)
            #expect(events.map(\.event) == ["token", "token", "done"])
            #expect(events.map(\.id) == ["1", "2", "3"])
            #expect(events.map(\.data) == [#"{"text":"Hel"}"#, #"{"text":"lo"}"#, "[DONE]"])
        }
    }

    // MARK: Data

    @Suite("data field")
    struct DataField {

        @Test(arguments: [
            ("data: a\ndata: b\ndata: c\n\n", "a\nb\nc"),
            ("data\n\n", ""),
            ("data:\n\n", ""),
            ("data: \n\n", ""),
            ("data:\ndata:\n\n", "\n"),
            ("data: a\ndata:\n\n", "a\n"),
            ("data:\ndata: a\n\n", "\na"),
            ("data: {\"k\": [1, 2]}\n\n", "{\"k\": [1, 2]}"),
        ])
        func joinsLinesWithNewline(stream: String, expected: String) {
            #expect(parse(stream).map(\.data) == [expected])
        }

        @Test func largePayload() {
            let payload = String(repeating: "x", count: 100_000)
            #expect(parse("data: \(payload)\n\n").first?.data == payload)
        }

        @Test func manyDataLines() {
            let lines = (0..<1_000).map(String.init)
            let stream = lines.map { "data: \($0)\n" }.joined() + "\n"
            #expect(parse(stream).first?.data == lines.joined(separator: "\n"))
        }
    }

    // MARK: Field/value syntax

    @Suite("Field and value syntax")
    struct Syntax {

        @Test(arguments: [
            ("data:x\n\n", "x"),
            ("data: x\n\n", "x"),
            ("data:  x\n\n", " x"),
            ("data:   x  \n\n", "  x  "),
            ("data:\tx\n\n", "\tx"),
            ("data: a:b:c\n\n", "a:b:c"),
            ("data: : not a comment\n\n", ": not a comment"),
            ("data: http://example.com\n\n", "http://example.com"),
        ])
        func valueParsing(stream: String, expected: String) {
            #expect(parse(stream).map(\.data) == [expected])
        }

        @Test(arguments: [
            "Data: x\n\n",
            "DATA: x\n\n",
            " data: x\n\n",
            "data : x\n\n",
            "datax: x\n\n",
            "dat: x\n\n",
        ])
        func fieldNamesMustMatchExactly(stream: String) {
            #expect(parse(stream).isEmpty)
        }

        @Test func unknownFieldsAreIgnored() {
            #expect(parse("foo: bar\ndata: x\nbaz\n\n") == [ServerSentEvent(data: "x")])
        }
    }

    // MARK: Event

    @Suite("event field")
    struct EventField {

        @Test(arguments: [
            ("event: update\ndata: x\n\n", "update"),
            ("event:update\ndata: x\n\n", "update"),
            ("event: with spaces \ndata: x\n\n", "with spaces "),
            ("event: 🚀\ndata: x\n\n", "🚀"),
            ("event:\ndata: x\n\n", "message"),
            ("event\ndata: x\n\n", "message"),
            ("event: a\nevent: b\ndata: x\n\n", "b"),
        ])
        func eventType(stream: String, expected: String) {
            #expect(parse(stream).map(\.event) == [expected])
        }
    }

    // MARK: ID

    @Suite("id field")
    struct IdField {

        @Test func idIsReportedAndRemembered() {
            var parser = SSEParser()
            #expect(parser.feed("id: abc\ndata: x\n\n").map(\.id) == ["abc"])
            #expect(parser.lastEventId == "abc")
        }

        @Test func idCarriesForwardToLaterEvents() {
            var parser = SSEParser(initialLastEventId: "1")
            let events = parser.feed("data: a\n\nid: 2\ndata: b\n\ndata: c\n\n")
            #expect(events.map(\.id) == ["1", "2", "2"])
        }

        @Test func noIdByDefault() {
            var parser = SSEParser()
            #expect(parser.feed("data: x\n\n").map(\.id) == [nil])
            #expect(parser.lastEventId == nil)
        }

        @Test func initialLastEventIdIsExposed() {
            #expect(SSEParser(initialLastEventId: "42").lastEventId == "42")
        }

        @Test func lastIdInBlockWins() {
            #expect(parse("id: 1\nid: 2\ndata: x\n\n").map(\.id) == ["2"])
        }

        @Test func emptyIdResetsLastEventId() {
            var parser = SSEParser(initialLastEventId: "5")
            #expect(parser.feed("id\ndata: x\n\n").map(\.id) == [""])
            #expect(parser.lastEventId == "")
        }

        @Test func idPreservesInnerAndTrailingSpaces() {
            #expect(parse("id: a b \ndata: x\n\n").map(\.id) == ["a b "])
        }

        @Test func idContainingNullIsIgnored() {
            var parser = SSEParser(initialLastEventId: "keep")
            let events = parser.feed("id: a\u{0000}b\ndata: x\n\n")
            #expect(events.map(\.id) == ["keep"])
            #expect(parser.lastEventId == "keep")
        }

        @Test func nullIdDoesNotOverrideEarlierIdInSameBlock() {
            #expect(parse("id: good\nid: bad\u{0000}\ndata: x\n\n").map(\.id) == ["good"])
        }

        @Test func idWithoutDataStillUpdatesLastEventId() {
            var parser = SSEParser()
            #expect(parser.feed(": heartbeat\nid: 3\n\n").isEmpty)
            #expect(parser.lastEventId == "3")
            #expect(parser.feed("data: x\n\n").map(\.id) == ["3"])
        }
    }

    // MARK: Retry

    @Suite("retry field")
    struct RetryField {

        @Test(arguments: [
            ("0", 0),
            ("1", 1),
            ("1500", 1500),
            ("007", 7),
            ("86400000", 86_400_000),
        ])
        func validValues(value: String, expected: Int) {
            var parser = SSEParser()
            let events = parser.feed("retry: \(value)\ndata: x\n\n")
            #expect(events.map(\.retryMilliseconds) == [expected])
            #expect(parser.reconnectionDelayMilliseconds == expected)
        }

        @Test(arguments: [
            "",
            " 5",
            "5 ",
            "1.5",
            "-3",
            "+5",
            "abc",
            "1e3",
            "0x10",
            "١٢٣",
            "99999999999999999999999",
        ])
        func invalidValuesAreIgnored(value: String) {
            var parser = SSEParser(initialReconnectionDelayMilliseconds: 100)
            let events = parser.feed("retry: \(value)\ndata: x\n\n")
            #expect(events.map(\.retryMilliseconds) == [nil])
            #expect(parser.reconnectionDelayMilliseconds == 100)
        }

        @Test func retryWithoutColonIsIgnored() {
            var parser = SSEParser()
            #expect(parser.feed("retry\ndata: x\n\n").map(\.retryMilliseconds) == [nil])
            #expect(parser.reconnectionDelayMilliseconds == nil)
        }

        @Test func reconnectionDelayPersistsAcrossEvents() {
            var parser = SSEParser()
            let events = parser.feed("retry: 3000\ndata: a\n\ndata: b\n\n")
            #expect(events.map(\.retryMilliseconds) == [3000, nil])
            #expect(parser.reconnectionDelayMilliseconds == 3000)
        }

        @Test func reconnectionDelayAppliesWithoutDispatch() {
            var parser = SSEParser()
            #expect(parser.feed("retry: 500\n\n").isEmpty)
            #expect(parser.reconnectionDelayMilliseconds == 500)
        }

        @Test func reconnectionDelayAppliesBeforeBlockEnds() {
            var parser = SSEParser()
            _ = parser.feed("retry: 250\n")
            #expect(parser.reconnectionDelayMilliseconds == 250)
        }

        @Test func latestRetryWins() {
            var parser = SSEParser()
            let events = parser.feed("retry: 1\nretry: 2\ndata: x\n\nretry: 3\n\n")
            #expect(events.map(\.retryMilliseconds) == [2])
            #expect(parser.reconnectionDelayMilliseconds == 3)
        }

        @Test func initialReconnectionDelayIsExposed() {
            #expect(SSEParser(initialReconnectionDelayMilliseconds: 2000).reconnectionDelayMilliseconds == 2000)
            #expect(SSEParser().reconnectionDelayMilliseconds == nil)
        }

        @Test func durationViews() {
            guard #available(iOS 16, macOS 13, watchOS 9, tvOS 16, *) else { return }
            var parser = SSEParser()
            #expect(parser.reconnectionDelay == nil)
            let event = parser.feed("retry: 1500\ndata: x\n\n").first
            #expect(event?.retryDuration == .milliseconds(1500))
            #expect(parser.reconnectionDelay == .seconds(1.5))
        }
    }

    // MARK: Comments

    @Suite("Comments")
    struct Comments {

        @Test(arguments: [
            (":hello\ndata: x\n\n", ["hello"]),
            (": hello\ndata: x\n\n", ["hello"]),
            (":  two spaces\ndata: x\n\n", [" two spaces"]),
            (":\ndata: x\n\n", [""]),
            (": a:b\ndata: x\n\n", ["a:b"]),
            (": a\n: b\ndata: x\n\n", ["a", "b"]),
            (": before\ndata: x\n: after\n\n", ["before", "after"]),
        ])
        func commentsAreCollectedOnTheEvent(stream: String, expected: [String]) {
            #expect(parse(stream).map(\.comments) == [expected])
        }

        @Test func keepaliveCommentsDoNotProduceEvents() {
            #expect(parse(":keepalive\n\n:keepalive\n\n: ping\n\n").isEmpty)
        }

        @Test func keepaliveBetweenDataLinesDoesNotSplitEvent() {
            #expect(parse("data: a\n:keepalive\ndata: b\n\n").map(\.data) == ["a\nb"])
        }

        @Test func commentsDoNotCarryIntoNextEvent() {
            let events = parse(": first\ndata: 1\n\ndata: 2\n\n")
            #expect(events.map(\.comments) == [["first"], []])
        }
    }

    // MARK: Line endings

    @Suite("Line endings")
    struct LineEndings {

        @Test(arguments: ["\n", "\r", "\r\n"])
        func allTerminatorsAreSupported(newline: String) {
            let stream = ["id: 1", "event: e", "data: a", "data: b", "", "data: c", "", ""].joined(separator: newline)
            let events = parse(stream)
            #expect(events.map(\.data) == ["a\nb", "c"])
            #expect(events.first?.event == "e")
        }

        @Test func mixedTerminatorsInOneStream() {
            #expect(parse("data: a\rdata: b\ndata: c\r\n\r\ndata: d\n\r").map(\.data) == ["a\nb\nc", "d"])
        }

        @Test func lfThenCrIsTwoLineBreaks() {
            #expect(parse("data: x\n\r").map(\.data) == ["x"])
        }

        @Test func consecutiveCarriageReturnsAreSeparateLines() {
            #expect(parse("data: x\r\r").map(\.data) == ["x"])
        }

        @Test func crlfSplitAcrossChunksIsOneLineBreak() {
            var parser = SSEParser()
            #expect(parser.feed("data: a\r").isEmpty)
            #expect(parser.feed("\ndata: b\r").isEmpty)
            #expect(parser.feed("\n\r\n").map(\.data) == ["a\nb"])
        }

        @Test func crAtChunkEndFollowedByOtherByte() {
            var parser = SSEParser()
            #expect(parser.feed("data: a\r").isEmpty)
            #expect(parser.feed("\r").map(\.data) == ["a"])
        }

        @Test func crlfDoesNotLeakIntoValues() {
            let event = parse("id: 1\r\nevent: e\r\ndata: x\r\n\r\n").first
            #expect(event == ServerSentEvent(id: "1", event: "e", data: "x"))
        }
    }

    // MARK: Chunking

    @Suite("Chunk boundaries")
    struct Chunking {

        static let stream = Array((
            "\u{FEFF}: hi\r\nid: 1\r\nevent: tök\r\nretry: 900\r\ndata: héllo 👋\r\ndata: ✓\r\n\r\n"
            + "data: second\n\n:keepalive\rdata: third\r\r"
        ).utf8)

        static var expected: [ServerSentEvent] {
            [
                ServerSentEvent(id: "1", event: "tök", data: "héllo 👋\n✓", retryMilliseconds: 900, comments: ["hi"]),
                ServerSentEvent(id: "1", data: "second"),
                ServerSentEvent(id: "1", data: "third", comments: ["keepalive"]),
            ]
        }

        @Test func singleChunk() {
            #expect(parse(Self.stream, chunkSize: Self.stream.count) == Self.expected)
        }

        @Test(arguments: [1, 2, 3, 5, 7, 16])
        func fixedSizeChunks(size: Int) {
            #expect(parse(Self.stream, chunkSize: size) == Self.expected)
        }

        @Test func everyTwoWaySplitGivesSameResult() {
            for split in 0...Self.stream.count {
                var parser = SSEParser()
                let events = parser.feed(Array(Self.stream[..<split])) + parser.feed(Array(Self.stream[split...]))
                #expect(events == Self.expected, "split at byte \(split)")
            }
        }

        @Test func emptyChunksAreHarmless() {
            var parser = SSEParser()
            #expect(parser.feed(Data()).isEmpty)
            #expect(parser.feed("data: x").isEmpty)
            #expect(parser.feed(Data()).isEmpty)
            #expect(parser.feed("\n\n").map(\.data) == ["x"])
        }

        @Test func fieldNameSplitAcrossChunks() {
            var parser = SSEParser()
            #expect(parser.feed("da").isEmpty)
            #expect(parser.feed("ta: x\n").isEmpty)
            #expect(parser.feed("\n").map(\.data) == ["x"])
        }
    }

    // MARK: UTF-8

    @Suite("UTF-8 decoding")
    struct UTF8Decoding {

        @Test(arguments: ["é", "한글", "👋", "👨‍👩‍👧", "日本語テキスト"])
        func multibyteCharactersSurviveByteAtATimeFeeding(text: String) {
            #expect(parse(Array("data: \(text)\n\n".utf8), chunkSize: 1).map(\.data) == [text])
        }

        @Test func invalidBytesAreReplaced() {
            let bytes = Array("data: a".utf8) + [0xFF] + Array("b\n\n".utf8)
            #expect(parse(bytes, chunkSize: 100).map(\.data) == ["a\u{FFFD}b"])
        }

        @Test func truncatedSequenceAtLineEndIsReplaced() {
            // First two bytes of the three-byte "한", then the line ends.
            let bytes = Array("data: ".utf8) + [0xED, 0x95] + Array("\n\n".utf8)
            #expect(parse(bytes, chunkSize: 100).map(\.data) == ["\u{FFFD}"])
        }

        @Test func nullCharacterInDataIsKept() {
            #expect(parse("data: a\u{0000}b\n\n").map(\.data) == ["a\u{0000}b"])
        }
    }

    // MARK: BOM

    @Suite("Byte order mark")
    struct ByteOrderMark {

        @Test func leadingBomIsStripped() {
            #expect(parse(byteOrderMark + Array("data: x\n\n".utf8), chunkSize: 100).map(\.data) == ["x"])
        }

        @Test(arguments: [1, 2])
        func bomSplitAcrossChunksIsStripped(splitAt: Int) {
            var parser = SSEParser()
            #expect(parser.feed(Array(byteOrderMark[..<splitAt])).isEmpty)
            #expect(parser.feed(Array(byteOrderMark[splitAt...]) + Array("data: x\n\n".utf8)).map(\.data) == ["x"])
        }

        @Test func onlyOneBomIsStripped() {
            // A second BOM makes the field name "\u{FEFF}data", which is unknown.
            #expect(parse(byteOrderMark + byteOrderMark + Array("data: x\n\n".utf8), chunkSize: 100).isEmpty)
        }

        @Test func bomLaterInStreamIsNotStripped() {
            var parser = SSEParser()
            #expect(parser.feed("data: a\n\n").count == 1)
            #expect(parser.feed(byteOrderMark + Array("data: b\n\n".utf8)).isEmpty)
        }

        @Test func bomAfterLeadingBlankLineIsNotStripped() {
            #expect(parse(Array("\n".utf8) + byteOrderMark + Array("data: x\n\n".utf8), chunkSize: 100).isEmpty)
        }

        @Test func partialBomPrefixIsNotDropped() {
            // 0xEF 0xBB followed by a non-BOM byte must be kept and decoded, not silently removed.
            let bytes: [UInt8] = [0xEF, 0xBB] + Array("data: x\n\n".utf8)
            #expect(parse(bytes, chunkSize: 1).isEmpty)
        }

        @Test func bomInsideValueIsKept() {
            #expect(parse(Array("data: ".utf8) + byteOrderMark + Array("x\n\n".utf8), chunkSize: 100).map(\.data) == ["\u{FEFF}x"])
        }
    }

    // MARK: finish()

    @Suite("finish()")
    struct Finish {

        @Test func discardsIncompleteEvent() {
            var parser = SSEParser()
            #expect(parser.feed("data: complete line\n").isEmpty)
            parser.finish()
            #expect(parser.feed("\n").isEmpty)
        }

        @Test func discardsUnterminatedLine() {
            var parser = SSEParser()
            #expect(parser.feed("data: tail").isEmpty)
            parser.finish()
            #expect(parser.feed("data: next\n\n").map(\.data) == ["next"])
        }

        @Test func incompleteEventIdDoesNotUpdateLastEventId() {
            var parser = SSEParser(initialLastEventId: "1")
            _ = parser.feed("id: 2\ndata: x\n")
            parser.finish()
            #expect(parser.lastEventId == "1")
        }

        @Test func keepsLastEventIdAndReconnectionDelay() {
            var parser = SSEParser()
            _ = parser.feed("id: 9\nretry: 750\ndata: x\n\nid: 10\ndata: partial")
            parser.finish()
            #expect(parser.lastEventId == "9")
            #expect(parser.reconnectionDelayMilliseconds == 750)
            #expect(parser.feed("data: next\n\n").map(\.id) == ["9"])
        }

        @Test func retryFromIncompleteEventStillApplies() {
            var parser = SSEParser()
            _ = parser.feed("retry: 4000\ndata: partial\n")
            parser.finish()
            #expect(parser.reconnectionDelayMilliseconds == 4000)
        }

        @Test func clearsPerEventState() {
            var parser = SSEParser()
            _ = parser.feed(": c\nevent: custom\nretry: 5\ndata: partial\n")
            parser.finish()
            #expect(parser.feed("data: x\n\n") == [ServerSentEvent(data: "x")])
        }

        @Test func clearsPendingCarriageReturn() {
            // Without finish(), the leading "\n" would complete the earlier "\r" as CRLF.
            var parser = SSEParser()
            _ = parser.feed("data: a\r")
            parser.finish()
            #expect(parser.feed("\ndata: b\n\n").map(\.data) == ["b"])
        }

        @Test func allowsBomOnNextConnection() {
            var parser = SSEParser()
            #expect(parser.feed(byteOrderMark + Array("data: a\n\n".utf8)).count == 1)
            parser.finish()
            #expect(parser.feed(byteOrderMark + Array("data: b\n\n".utf8)).map(\.data) == ["b"])
        }

        @Test func isIdempotentAndSafeOnFreshParser() {
            var parser = SSEParser(initialLastEventId: "x")
            parser.finish()
            parser.finish()
            #expect(parser.lastEventId == "x")
            #expect(parser.feed("data: ok\n\n").map(\.data) == ["ok"])
        }
    }

    // MARK: Direct line processing

    @Suite("processLine(_:)")
    struct ProcessLine {

        @Test func processesIndividualLines() {
            var parser = SSEParser()
            #expect(parser.processLine("event: e") == nil)
            #expect(parser.processLine("data: x") == nil)
            #expect(parser.processLine("") == ServerSentEvent(event: "e", data: "x"))
        }
    }

    // MARK: Type semantics

    @Suite("Type semantics")
    struct TypeSemantics {

        @Test func isSendable() {
            let parser: any Sendable = SSEParser()
            #expect(parser is SSEParser)
        }

        @Test func copiesHaveIndependentState() {
            var original = SSEParser()
            _ = original.feed("id: 1\ndata: shared\n")
            var copy = original

            #expect(copy.feed("data: copy\n\n").map(\.data) == ["shared\ncopy"])
            #expect(original.feed("data: original\n\n").map(\.data) == ["shared\noriginal"])
        }

        @Test func canBeUsedAcrossConcurrencyDomains() async {
            let events = await Task.detached {
                var parser = SSEParser()
                return parser.feed("data: from task\n\n")
            }.value
            #expect(events.map(\.data) == ["from task"])
        }
    }
}
