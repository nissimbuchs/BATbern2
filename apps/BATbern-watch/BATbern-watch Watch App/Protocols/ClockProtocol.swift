import Foundation

/// Injectable clock for testable time-dependent code.
/// Production: `SystemClock()`. Tests: `MockClock(fixedDate:)`.
protocol ClockProtocol: Sendable {
    var now: Date { get }
}

struct SystemClock: ClockProtocol {
    var now: Date { Date() }
}
