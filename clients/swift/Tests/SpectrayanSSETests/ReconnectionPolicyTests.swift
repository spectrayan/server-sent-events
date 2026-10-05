import Foundation
import Testing
@testable import SpectrayanSSE

@Suite("ReconnectionPolicy")
struct ReconnectionPolicyTests {

    @Test func defaults() {
        let policy = ReconnectionPolicy()
        #expect(policy.initialDelayMilliseconds == 1_000)
        #expect(policy.maxDelayMilliseconds == 30_000)
        #expect(policy.multiplier == 2.0)
        #expect(policy.maxRetries == nil)
    }

    @Test func durationInitializerAndAccessors() {
        guard #available(iOS 16, macOS 13, watchOS 9, tvOS 16, *) else { return }
        let policy = ReconnectionPolicy(initialDelay: .milliseconds(1500), maxDelay: .seconds(60), maxRetries: 5)
        #expect(policy == ReconnectionPolicy(initialDelayMilliseconds: 1500, maxDelayMilliseconds: 60_000, maxRetries: 5))
        #expect(policy.initialDelay == .milliseconds(1500))
        #expect(policy.maxDelay == .seconds(60))
    }

    @Test func durationIsTruncatedToWholeMilliseconds() {
        guard #available(iOS 16, macOS 13, watchOS 9, tvOS 16, *) else { return }
        #expect(ReconnectionPolicy(initialDelay: .microseconds(2_999)).initialDelayMilliseconds == 2)
    }
}

@Suite("Backoff with full jitter")
struct BackoffTests {
    let policy = ReconnectionPolicy(initialDelayMilliseconds: 1_000, maxDelayMilliseconds: 30_000, multiplier: 2)

    @Test(arguments: [(0, 1_000), (1, 2_000), (2, 4_000), (4, 16_000), (5, 30_000), (100, 30_000), (10_000, 30_000)])
    func upperBoundGrowsExponentiallyUntilCap(attempt: Int, expected: Int) {
        #expect(policy.delayMilliseconds(forAttempt: attempt, randomFactor: 1) == expected)
    }

    @Test func jitterScalesBetweenZeroAndUpperBound() {
        #expect(policy.delayMilliseconds(forAttempt: 2, randomFactor: 0) == 0)
        #expect(policy.delayMilliseconds(forAttempt: 2, randomFactor: 0.25) == 1_000)
    }

    @Test func randomDelaysStayInRange() {
        for attempt in 0..<10 {
            let upper = policy.delayMilliseconds(forAttempt: attempt, randomFactor: 1)
            for _ in 0..<50 {
                #expect((0...upper).contains(policy.delayMilliseconds(forAttempt: attempt)))
            }
        }
    }

    @Test func serverRetryReplacesBaseAndRaisesCap() {
        #expect(policy.delayMilliseconds(forAttempt: 0, serverDelayMilliseconds: 5_000, randomFactor: 1) == 5_000)
        #expect(policy.delayMilliseconds(forAttempt: 1, serverDelayMilliseconds: 5_000, randomFactor: 1) == 10_000)
        #expect(policy.delayMilliseconds(forAttempt: 0, serverDelayMilliseconds: 60_000, randomFactor: 1) == 60_000)
        #expect(policy.delayMilliseconds(forAttempt: 3, serverDelayMilliseconds: 60_000, randomFactor: 1) == 60_000)
    }

    @Test func outOfRangeInputsAreClamped() {
        #expect(policy.delayMilliseconds(forAttempt: -1, randomFactor: 1) == 1_000)
        #expect(policy.delayMilliseconds(forAttempt: 1, randomFactor: 5) == 2_000)
        #expect(policy.delayMilliseconds(forAttempt: 1, randomFactor: -1) == 0)
        let shrinking = ReconnectionPolicy(initialDelayMilliseconds: 1_000, multiplier: 0.5)
        #expect(shrinking.delayMilliseconds(forAttempt: 3, randomFactor: 1) == 1_000)
    }
}
