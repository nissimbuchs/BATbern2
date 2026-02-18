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
        if entry.snapshot?.isLive == true {
            // Minutes-only per UX design: "24" normal, "+4" overtime
            Text(entry.displayMinutes)
                .font(.system(.title2, design: .monospaced).bold())
                .foregroundStyle(isLuminanceReduced ? .gray : entry.urgencyColor)
        } else {
            // AC5: No active session — minimal icon
            Image(systemName: "calendar")
                .foregroundStyle(.secondary)
        }
    }

}
