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
        if entry.snapshot?.isLive == true {
            VStack(alignment: .leading, spacing: 2) {
                // Top: speaker name (gray, truncated to 1 line)
                Text(entry.snapshot?.speakerNames ?? "")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                // Middle: MM:SS bold — "+MM:SS" when overtime (per UX design)
                Text(entry.formattedCountdown)
                    .font(.system(.title3, design: .monospaced).bold())
                    .foregroundStyle(isLuminanceReduced ? .gray : entry.urgencyColor)

                // Bottom: linear progress bar — full bar at 1.0 (overtime) per UX design
                ProgressView(value: entry.progress)
                    .progressViewStyle(.linear)
                    .tint(isLuminanceReduced ? .gray : entry.urgencyColor)
            }
        } else {
            // AC5: No active session fallback
            HStack(spacing: 6) {
                Image(systemName: "calendar.badge.clock")
                    .foregroundStyle(.secondary)
                Text("BATbern")
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
            }
        }
    }

}
