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

    /// Persist a snapshot to the App Group store and ask WidgetKit to refresh.
    /// Must be called from the main app process only.
    static func write(_ snapshot: ComplicationSnapshot) {
        guard let defaults = UserDefaults(suiteName: appGroupID),
              let data = try? encoder.encode(snapshot) else { return }
        defaults.set(data, forKey: snapshotKey)
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
}
