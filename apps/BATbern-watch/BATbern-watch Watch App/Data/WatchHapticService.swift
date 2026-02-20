//
//  WatchHapticService.swift
//  BATbern-watch Watch App
//
//  Concrete HapticServiceProtocol implementation using WKInterfaceDevice.
//  W3.1: Minimal concrete HapticServiceProtocol implementation.
//  W3.2: Expanded with distinct patterns, scheduled queue, and
//        Extended Runtime session for background haptic delivery (NFR9).
//  Source: docs/watch-app/architecture.md#Implementation-Patterns
//

import WatchKit
import OSLog

/// Plays haptic patterns on Apple Watch via WKInterfaceDevice.
///
/// Each alert type maps to a distinct tactile pattern so organizers can
/// read time state without looking at the screen:
/// - 5-min warning: .notification — medium buzz, "take notice"
/// - 2-min warning: .failure — heavy single bump, unmistakably more urgent than 5-min
/// - Time's up:     .failure + .stop (300ms) — heavy-thud combo, physically unmistakable
/// - Overrun pulse: .failure every 30s — persistent urgency
/// - Gong reminder: .notification × 3 (150/300ms) — "last call" texture distinct from failure
/// - Action confirm / connection lost: .success / .failure
///
/// Design rationale (Option B): type-based distinction rather than count-based.
/// Organizers feel the urgency level from the haptic texture alone (.notification vs .failure vs
/// .failure+.stop) — no counting of taps required. This is more reliable during live events
/// where the watch may receive only a glancing contact.
///
/// Extended Runtime session keeps the app alive in background so haptics
/// fire even when the screen is off (AC6 / NFR9).
///
/// Thread safety: All methods are called from @MainActor context (LiveCountdownViewModel).
/// WKExtendedRuntimeSessionDelegate callbacks are dispatched to the main thread by WatchKit.
/// @unchecked Sendable is used instead of @MainActor because WKExtendedRuntimeSessionDelegate
/// infers @MainActor on the conforming class, which breaks default parameter expressions such as
/// `WatchHapticService()` in LiveCountdownViewModel.init(). The invariant is:
///   - scheduledQueue, extendedSession, pendingHapticTasks — ONLY accessed from @MainActor.
/// Violating this invariant produces a data race that the compiler cannot detect. Do not call
/// these methods from a non-@MainActor context.
final class WatchHapticService: NSObject, HapticServiceProtocol, WKExtendedRuntimeSessionDelegate, @unchecked Sendable {

    // MARK: - Haptic Timing Constants
    //
    // Named to surface their design intent: multiple rapid plays create a distinct
    // multi-tap feel. Centralised here so on-device tuning changes one place only.

    private enum HapticTiming {
        /// Gap between .failure and .stop in the time's-up pattern. AC3.
        static let timesUpStopGap: TimeInterval = 0.3
        /// Gap between tap 1 and tap 2 of the gong triple-tap pattern. AC5.
        static let gongFirstGap: TimeInterval = 0.15
        /// Gap between tap 1 and tap 3 of the gong triple-tap pattern. AC5.
        static let gongSecondGap: TimeInterval = 0.30
    }

    // MARK: - State

    private(set) var scheduledQueue: [(alert: HapticAlert, at: Date)] = []
    private var extendedSession: WKExtendedRuntimeSession?
    /// Pending delayed-tap Tasks for multi-tap haptic patterns (double/triple).
    /// Cancelled by stopEventSession() and cancelAll() so in-flight taps are suppressed
    /// when the session ends or the view is dismissed mid-pattern.
    private var pendingHapticTasks: [Task<Void, Never>] = []
    private let logger = Logger(subsystem: "ch.batbern.watch", category: "WatchHapticService")

    // MARK: - HapticServiceProtocol — Immediate Playback

