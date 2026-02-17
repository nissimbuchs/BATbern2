//
//  ComplicationEntry.swift
//  BATbern-watch Complications
//
//  Shared WidgetKit TimelineEntry. Holds the snapshot + wall-clock date,
//  computing remaining/overtime from their difference.
//  W3.3: AC1, AC2, AC5
//
//  Color semantics (per ux-design-directions.html legend):
//    normal   → .green  (#22c55e) — On Track (>5 min)
//    caution  → .yellow            — Warning  (2-5 min)
//    warning  → .orange            — Urgent   (<2 min)
//    critical → .orange            — Urgent   (<1 min)
//    overtime → .red               — Time's Up / Overrun
//

import WidgetKit
import SwiftUI

struct ComplicationEntry: TimelineEntry {
    let date: Date
    let snapshot: ComplicationSnapshot?

    // MARK: - Remaining

    /// Remaining seconds at `entry.date`, clamped to 0. Nil when no end time available.
    var remainingSeconds: TimeInterval? {
        guard let end = snapshot?.scheduledEndTime else { return nil }
        return max(0, end.timeIntervalSince(date))
    }

    // MARK: - Overtime

    /// True when `date` has passed the scheduled end time.
    var isOvertime: Bool {
        guard let end = snapshot?.scheduledEndTime else { return false }
        return date > end
    }

    /// Seconds elapsed past the scheduled end time. 0 when not overtime.
    var overtimeSeconds: TimeInterval {
        guard let end = snapshot?.scheduledEndTime, date > end else { return 0 }
        return date.timeIntervalSince(end)
    }

    // MARK: - Formatted Countdown

    /// "MM:SS" normally, "+MM:SS" when overtime.
    /// Used by C2 (rectangular) middle-line display.
    var formattedCountdown: String {
        if isOvertime {
            let over = Int(overtimeSeconds)
            let mins = over / 60
            let secs = over % 60
            return String(format: "+%02d:%02d", mins, secs)
        }
        guard let remaining = remainingSeconds else { return "--:--" }
        let mins = Int(remaining) / 60
        let secs = Int(remaining) % 60
        return String(format: "%02d:%02d", mins, secs)
    }

    // MARK: - Display Minutes

    /// Remaining minutes as a string for C1/C3:
    /// "24" normally, "+4" when overtime.
    /// Matches UX design: C1 circular ring center, C3 corner — minutes-only.
    var displayMinutes: String {
        if isOvertime {
            return "+\(Int(overtimeSeconds) / 60)"
        }
        return "\(remainingMinutes)"
    }

    var remainingMinutes: Int {
        Int((remainingSeconds ?? 0) / 60)
    }

    // MARK: - Progress Ring

    /// Progress fraction [0, 1]: elapsed / duration. Pins at 1.0 when overtime.
    var progress: Double {
        guard let snapshot,
              let duration = snapshot.sessionDuration,
              let start = snapshot.scheduledStartTime,
              duration > 0 else { return 0 }
        let elapsed = date.timeIntervalSince(start)
        return min(1.0, max(0.0, elapsed / duration))
    }
}
