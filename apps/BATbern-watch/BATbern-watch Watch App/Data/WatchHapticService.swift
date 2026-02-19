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
/// - 5-min warning: single buzz (.notification × 1)
/// - 2-min warning: double buzz (.notification × 2, 200ms gap)
/// - Time's up / gong: triple buzz (.notification × 3, 150ms gaps)
/// - Overrun pulse: rhythmic pulse (.retry)
/// - Action confirm / connection lost: semantic (.success / .failure)
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
        /// Gap between the two taps of the double-tap pattern (2-min warning). AC2.
        static let doubleTapGap: TimeInterval = 0.2
        /// Gap between tap 1 and tap 2 of the triple-tap pattern (time's up / gong). AC3, AC5.
        static let tripleTapFirstGap: TimeInterval = 0.15
        /// Gap between tap 1 and tap 3 of the triple-tap pattern. AC3, AC5.
        static let tripleTapSecondGap: TimeInterval = 0.30
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
            // Single firm tap — "heads up"
            WKInterfaceDevice.current().play(.notification)

        case .twoMinuteWarning:
            // Double tap — distinctly different from single (AC2).
            // Uses a cancellable Task so stopEventSession() can suppress the delayed tap
            // if the session ends or the view is dismissed within the 200ms window.
            WKInterfaceDevice.current().play(.notification)
            let doubleTap = Task { @MainActor in
                do {
                    try await Task.sleep(nanoseconds: UInt64(HapticTiming.doubleTapGap * 1_000_000_000))
                    WKInterfaceDevice.current().play(.notification)
                } catch {
                    // Cancelled by stopEventSession() — second tap suppressed intentionally.
                }
            }
            pendingHapticTasks.append(doubleTap)

        case .timesUp, .gongReminder:
            // Triple tap — "last call" feel, distinct from 2-min pattern (AC3, AC5).
            // Both delayed taps are cancellable (see doubleTap comment above).
            WKInterfaceDevice.current().play(.notification)
            let tripleTap2 = Task { @MainActor in
                do {
                    try await Task.sleep(nanoseconds: UInt64(HapticTiming.tripleTapFirstGap * 1_000_000_000))
                    WKInterfaceDevice.current().play(.notification)
                } catch { /* Cancelled — tap 2 suppressed. */ }
            }
            let tripleTap3 = Task { @MainActor in
                do {
                    try await Task.sleep(nanoseconds: UInt64(HapticTiming.tripleTapSecondGap * 1_000_000_000))
                    WKInterfaceDevice.current().play(.notification)
                } catch { /* Cancelled — tap 3 suppressed. */ }
            }
            pendingHapticTasks.append(contentsOf: [tripleTap2, tripleTap3])

        case .overtimePulse:
            // Rhythmic pulse — "keep going" feel (AC4)
            WKInterfaceDevice.current().play(.retry)

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
