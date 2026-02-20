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
            switch entry.context {
            case .noEvent, .eventComplete:
                // AC5: No active session — show BATbern icon fallback
                Image(systemName: "calendar.badge.clock")
                    .foregroundStyle(.secondary)

            case .eventFar(let dateString):
                // Date in center, no ring (event is >1 day away)
                Text(dateString)
                    .font(.system(size: 13, weight: .semibold, design: .monospaced))
                    .foregroundStyle(isLuminanceReduced ? .gray : .secondary)

            case .eventDayPreSession(let minutesUntil, let progress):
                // Count-UP ring: elapsed / sessionStartFromMidnight
                ProgressView(value: progress)
                    .progressViewStyle(.circular)
                    .tint(isLuminanceReduced ? .gray : .blue)
                // Show "5m" when <60 min, "2h" when ≥60 min — avoids useless "0h" at near-start
                Text(minutesUntil < 60 ? "\(minutesUntil)m" : "\(minutesUntil / 60)h")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(isLuminanceReduced ? .gray : .blue)

            case .sessionRunning(_, let fractionRemaining):
                // Count-DOWN ring: fractionRemaining = timeLeft / duration.
                // Overtime: pin ring to 1.0 (full red ring per AC1) and show "+N" overrun.
                ProgressView(value: entry.isOvertime ? 1.0 : fractionRemaining)
                    .progressViewStyle(.circular)
                    .tint(isLuminanceReduced ? .gray : entry.urgencyColor)
                // entry.displayMinutes: "24" normally, "+4" when overtime
                Text(entry.displayMinutes)
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(isLuminanceReduced ? .gray : entry.urgencyColor)
            }
        }
    }

}
