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
    /// W4.3: true when remaining ≤ 600s and not yet overtime — shows Extend button.
    private(set) var shouldShowExtend: Bool = false
    /// W4.3: true when session active < 600s — shows Delayed button.
    private(set) var shouldShowDelayed: Bool = false
    /// W4.3: seconds since activeSession.actualStartTime.
    private(set) var sessionActiveSeconds: Int = 0
    /// W4.4 AC2: true when ≤60s remain in the active break — triggers "break ending" overlay.
    private(set) var gongOverlayVisible: Bool = false

    // MARK: - Internal Break Gong State

    /// Prevents re-showing the gong overlay on every tick once it has been shown.
    /// Reset on session change (same lifecycle as scheduler.reset()).
    private var gongFiredInCurrentBreak: Bool = false

    // MARK: - Injected Dependencies (1.3)

    private let clock: ClockProtocol
    private let hapticService: HapticServiceProtocol

    /// Set by LiveCountdownView from its @Environment after the view resolves its environment.
    var eventState: (any EventStateManagerProtocol)?

    // MARK: - Internal Domain Objects (1.4)

    private let engine: SessionTimerEngine
    private let scheduler: HapticScheduler

    // MARK: - Notification Service

    private let notificationService = WatchNotificationService()

    // MARK: - Complication Change Tracking (M1: reload only on meaningful state changes)

    /// Last context written to the complication store.
    /// Exposed (internal access) for unit testing via @testable import.
    @ObservationIgnored private(set) var complicationContext: ComplicationContext = .noEvent
    @ObservationIgnored private var lastUrgencyLevel: UrgencyLevel = .normal

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
        notificationService.requestAuthorization()
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
        notificationService.cancelAll()
    }

    // MARK: - State Refresh

    /// Recalculate all published state from wall clock. Internal for testability.
    func refreshState() {
        guard let eventState else { return }

        let discovered = findActiveSession(in: eventState)

        // Reset haptic scheduler only when the active session itself changes.
        // Engine is always refreshed with the latest session (catches schedule cascade
        // from delay/extend broadcasts that update CachedSession.endTime in place).
        if discovered?.id != activeSession?.id {
            scheduler.reset()
            shouldAutoAdvance = false
            shouldShowExtend = false
            shouldShowDelayed = false
            sessionActiveSeconds = 0
            gongFiredInCurrentBreak = false
            gongOverlayVisible = false
        }
        if let session = discovered {
            engine.setActiveSession(session)
        } else {
            engine.clearActiveSession()
        }

        activeSession = discovered

        if let session = discovered {
            // AC5: break sessions get gong reminder; talk sessions get threshold alerts (W3.2)
            if session.isBreak {
                let gonged = scheduler.evaluateBreakGong(breakSession: session)
                // W4.4 AC2: show overlay banner on the first tick where gong fires.
                // gongFiredInCurrentBreak prevents re-triggering on subsequent ticks.
                if !gongFiredInCurrentBreak && gonged.contains(.gongReminder) {
                    gongFiredInCurrentBreak = true
                    gongOverlayVisible = true
                }
            } else {
                let fired = scheduler.evaluate(session: session)
                postNotifications(for: fired, session: session)
            }
            nextSession = findNextSession(after: session, in: eventState)
        } else {
            nextSession = nil
        }

        // Propagate engine state to published properties (AC1-AC6, W4.2 amendment)
        formattedTime = engine.formattedTime
        urgencyLevel = engine.urgencyLevel

        // W4.3: Extend (last 10 min) and Delayed (first 10 min) button visibility
        if let active = activeSession, urgencyLevel != .overtime {
            shouldShowExtend = engine.remainingSeconds <= 600
            // Use actualStartTime when set; fall back to scheduled startTime so the
            // first session (which has no previous session to set actualStartTime) also
            // shows the Delayed button in its first 10 minutes.
            let effectiveStart = active.actualStartTime ?? active.startTime
            sessionActiveSeconds = max(0, Int(clock.now.timeIntervalSince(effectiveStart)))
            shouldShowDelayed = sessionActiveSeconds < 600
        } else {
            shouldShowExtend = false
            shouldShowDelayed = false
            sessionActiveSeconds = 0
        }

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
        let newContext = computeComplicationContext(in: eventState)
        let newUrgencyLevel = engine.urgencyLevel
        // Only attach session timing when the context is an actually-running session — prevents the
        // complication extension from building a live timeline off a stale/past endTime (which is
        // what produced the huge overtime number for an afterglow event).
        let liveForComplication = isComplicationLive(for: newContext)
        ComplicationDataStore.write(ComplicationSnapshot(
            sessionTitle: liveForComplication ? discovered?.title : nil,
            speakerNames: liveForComplication ? formattedSpeakerNames : "",
            scheduledEndTime: liveForComplication ? discovered?.endTime : nil,
            sessionDuration: liveForComplication ? discovered?.duration : nil,
            scheduledStartTime: liveForComplication ? discovered?.startTime : nil,
            isLive: liveForComplication,
            urgencyLevel: newUrgencyLevel.rawValue,
            updatedAt: clock.now,
            complicationContext: newContext
        ))
        // Reload timeline only on meaningful state changes (context or urgency transitions).
        // Avoids calling reloadAllTimelines() 3600×/hour during a live event (NFR21/22).
        if newContext != complicationContext || newUrgencyLevel != lastUrgencyLevel {
            ComplicationDataStore.reloadTimeline()
        }
        complicationContext = newContext
        lastUrgencyLevel = newUrgencyLevel
    }

    // MARK: - Haptic Notifications

    /// Post a local notification for each newly fired threshold alert.
    /// Shown on wrist raise so organizers know why the watch buzzed.
    private func postNotifications(for alerts: [HapticAlert], session: WatchSession) {
        for alert in alerts {
            switch alert {
            case .fiveMinuteWarning:
                notificationService.post(
                    title: "5 minutes remaining",
                    subtitle: session.title,
                    identifier: "batbern-haptic-5min"
                )
            case .twoMinuteWarning:
                notificationService.post(
                    title: "2 minutes remaining",
                    subtitle: session.title,
                    identifier: "batbern-haptic-2min"
                )
            case .timesUp:
                notificationService.post(
                    title: "Time's up!",
                    subtitle: session.title,
                    identifier: "batbern-haptic-timesup"
                )
            default:
                break
            }
        }
    }

    // MARK: - Complication Context (W3.3 amendment)

    /// Only surface the upcoming-event countdown once the next event is within this lead window.
    /// Beyond it the complication shows nothing (feedback 2026-06-20: "nothing until one or two
    /// weeks ahead of the real next event"). Prevents counting down to a far-future event (e.g. the
    /// November edition) months out.
    private static let complicationLeadWindow: TimeInterval = 14 * 24 * 3600  // 2 weeks

    /// A just-ended session keeps showing on the complication (at 0:00 / brief overtime) for this
    /// grace, covering realistic last-session overrun. Beyond it — with no upcoming session — the
    /// event is over and the complication shows nothing. NO afterglow: a session that ended long
    /// ago (e.g. a completed event still returned as `current` by the afterglow API) must never be
    /// treated as live, which previously produced a huge overtime count-up.
    private static let complicationOvertimeGrace: TimeInterval = 30 * 60  // 30 min

    /// Compute the context-aware display state for the complication.
    ///
    /// Rules (per sprint-change-proposal-2026-02-19, amended 2026-06-20 for the next-event window):
    ///   - `.sessionRunning`      — session started AND `now <= endTime + overtimeGrace`
    ///   - `.eventDayPreSession`  — next session within 24h, none running
    ///   - `.eventFar`            — next session 1 day … `leadWindow` away
    ///   - `.noEvent`             — no event loaded, OR next session is beyond the lead window
    ///                              (far future), OR a stale just-completed event (no afterglow)
    ///   - `.eventComplete`       — all sessions ended (renders as the neutral fallback, no number)
    private func computeComplicationContext(in eventState: any EventStateManagerProtocol) -> ComplicationContext {
        guard let event = eventState.currentEvent else { return .noEvent }
        let now = clock.now
        let sessions = event.sessions.compactMap { $0.toWatchSession() }
        guard !sessions.isEmpty else { return .noEvent }
        let sorted = sessions.sorted { $0.startTime < $1.startTime }

        // Active/overtime session — but ONLY within the overtime grace. A session that ended long
        // ago (afterglow / stale `current` event) is NOT live and must not produce an overtime
        // count-up — fall through to the upcoming/complete handling below.
        if let session = activeSession,
           session.startTime <= now,
           now <= session.endTime.addingTimeInterval(Self.complicationOvertimeGrace) {
            let remaining = session.endTime.timeIntervalSince(now)
            let minutesLeft = max(0, Int(remaining / 60))
            let fractionRemaining = session.duration > 0
                ? max(0.0, min(1.0, remaining / session.duration))
                : 0.0
            return .sessionRunning(minutesLeft: minutesLeft, fractionRemaining: fractionRemaining)
        }

        // Next upcoming session (if any). Drives the pre-event display within the lead window.
        guard let next = sorted.first(where: { $0.startTime > now }) else {
            // Nothing upcoming → the event is over. No afterglow: show the neutral fallback.
            return .eventComplete
        }

        let timeUntilNext = next.startTime.timeIntervalSince(now)

        // Beyond the lead window (far-future event) → show nothing until it draws near.
        if timeUntilNext > Self.complicationLeadWindow {
            return .noEvent
        }

        // Within the lead window but more than a day away → show the date (dd.MM).
        if timeUntilNext > 24 * 3600 {
            let formatter = DateFormatter()
            formatter.dateFormat = "dd.MM"
            return .eventFar(dateString: formatter.string(from: next.startTime))
        }

        // Event day / within 24h: pre-session count-up ring
        // progress = elapsed since midnight / session start since midnight
        // Example: session at 16:00, now 08:00 → 8/16 = 0.5 (per ring semantics spec)
        // minutesUntil = total minutes (not hours) so views can show "5m" instead of "0h"
        let minutesUntil = max(0, Int(timeUntilNext / 60))
        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: now)
        let elapsedSinceMidnight = now.timeIntervalSince(startOfDay)
        let sessionStartSinceMidnight = next.startTime.timeIntervalSince(startOfDay)
        let progress = sessionStartSinceMidnight > 0
            ? min(1.0, max(0.0, elapsedSinceMidnight / sessionStartSinceMidnight))
            : 0.0
        return .eventDayPreSession(minutesUntil: minutesUntil, progress: progress)
    }

    // MARK: - Complication Live State (W3.3)

    /// True only when the complication context is an actually-running session — keeps the snapshot's
    /// `isLive` flag consistent with `complicationContext` so a stale/just-completed session can
    /// never be rendered as live (feedback 2026-06-20: no huge overtime number, no afterglow).
    private func isComplicationLive(for context: ComplicationContext) -> Bool {
        if case .sessionRunning = context { return true }
        return false
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
            .sorted { $0.startTime < $1.startTime }
        return sessions.first { $0.startTime > activeSession.startTime && !$0.isBreak }
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
