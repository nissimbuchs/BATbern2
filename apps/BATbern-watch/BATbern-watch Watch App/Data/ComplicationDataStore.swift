//
//  ComplicationDataStore.swift
//  BATbern-watch Watch App
//
//  Shared data bridge between the main Watch app and the Widget Extension.
//  W3.3: Writes ComplicationSnapshot to App Group UserDefaults so the
//  BATbern Complications extension can read it without accessing the main app process.
//
//  TARGET MEMBERSHIP: Add this file to BOTH:
//    - "BATbern-watch Watch App" target (writer)
//    - "BATbern-watch Complications" target (reader)
//  See Task 1 notes in w3-3-watch-face-complications.md.
//
//  Source: docs/watch-app/architecture.md#Enforcement-Guidelines
//

import Foundation
import WidgetKit

// MARK: - Complication Context

/// Context-aware display state written by `LiveCountdownViewModel` and read by complication views.
///
/// Complications switch on this enum to decide which visual variant to render.
/// Each case encodes exactly the data the view needs — no view logic required to re-derive state.
///
/// Ring semantics (per sprint-change-proposal-2026-02-19):
///   - `.eventDayPreSession` ring: COUNT-UP  — progress = elapsed / totalUntilSessionStart
///   - `.sessionRunning`    ring: COUNT-DOWN — fractionRemaining = timeLeft / duration
///
/// W3.3 Course Correction Amendment (2026-02-19)
enum ComplicationContext: Codable, Equatable, Sendable {
    case noEvent
    case eventFar(dateString: String)                                          // >1 day away: dd.MM format
    case eventDayPreSession(minutesUntil: Int, progress: Double)               // today, between sessions — total minutes until session start
    case sessionRunning(minutesLeft: Int, fractionRemaining: Double)           // active session
    case eventComplete

    // MARK: - Manual Codable (required for associated values)

    private enum CodingKeys: String, CodingKey {
        case type, dateString, minutesUntil, progress, minutesLeft, fractionRemaining
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "noEvent":
            self = .noEvent
        case "eventFar":
            self = .eventFar(dateString: try c.decode(String.self, forKey: .dateString))
        case "eventDayPreSession":
            self = .eventDayPreSession(
                minutesUntil: try c.decode(Int.self, forKey: .minutesUntil),
                progress: try c.decode(Double.self, forKey: .progress)
            )
        case "sessionRunning":
            self = .sessionRunning(
                minutesLeft: try c.decode(Int.self, forKey: .minutesLeft),
                fractionRemaining: try c.decode(Double.self, forKey: .fractionRemaining)
            )
        case "eventComplete":
            self = .eventComplete
        default:
            self = .noEvent
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .noEvent:
            try c.encode("noEvent", forKey: .type)
        case .eventFar(let ds):
            try c.encode("eventFar", forKey: .type)
            try c.encode(ds, forKey: .dateString)
        case .eventDayPreSession(let m, let p):
            try c.encode("eventDayPreSession", forKey: .type)
            try c.encode(m, forKey: .minutesUntil)
            try c.encode(p, forKey: .progress)
        case .sessionRunning(let m, let f):
            try c.encode("sessionRunning", forKey: .type)
            try c.encode(m, forKey: .minutesLeft)
            try c.encode(f, forKey: .fractionRemaining)
        case .eventComplete:
            try c.encode("eventComplete", forKey: .type)
        }
    }
}

// MARK: - Snapshot

/// Immutable snapshot of the current session state, written by the main app
/// and read by the complication extension on every timeline refresh.
struct ComplicationSnapshot: Codable {
    let sessionTitle: String?
    let speakerNames: String?        // e.g. "Meier" or "Meier, Müller" (last names only)
    let scheduledEndTime: Date?      // used to compute remaining on each timeline entry
    let sessionDuration: TimeInterval? // for progress ring: (elapsed / duration)
    let scheduledStartTime: Date?
    let isLive: Bool                  // false → show fallback logo
    let urgencyLevel: String          // UrgencyLevel.rawValue: "normal"|"caution"|"warning"|"critical"|"overtime"
    let updatedAt: Date
    /// Context-aware display hint. Nil in snapshots encoded before this field was introduced
    /// (backward-compatible: auto-decoded as nil when key is absent).
    let complicationContext: ComplicationContext?  // W3.3 amendment
}

