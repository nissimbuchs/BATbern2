//
//  CircularComplication.swift
//  BATbern-watch Complications
//
//  C1 — .accessoryCircular complication.
//  W3.3: AC1 (circular ring + minutes), AC3 (always-on dim), AC4 (deep link), AC5 (fallback)
//
//  Design per ux-design-directions.html:
//    - Progress ring fills clockwise, green → yellow → orange → red by urgency
//    - Center: remaining minutes as a number ("24") — overtime shows "+4"
//    - Fallback: calendar icon when no active session
//    - Always-on: .gray tint when isLuminanceReduced
//

import WidgetKit
import SwiftUI

struct CircularComplication: Widget {
    let kind = "BATbernCircular"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ComplicationProvider()) { entry in
            CircularView(entry: entry)
                .widgetURL(URL(string: "batbern-watch://organizer/live"))
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("BATbern Countdown")
        .description("Circular progress ring with remaining minutes.")
        .supportedFamilies([.accessoryCircular])
    }
}

struct CircularView: View {
    let entry: ComplicationEntry
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    var body: some View {
        ZStack {
            if entry.snapshot?.isLive == true {
                // Progress ring — fills as session progresses
                ProgressView(value: entry.progress)
                    .progressViewStyle(.circular)
                    .tint(isLuminanceReduced ? .gray : entry.urgencyColor)
                // Center: minutes ("24") or overtime ("+4") — per UX design
                Text(entry.displayMinutes)
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(isLuminanceReduced ? .gray : entry.urgencyColor)
            } else {
                // AC5: No active session — show BATbern icon fallback
                Image(systemName: "calendar.badge.clock")
                    .foregroundStyle(.secondary)
            }
        }
    }

}
