//
//  LiveCountdownViewModel.swift
//  BATbern-watch Watch App
//
//  O3: Live countdown timer ViewModel.
//  W3.1: MVVM presentation logic for LiveCountdownView.
//  Source: docs/watch-app/architecture.md#Implementation-Patterns
//

import Foundation

/// Drives the O3 live countdown display.
///
/// Owns `SessionTimerEngine` and `HapticScheduler` internally.
/// Discovers the active session from `eventState.currentEvent?.sessions` using wall-clock comparison.
/// Publishes formatted time, urgency level, progress ring value, and session metadata.
///
/// Usage:
/// ```swift
/// let vm = LiveCountdownViewModel()
/// vm.eventState = eventStateManager  // set from view's @Environment
/// vm.startTimer()
/// ```
@Observable
@MainActor
final class LiveCountdownViewModel {

    // MARK: - Published State (AC1-AC6, W4.2 amendment)

    private(set) var formattedTime: String = "00:00"
    private(set) var urgencyLevel: UrgencyLevel = .normal
    private(set) var progress: Double = 0
    private(set) var activeSession: WatchSession?
    private(set) var nextSession: WatchSession?
    private(set) var speakerNames: String = ""
    private(set) var sessionTitle: String = ""
    /// W4.2 amendment: true when session is at or past 0:00 — triggers auto-advance in LiveCountdownView.
    private(set) var shouldAutoAdvance: Bool = false

    // MARK: - Injected Dependencies (1.3)

    private let clock: ClockProtocol
    private let hapticService: HapticServiceProtocol

    /// Set by LiveCountdownView from its @Environment after the view resolves its environment.
    var eventState: (any EventStateManagerProtocol)?

    // MARK: - Internal Domain Objects (1.4)

    private let engine: SessionTimerEngine
    private let scheduler: HapticScheduler

    // MARK: - Timer Task

    private var timerTask: Task<Void, Never>?

    // MARK: - Init

    @MainActor
    init(
        clock: ClockProtocol = SystemClock(),
        hapticService: HapticServiceProtocol = WatchHapticService()
    ) {
        self.clock = clock
        self.hapticService = hapticService
        self.engine = SessionTimerEngine(clock: clock)
        self.scheduler = HapticScheduler(clock: clock, hapticService: hapticService)
    }

    // MARK: - Timer Control (1.6, 1.7)