    func play(_ alert: HapticAlert) {
        switch alert {
        case .fiveMinuteWarning:
            // Medium single buzz — "take notice" (AC1)
            WKInterfaceDevice.current().play(.notification)

        case .twoMinuteWarning:
            // Heavy single bump — physically distinct from .notification (AC2).
            // No multi-tap needed: .failure texture is unmistakably different from .notification.
            WKInterfaceDevice.current().play(.failure)

        case .timesUp:
            // Heavy + thud combo — unmistakable "it's done" feel (AC3).
            // Sequence: .failure (heavy) → 300ms → .stop (sharp thud).
            WKInterfaceDevice.current().play(.failure)
            let stopTap = Task { @MainActor in
                do {
                    try await Task.sleep(nanoseconds: UInt64(HapticTiming.timesUpStopGap * 1_000_000_000))
                    WKInterfaceDevice.current().play(.stop)
                } catch { /* Cancelled by stopEventSession() — stop suppressed intentionally. */ }
            }
            pendingHapticTasks.append(stopTap)

        case .gongReminder:
            // Triple .notification — "last call" texture for break end (AC5).
            // Distinct from timesUp: .notification feel vs .failure/.stop.
            WKInterfaceDevice.current().play(.notification)
            let gongTap2 = Task { @MainActor in
                do {
                    try await Task.sleep(nanoseconds: UInt64(HapticTiming.gongFirstGap * 1_000_000_000))
                    WKInterfaceDevice.current().play(.notification)
                } catch { /* Cancelled — tap 2 suppressed. */ }
            }
            let gongTap3 = Task { @MainActor in
                do {
                    try await Task.sleep(nanoseconds: UInt64(HapticTiming.gongSecondGap * 1_000_000_000))
                    WKInterfaceDevice.current().play(.notification)
                } catch { /* Cancelled — tap 3 suppressed. */ }
            }
            pendingHapticTasks.append(contentsOf: [gongTap2, gongTap3])

        case .overtimePulse:
            // Heavy single — persistent urgency every 30s in overrun (AC4).
            // .failure is more urgent than .retry and matches the elevated urgency state.
            WKInterfaceDevice.current().play(.failure)

        case .actionConfirm:
            WKInterfaceDevice.current().play(.success)

        case .connectionLost:
            WKInterfaceDevice.current().play(.failure)
        }
    }

    // MARK: - HapticServiceProtocol — Scheduled Queue

    func schedule(_ alert: HapticAlert, at date: Date) {
        let delay = max(0, date.timeIntervalSince(Date()))
        let task = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                guard let self else { return }
                // Guard: if cancel() removed this entry while we slept, suppress play.
                // cancelAll() / stopEventSession() suppress via Task cancellation (catch below).
                guard self.scheduledQueue.contains(where: { $0.alert == alert && $0.at == date }) else {
                    return
                }
                self.scheduledQueue.removeAll { $0.alert == alert && $0.at == date }
                self.play(alert)
            } catch {
                // Cancelled by stopEventSession() or cancelAll() — alert suppressed intentionally.
            }
        }
        scheduledQueue.append((alert: alert, at: date))
        pendingHapticTasks.append(task)
    }

    func cancelAll() {
        pendingHapticTasks.forEach { $0.cancel() }
        pendingHapticTasks.removeAll()
        scheduledQueue.removeAll()
    }

    func cancel(_ alert: HapticAlert) {
        scheduledQueue.removeAll { $0.alert == alert }
    }

    // MARK: - Extended Runtime Session (AC6 / NFR9)

    func startEventSession() {
        #if targetEnvironment(simulator)
        logger.info("Extended runtime session skipped (simulator — device only)")
        #else
        guard extendedSession?.state != .running else { return }
        let session = WKExtendedRuntimeSession()
        session.delegate = self
        session.start()
        extendedSession = session
        logger.info("Extended runtime session starting")
        #endif
    }

    func stopEventSession() {
        cancelAll()
        #if !targetEnvironment(simulator)
        extendedSession?.invalidate()
        extendedSession = nil
        #endif
        logger.info("Extended runtime session stopped")
    }

    // MARK: - WKExtendedRuntimeSessionDelegate

    func extendedRuntimeSessionDidStart(_ extendedRuntimeSession: WKExtendedRuntimeSession) {
        logger.info("Extended runtime session active — haptics will fire in background")
    }

    func extendedRuntimeSession(
        _ extendedRuntimeSession: WKExtendedRuntimeSession,
        didInvalidateWith reason: WKExtendedRuntimeSessionInvalidationReason,
        error: (any Error)?
    ) {
        // Identity check: extendedRuntimeSessionWillExpire may have already cleared extendedSession
        // and started a new session before this callback fires for the OLD session. Without the
        // check, setting nil here would orphan the new session.
        if extendedRuntimeSession === extendedSession {
            extendedSession = nil
        }
        if let error {
            logger.error("Extended runtime session error: \(error.localizedDescription, privacy: .public)")
        }
        logger.warning("Extended runtime session invalidated: \(reason.rawValue, privacy: .public)")
    }

    func extendedRuntimeSessionWillExpire(_ extendedRuntimeSession: WKExtendedRuntimeSession) {
        // Session nearing expiry — restart to maintain coverage for rest of event (3.3).
        // Clear extendedSession BEFORE invalidating: ensures startEventSession()'s guard
        // passes (old session may still report .running) and ensures didInvalidateWith's
        // identity check does NOT clear the newly created session.
        logger.info("Extended runtime session expiring — restarting")
        extendedSession = nil
        extendedRuntimeSession.invalidate()
        startEventSession()
    }
}
