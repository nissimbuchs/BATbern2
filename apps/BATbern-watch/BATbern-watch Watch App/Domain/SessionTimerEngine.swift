import Foundation

/// Calculates countdown remaining time using wall-clock comparison.
///
/// CRITICAL: Never use a decrementing counter — it drifts across watchOS suspensions.
/// This engine recalculates from `clock.now` on every tick, guaranteeing accuracy even
/// after the app is suspended and resumed by watchOS.
///
/// Usage:
/// ```swift
/// let engine = SessionTimerEngine(clock: SystemClock())
/// engine.setActiveSession(session)
/// // On timer tick:
/// engine.recalculate()
/// print(engine.formattedTime) // "23:45"
/// ```
final class SessionTimerEngine {
    private let clock: ClockProtocol

    private(set) var activeSession: WatchSession?
    private(set) var remainingSeconds: TimeInterval = 0
    private(set) var isOvertime: Bool = false
    private(set) var overtimeSeconds: TimeInterval = 0

    init(clock: ClockProtocol = SystemClock()) {
        self.clock = clock
    }

    /// Set the currently active session and calculate initial state.
    func setActiveSession(_ session: WatchSession) {
        self.activeSession = session
        recalculate()
    }

    /// Clear the active session and reset all state.
    func clearActiveSession() {
        self.activeSession = nil
        self.remainingSeconds = 0
        self.isOvertime = false
        self.overtimeSeconds = 0
    }

    /// Recalculate countdown from wall clock. Call on every timer tick.
    func recalculate() {
        guard let session = activeSession else {
            remainingSeconds = 0
            isOvertime = false
            overtimeSeconds = 0
            return
        }

        let remaining = session.endTime.timeIntervalSince(clock.now)

        if remaining > 0 {
            remainingSeconds = remaining
            isOvertime = false
            overtimeSeconds = 0
        } else {
            remainingSeconds = 0
            isOvertime = true
            overtimeSeconds = abs(remaining)
        }
    }

    /// Format remaining time as MM:SS string. Prefixed with "+" during overtime.
    var formattedTime: String {
        let seconds = isOvertime ? overtimeSeconds : remainingSeconds
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        let prefix = isOvertime ? "+" : ""
        return "\(prefix)\(String(format: "%02d:%02d", mins, secs))"
    }

    /// Urgency level based on remaining time, driving UI color transitions.
    /// Urgency level based on remaining time, driving UI color transitions.
    /// Boundaries align with haptic thresholds: ≤300 caution, ≤120 warning, <60 critical.
    var urgencyLevel: UrgencyLevel {
        if isOvertime { return .overtime }
        if remainingSeconds <= 60 { return .critical }
        if remainingSeconds <= 120 { return .warning }
        if remainingSeconds <= 300 { return .caution }
        return .normal
    }
}