    /// Launch a 1-second tick loop. Idempotent — cancels any existing task first.
    /// Also starts Extended Runtime session for background haptic delivery (AC6/NFR9).
    func startTimer() {
        timerTask?.cancel()
        hapticService.startEventSession()
        timerTask = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                refreshState()
            }
        }
    }

    /// Cancel the tick loop and stop Extended Runtime session. Call from view's onDisappear.
    func stopTimer() {
        timerTask?.cancel()
        timerTask = nil
        hapticService.stopEventSession()
    }

    // MARK: - State Refresh

    /// Recalculate all published state from wall clock. Internal for testability.
    func refreshState() {
        guard let eventState else { return }

        let discovered = findActiveSession(in: eventState)

        // Reset haptic scheduler when session changes
        if discovered?.id != activeSession?.id {
            scheduler.reset()
            shouldAutoAdvance = false
            if let session = discovered {
                engine.setActiveSession(session)
            } else {
                engine.clearActiveSession()
            }
        } else {
            engine.recalculate()
        }

        activeSession = discovered

        if let session = discovered {
            // AC5: break sessions get gong reminder; talk sessions get threshold alerts (W3.2)
            if session.isBreak {
                scheduler.evaluateBreakGong(breakSession: session)
            } else {
                scheduler.evaluate(session: session)
            }
            nextSession = findNextSession(after: session, in: eventState)
        } else {
            nextSession = nil
        }

        // Propagate engine state to published properties (AC1-AC6, W4.2 amendment)
        formattedTime = engine.formattedTime
        urgencyLevel = engine.urgencyLevel
        let newShouldAutoAdvance = (urgencyLevel == .overtime)
        if newShouldAutoAdvance && !shouldAutoAdvance {
            // W4.2 amendment: fire haptic exactly once when session first enters overtime
            hapticService.play(.actionConfirm)
        }
        shouldAutoAdvance = newShouldAutoAdvance
        progress = calculateProgress()
        speakerNames = discovered?.speakers.map { $0.fullName }.joined(separator: ", ") ?? ""
        sessionTitle = discovered?.title ?? ""

        // W3.3: Write snapshot to App Group store so complication extension can read it.
        // isLive is only true when the session has actually started (startTime <= now).
        // Upcoming sessions (startTime > now) must not set isLive:true — the complication
        // would otherwise show a countdown to a future session as if it were active.
        ComplicationDataStore.write(ComplicationSnapshot(
            sessionTitle: discovered?.title,
            speakerNames: formattedSpeakerNames,
            scheduledEndTime: discovered?.endTime,
            sessionDuration: discovered?.duration,
            scheduledStartTime: discovered?.startTime,
            isLive: isComplicationLive(discovered),
            urgencyLevel: engine.urgencyLevel.rawValue,
            updatedAt: clock.now
        ))
    }

    // MARK: - Complication Live State (W3.3)

    /// True when the session has actually started (startTime <= now), whether in-progress or overtime.
    /// Returns false for upcoming sessions (startTime > now) — the complication must not show
    /// a countdown to a future session as if it were live.
    private func isComplicationLive(_ session: WatchSession?) -> Bool {
        guard let session else { return false }
        return session.startTime <= clock.now
    }

    // MARK: - Complication Speaker Names (W3.3)

    /// Last names only, max 2 speakers — fits the narrow C2 rectangular complication.
    /// "Meier" (single) or "Meier, Müller" (two speakers).
    var formattedSpeakerNames: String {
        guard let session = activeSession, !session.speakers.isEmpty else { return "" }
        let lastNames = session.speakers.map { $0.lastName }
        return lastNames.prefix(2).joined(separator: ", ")
    }

    // MARK: - Session Discovery (1.8, 1.9)

    /// Find the active session by wall-clock comparison. (AC6: no drift after suspension)
    ///
    /// 1. In-progress: start <= now <= end
    /// 2. Overtime: most recently ended session (end < now) — engine shows "+MM:SS"
    /// 3. Fallback: first upcoming session not yet started
    private func findActiveSession(in eventState: any EventStateManagerProtocol) -> WatchSession? {
        guard let event = eventState.currentEvent else { return nil }
        let now = clock.now
        let sessions = event.sessions.compactMap { $0.toWatchSession() }

        // In-progress: now is between start and end
        if let active = sessions.first(where: { $0.startTime <= now && $0.endTime >= now }) {
            return active
        }

        // Overtime: session has ended — return most recently ended so engine counts up
        let ended = sessions.filter { $0.startTime <= now && $0.endTime < now }
        if let mostRecent = ended.max(by: { $0.endTime < $1.endTime }) {
            return mostRecent
        }

        // Nothing started yet: first upcoming session
        return sessions.first { $0.startTime > now }
    }

    /// First non-break session that starts after activeSession ends. (1.9)
    private func findNextSession(
        after activeSession: WatchSession,
        in eventState: any EventStateManagerProtocol
    ) -> WatchSession? {
        guard let event = eventState.currentEvent else { return nil }
        let sessions = event.sessions.compactMap { $0.toWatchSession() }
        return sessions.first { $0.startTime > activeSession.endTime && !$0.isBreak }
    }

    // MARK: - Progress (1.10)

    /// Progress ring fill: elapsed / duration, clamped [0, 1]. Overtime pins at 1.0.
    private func calculateProgress() -> Double {
        guard let session = activeSession else { return 0 }
        let duration = session.duration
        guard duration > 0 else { return 0 }
        // 1.0 - (remainingSeconds / duration) == elapsed / duration
        return max(0, min(1, 1.0 - (engine.remainingSeconds / duration)))
    }
}
