import Foundation

/// Controls how the client waits between reconnection attempts.
///
/// Delays are stored in milliseconds because `Duration` requires iOS 16 / macOS 13, above this
/// package's deployment targets. Where `Duration` is available, use
/// ``init(initialDelay:maxDelay:multiplier:maxRetries:)`` and the `Duration` accessors.
public struct ReconnectionPolicy: Sendable, Equatable {
    /// Delay before the first reconnection attempt, in milliseconds.
    public let initialDelayMilliseconds: Int
    /// Upper bound for any single delay, in milliseconds.
    public let maxDelayMilliseconds: Int
    public let multiplier: Double
    public let maxRetries: Int?

    public init(
        initialDelayMilliseconds: Int = 1_000,
        maxDelayMilliseconds: Int = 30_000,
        multiplier: Double = 2.0,
        maxRetries: Int? = nil
    ) {
        self.initialDelayMilliseconds = initialDelayMilliseconds
        self.maxDelayMilliseconds = maxDelayMilliseconds
        self.multiplier = multiplier
        self.maxRetries = maxRetries
    }
}

@available(iOS 16, macOS 13, watchOS 9, tvOS 16, *)
extension ReconnectionPolicy {
    /// Creates a policy from `Duration` values, truncated to whole milliseconds.
    public init(
        initialDelay: Duration,
        maxDelay: Duration = .seconds(30),
        multiplier: Double = 2.0,
        maxRetries: Int? = nil
    ) {
        self.init(
            initialDelayMilliseconds: Self.wholeMilliseconds(initialDelay),
            maxDelayMilliseconds: Self.wholeMilliseconds(maxDelay),
            multiplier: multiplier,
            maxRetries: maxRetries
        )
    }

    /// ``initialDelayMilliseconds`` as a `Duration`.
    public var initialDelay: Duration {
        .milliseconds(initialDelayMilliseconds)
    }

    /// ``maxDelayMilliseconds`` as a `Duration`.
    public var maxDelay: Duration {
        .milliseconds(maxDelayMilliseconds)
    }

    private static func wholeMilliseconds(_ duration: Duration) -> Int {
        let (seconds, attoseconds) = duration.components
        return Int(seconds) * 1_000 + Int(attoseconds / 1_000_000_000_000_000)
    }
}

extension ReconnectionPolicy {
    /// Computes the delay before reconnection attempt `attempt` (0 for the first reconnect) using
    /// exponential backoff with full jitter: `random(0...min(base * multiplier^attempt, cap))`.
    ///
    /// - Parameters:
    ///   - serverDelayMilliseconds: The stream's `retry:` value. When present it replaces
    ///     ``initialDelayMilliseconds`` as the base, and the cap is raised to at least this value
    ///     so ``maxDelayMilliseconds`` never overrides the server.
    ///   - randomFactor: A value in `0...1`; injectable for deterministic tests.
    func delayMilliseconds(
        forAttempt attempt: Int,
        serverDelayMilliseconds: Int? = nil,
        randomFactor: Double = .random(in: 0...1)
    ) -> Int {
        let base = Double(serverDelayMilliseconds ?? initialDelayMilliseconds)
        let cap = max(Double(maxDelayMilliseconds), Double(serverDelayMilliseconds ?? 0))
        let exponential = base * pow(max(multiplier, 1), Double(max(attempt, 0)))
        let capped = min(exponential, cap)
        return max(0, Int(capped * min(max(randomFactor, 0), 1)))
    }
}
