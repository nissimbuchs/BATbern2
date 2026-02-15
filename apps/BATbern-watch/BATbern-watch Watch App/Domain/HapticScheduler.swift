import Foundation

/// Schedules haptic alerts based on session timing thresholds.
///
/// Evaluates the current time against a session's end time and fires
/// the appropriate haptic pattern. Deduplicates alerts so each type
/// fires at most once per session (except overtime pulse, which repeats).
///
/// Usage:
/// ```swift
/// let scheduler = HapticScheduler(clock: clock, hapticService: hapticService)
/// // On every timer tick:
/// scheduler.evaluate(session: activeSession)
/// // On session change:
/// scheduler.reset()
/// ```
final class HapticScheduler {
    private let clock: ClockProtocol
    private let hapticService: HapticServiceProtocol

    /// Configurable timing thresholds (seconds before session end).
    struct Thresholds {
        var fiveMinute: TimeInterval = 300
        var twoMinute: TimeInterval = 120
        var timesUp: TimeInterval = 0
        var overtimeInterval: TimeInterval = 30
        var gongLeadTime: TimeInterval = 60
    }

    private(set) var thresholds: Thresholds
    private(set) var firedAlerts: Set<HapticAlert> = []

    init(
        clock: ClockProtocol = SystemClock(),
        hapticService: HapticServiceProtocol,
        thresholds: Thresholds = Thresholds()
    ) {
        self.clock = clock
        self.hapticService = hapticService
        self.thresholds = thresholds
    }

    /// Evaluate which haptic alerts should fire for the given session.
    /// Returns only alerts that were newly fired (not previously fired).
    @discardableResult
    func evaluate(session: WatchSession) -> [HapticAlert] {
        let remaining = session.endTime.timeIntervalSince(clock.now)
        var newlyFired: [HapticAlert] = []

        if remaining <= thresholds.fiveMinute && remaining > thresholds.twoMinute {
            if fire(.fiveMinuteWarning) { newlyFired.append(.fiveMinuteWarning) }
        }

        if remaining <= thresholds.twoMinute && remaining > thresholds.timesUp {
            if fire(.twoMinuteWarning) { newlyFired.append(.twoMinuteWarning) }
        }

        if remaining <= thresholds.timesUp && remaining > -thresholds.overtimeInterval {
            if fire(.timesUp) { newlyFired.append(.timesUp) }
        }

        // Overtime pulse fires repeatedly — reset before each evaluation
        if remaining < -thresholds.overtimeInterval {
            firedAlerts.remove(.overtimePulse)
            if fire(.overtimePulse) { newlyFired.append(.overtimePulse) }
        }

        return newlyFired
    }

    /// Evaluate gong reminder for a break session.
    @discardableResult
    func evaluateBreakGong(breakSession: WatchSession) -> [HapticAlert] {
        let remaining = breakSession.endTime.timeIntervalSince(clock.now)
        var newlyFired: [HapticAlert] = []

        if remaining <= thresholds.gongLeadTime && remaining > 0 {
            if fire(.gongReminder) { newlyFired.append(.gongReminder) }
        }

        return newlyFired
    }

    /// Reset all fired alerts. Call when switching to a new session.
    func reset() {
        firedAlerts.removeAll()
    }

    // MARK: - Private

    /// Attempt to fire an alert. Returns true if newly fired (not duplicate).
    private func fire(_ alert: HapticAlert) -> Bool {
        guard !firedAlerts.contains(alert) else { return false }
        firedAlerts.insert(alert)
        hapticService.play(alert)
        return true
    }
}
