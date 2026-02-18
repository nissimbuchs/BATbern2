//
//  ComplicationProvider.swift
//  BATbern-watch Complications
//
//  WidgetKit TimelineProvider for all BATbern complications.
//  Reads ComplicationSnapshot from App Group UserDefaults (written by main app).
//  W3.3: AC2 — sub-second update via WidgetCenter.reloadAllTimelines(); AC5 fallback.
//
//  TIMELINE STRATEGY (per story dev notes):
//  - Active session: 1-minute entries from now → endTime, policy .never
//    (main app drives reloads via WidgetCenter.reloadAllTimelines() on each tick)
//  - No active session: single entry, policy .never
//    (avoids waking the extension unnecessarily — NFR21/22 battery conservation)
//
//  Source: docs/watch-app/architecture.md#Frontend-Architecture
//

import WidgetKit

struct ComplicationProvider: TimelineProvider {

    func placeholder(in context: Context) -> ComplicationEntry {
        ComplicationEntry(date: .now, snapshot: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (ComplicationEntry) -> Void) {
        let resolved = resolvedSnapshot(ComplicationDataStore.read())
        completion(ComplicationEntry(date: .now, snapshot: resolved))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ComplicationEntry>) -> Void) {
        let resolved = resolvedSnapshot(ComplicationDataStore.read())

        guard let endTime = resolved?.scheduledEndTime, endTime > .now else {
            // No active session, session expired, or stale snapshot — show fallback icon (AC5).
            // `resolved` may be a recent-overtime snapshot (non-nil) or nil; views handle both.
            let entry = ComplicationEntry(date: .now, snapshot: resolved)
            completion(Timeline(entries: [entry], policy: .never))
            return
        }

        // Generate 1-minute entries from now until session end
        var entries: [ComplicationEntry] = []
        var current = Date.now
        while current < endTime {
            entries.append(ComplicationEntry(date: current, snapshot: resolved))
            current = current.addingTimeInterval(60)
        }
        // Entry at exact end time (shows 0:00 → next reload handles overtime or fallback)
        entries.append(ComplicationEntry(date: endTime, snapshot: resolved))

        // Policy .never: main app drives reloads via WidgetCenter.reloadAllTimelines()
        completion(Timeline(entries: entries, policy: .never))
    }

    // MARK: - Staleness Guard

    /// Returns the snapshot to display, or nil if stale (should show fallback icon).
    ///
    /// Rules:
    /// - `isLive: false` or no snapshot → nil
    /// - `endTime > now` (active session) → return snapshot
    /// - `endTime <= now` but `updatedAt` within 5 min (app actively writing, recent overtime) → return snapshot
    /// - Otherwise (stale — app closed, old event) → nil → shows calendar fallback
    private func resolvedSnapshot(_ snapshot: ComplicationSnapshot?) -> ComplicationSnapshot? {
        guard let snapshot, snapshot.isLive else { return nil }
        if let endTime = snapshot.scheduledEndTime, endTime > .now { return snapshot }
        // Recent overtime: the main app is still ticking and writing fresh snapshots.
        // Allow up to 5 minutes of overtime display before reverting to fallback.
        if Date.now.timeIntervalSince(snapshot.updatedAt) < 5 * 60 { return snapshot }
        return nil
    }
}
