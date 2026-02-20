//
//  RectangularComplication.swift
//  BATbern-watch Complications
//
//  C2 — .accessoryRectangular complication.
//  W3.3: AC1 (rectangular layout), AC3 (always-on dim), AC4 (deep link), AC5 (fallback)
//
//  Design per ux-design-directions.html:
//    - Top:    speaker name in gray (.caption, 1 line truncated)
//    - Middle: countdown MM:SS bold monospaced, urgency-colored; "+MM:SS" when overtime
//    - Bottom: linear progress bar, urgency-colored; full red bar when overtime
//    - Always-on: .gray tint, no color
//

import WidgetKit
import SwiftUI

struct RectangularComplication: Widget {
    let kind = "BATbernRectangular"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ComplicationProvider()) { entry in
            RectangularView(entry: entry)
                .widgetURL(URL(string: "batbern-watch://organizer/live"))
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("BATbern Session")
        .description("Speaker name, countdown, and progress bar.")
        .supportedFamilies([.accessoryRectangular])
    }
}

struct RectangularView: View {
    let entry: ComplicationEntry
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    var body: some View {
        switch entry.context {
        case .noEvent, .eventComplete:
            // AC5: No active session fallback
            HStack(spacing: 6) {
                Image(systemName: "calendar.badge.clock")
                    .foregroundStyle(.secondary)
                Text("BATbern")
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
            }

        case .eventFar(let dateString):
            // Date only, no ring
            VStack(alignment: .leading, spacing: 2) {
                Text("Next Event")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text(dateString)
                    .font(.system(.title3, design: .monospaced).bold())
                    .foregroundStyle(isLuminanceReduced ? .gray : .secondary)
            }

        case .eventDayPreSession(let minutesUntil, let progress):
            // Count-UP ring toward session start
            VStack(alignment: .leading, spacing: 2) {
                Text("Next Session")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text(minutesUntil < 60 ? "\(minutesUntil)m" : "\(minutesUntil / 60)h")
                    .font(.system(.title3, design: .monospaced).bold())
                    .foregroundStyle(isLuminanceReduced ? .gray : .blue)
                ProgressView(value: progress)
                    .progressViewStyle(.linear)
                    .tint(isLuminanceReduced ? .gray : .blue)
            }

        case .sessionRunning(_, let fractionRemaining):
            // Count-DOWN ring for running session — live MM:SS via Text(timerInterval:)
            // Layout: 3 rows (same as original) — title · speaker merged on one line
            let parts = [entry.snapshot?.sessionTitle, entry.snapshot?.speakerNames]
                .compactMap { s in (s?.isEmpty == false) ? s : nil }
            let header = parts.joined(separator: " · ")
            VStack(alignment: .leading, spacing: 2) {
                Text(header)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                if let endTime = entry.snapshot?.scheduledEndTime, endTime > Date() {
                    // Live countdown: updates every second automatically (no extra entries needed)
                    Text(timerInterval: Date()...endTime, countsDown: true)
                        .font(.system(.body, design: .monospaced).bold())
                        .foregroundStyle(isLuminanceReduced ? .gray : entry.urgencyColor)
                        .lineLimit(1)
                } else {
                    // Overtime or no end time: static text refreshed by app via WidgetCenter
                    Text(entry.formattedCountdown)
                        .font(.system(.body, design: .monospaced).bold())
                        .foregroundStyle(isLuminanceReduced ? .gray : entry.urgencyColor)
                }
                ProgressView(value: fractionRemaining)
                    .progressViewStyle(.linear)
                    .tint(isLuminanceReduced ? .gray : entry.urgencyColor)
            }
        }
    }

}
