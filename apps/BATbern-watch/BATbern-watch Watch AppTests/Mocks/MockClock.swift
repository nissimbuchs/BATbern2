import Foundation
@testable import BATbern_watch_Watch_App

/// Controllable clock for deterministic timer tests.
/// Advance time manually to simulate watchOS suspension, time passage, etc.
final class MockClock: ClockProtocol, @unchecked Sendable {
    private var _now: Date

    init(fixedDate: Date = Date()) {
        self._now = fixedDate
    }

    var now: Date { _now }

    /// Advance time by the given interval.
    func advance(by interval: TimeInterval) {
        _now = _now.addingTimeInterval(interval)
    }

    /// Set time to a specific date.
    func set(to date: Date) {
        _now = date
    }
}
