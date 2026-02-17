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
        let snapshot = ComplicationDataStore.read()
        completion(ComplicationEntry(date: .now, snapshot: snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ComplicationEntry>) -> Void) {
        let snapshot = ComplicationDataStore.read()

        guard let endTime = snapshot?.scheduledEndTime, snapshot?.isLive == true, endTime > .now else {
            // No active session — single entry, reload only when app signals
            let entry = ComplicationEntry(date: .now, snapshot: snapshot)
            completion(Timeline(entries: [entry], policy: .never))
            return
        }

        // Generate 1-minute entries from now until session end
        var entries: [ComplicationEntry] = []
        var current = Date.now
        while current < endTime {
            entries.append(ComplicationEntry(date: current, snapshot: snapshot))
            current = current.addingTimeInterval(60)
        }
        // Overtime entry at exact end time (shows 0:00 → triggers overtime on next reload)
        entries.append(ComplicationEntry(date: endTime, snapshot: snapshot))

        // Policy .never: main app drives reloads via WidgetCenter.reloadAllTimelines()
        completion(Timeline(entries: entries, policy: .never))
    }
}
