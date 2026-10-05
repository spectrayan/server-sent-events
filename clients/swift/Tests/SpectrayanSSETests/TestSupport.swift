import Foundation

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

// MARK: - Async helpers

/// Collects up to `limit` elements, then stops iterating.
func collect<S: AsyncSequence>(_ sequence: S, limit: Int) async throws -> [S.Element] {
    var items: [S.Element] = []
    for try await item in sequence {
        items.append(item)
        if items.count == limit { break }
    }
    return items
}

/// Polls `condition` until it holds or the timeout expires.
func waitUntil(timeoutSeconds: TimeInterval = 2, _ condition: () -> Bool) async -> Bool {
    let deadline = Date().addingTimeInterval(timeoutSeconds)
    while !condition() {
        if Date() > deadline { return false }
        try? await Task.sleep(nanoseconds: 5_000_000)
    }
    return true
}
