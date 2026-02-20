//
//  CornerComplication.swift
//  BATbern-watch Complications
//
//  C3 — .accessoryCorner complication.
//  W3.3: AC1 (corner digits), AC3 (always-on dim), AC4 (deep link), AC5 (fallback)
//
//  Design per ux-design-directions.html:
//    - Shows remaining minutes as a single number: "24" normally, "+4" when overtime
//    - Minutes-only (not MM:SS): matches the limited space of .accessoryCorner
//    - Always-on: .gray tint
//    - Note: .accessoryCorner is Series 4+ only — C3 is best-effort
//

import WidgetKit
import SwiftUI

struct CornerComplication: Widget {
    let kind = "BATbernCorner"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ComplicationProvider()) { entry in
            CornerView(entry: entry)
                .widgetURL(URL(string: "batbern-watch://organizer/live"))
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("BATbern Corner")
        .description("Remaining minutes at a glance.")
        .supportedFamilies([.accessoryCorner])
    }
}

struct CornerView: View {
    let entry: ComplicationEntry
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    var body: some View {
        switch entry.context {
        case .noEvent, .eventComplete:
            // AC5: No active session — minimal icon
            Image(systemName: "calendar")
                .foregroundStyle(.secondary)

        case .eventFar(let dateString):
            // Date only, no ring — event is far away
            Text(dateString)
                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                .foregroundStyle(isLuminanceReduced ? .gray : .secondary)

        case .eventDayPreSession(let minutesUntil, _):
            // Minutes/hours until next session (corner has limited space — no ring)
            Text(minutesUntil < 60 ? "\(minutesUntil)m" : "\(minutesUntil / 60)h")
                .font(.system(.title2, design: .monospaced).bold())
                .foregroundStyle(isLuminanceReduced ? .gray : .blue)

        case .sessionRunning:
            // Minutes remaining — count-DOWN (corner has no room for a ring)
            // entry.displayMinutes: "24" normally, "+4" when overtime (no "m" suffix per UX spec)
            Text(entry.displayMinutes)
                .font(.system(.title2, design: .monospaced).bold())
                .foregroundStyle(isLuminanceReduced ? .gray : entry.urgencyColor)
        }
    }

}