// MARK: - Store

/// Static read/write access to the App Group UserDefaults shared between
/// the Watch app and the Complications extension.
///
/// - Writer: `LiveCountdownViewModel.refreshState()` (main app, @MainActor)
/// - Reader: `ComplicationProvider.getTimeline()` (extension process)
enum ComplicationDataStore {
    static let appGroupID = "group.ch.batbern.watch"
    static let snapshotKey = "complication_snapshot"

    // Shared encoder/decoder — avoids allocating new instances on every write (called each second).
    private static let encoder = JSONEncoder()
    private static let decoder = JSONDecoder()

    // MARK: - Production API

    /// Persist a snapshot to the App Group store.
    /// Callers must call `reloadTimeline()` separately when meaningful state changes —
    /// do NOT reload on every write (called each second; battery NFR21/22).
    static func write(_ snapshot: ComplicationSnapshot) {
        guard let defaults = UserDefaults(suiteName: appGroupID),
              let data = try? encoder.encode(snapshot) else { return }
        defaults.set(data, forKey: snapshotKey)
    }

    /// Ask WidgetKit to regenerate all timelines.
    /// Call sparingly — only on context or urgency transitions, not every tick.
    static func reloadTimeline() {
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Read the latest snapshot from the App Group store.
    /// Returns nil when no session data has been written yet.
    static func read() -> ComplicationSnapshot? {
        read(from: UserDefaults(suiteName: appGroupID))
    }

    // MARK: - Injectable API (unit tests)

    /// Write to a caller-supplied UserDefaults. Does NOT trigger WidgetCenter reload.
    /// Use in unit tests to bypass the App Group requirement.
    static func write(_ snapshot: ComplicationSnapshot, to defaults: UserDefaults?) {
        guard let defaults,
              let data = try? encoder.encode(snapshot) else { return }
        defaults.set(data, forKey: snapshotKey)
    }

    /// Read from a caller-supplied UserDefaults.
    /// Use in unit tests to bypass the App Group requirement.
    static func read(from defaults: UserDefaults?) -> ComplicationSnapshot? {
        guard let defaults,
              let data = defaults.data(forKey: snapshotKey),
              let snapshot = try? decoder.decode(ComplicationSnapshot.self, from: data)
        else { return nil }
        return snapshot
    }

    // MARK: - Staleness Logic (shared with ComplicationProvider and tests)

    /// Returns the snapshot to display, applying staleness logic based on `ComplicationContext`.
    ///
    /// Rules (W3.3 amendment):
    /// - `.sessionRunning` (or pre-amendment `isLive:true`): staleness guard applies —
    ///   discard if endTime expired AND updatedAt > 5 min ago (app closed / old event)
    /// - All other contexts: always pass through (date/hours info always valid to show)
    /// - No snapshot → nil
    ///
    /// Extracted here so `ComplicationProvider` and test code share the same implementation.
    static func resolvedSnapshot(_ snapshot: ComplicationSnapshot?, now: Date = .now) -> ComplicationSnapshot? {
        guard let snapshot else { return nil }

        // Infer context for pre-amendment snapshots that lack `complicationContext`
        let context = snapshot.complicationContext
            ?? (snapshot.isLive ? .sessionRunning(minutesLeft: 0, fractionRemaining: 0) : .noEvent)

        switch context {
        case .sessionRunning:
            // Apply staleness guard: discard stale session data
            if let endTime = snapshot.scheduledEndTime, endTime > now { return snapshot }
            // Recent overtime: main app is still writing; allow 5 min of overtime display
            if now.timeIntervalSince(snapshot.updatedAt) < 5 * 60 { return snapshot }
            return nil
        default:
            // Non-session contexts: always show (eventFar, eventDayPreSession, etc.)
            return snapshot
        }
    }
}
