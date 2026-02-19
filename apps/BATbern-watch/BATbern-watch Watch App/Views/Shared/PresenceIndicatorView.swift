//
//  PresenceIndicatorView.swift
//  BATbern-watch Watch App
//
//  Additive presence indicator showing connected organizer count.
//  Story W4.1 Task 4.1-4.5.
//
//  DESIGN RULE: This view is ADDITIVE alongside ConnectionStatusBar — never a replacement.
//  Source: story W4.1 Design Direction, Area 3 mandate.
//
//  Placement: HStack in LiveCountdownView status bar area, alongside existing ConnectionStatusBar.
//  Styling: 12pt semibold, matches ConnectionStatusBar font weight.
//  Color: .teal when connected, .orange when connection lost.
//

import SwiftUI

struct PresenceIndicatorView: View {

    // MARK: - Inputs

    let presenceCount: Int
    let isConnected: Bool

    // MARK: - Computed Properties (exposed for tests)

    /// SF Symbol name: person.2.fill for >1 organizer, person.fill for exactly 1.
    var systemImageName: String {
        presenceCount > 1 ? "person.2.fill" : "person.fill"
    }

    /// Teal when connected, orange when connection lost.
    var indicatorColor: Color {
        isConnected ? .teal : .orange
    }

    /// Whether the view is visible (hidden when presenceCount == 0).
    var isVisible: Bool {
        presenceCount > 0
    }

    // MARK: - View Body

    var body: some View {
        if presenceCount > 0 {
            HStack(spacing: 3) {
                Image(systemName: systemImageName)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(indicatorColor)

                Text("\(presenceCount)")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(indicatorColor)
            }
            .padding(.trailing, 10)
            .padding(.top, 4)
        }
    }
}

// MARK: - Previews

#Preview("Online - 2 organizers") {
    PresenceIndicatorView(presenceCount: 2, isConnected: true)
}

#Preview("Connection Lost - 1 organizer") {
    PresenceIndicatorView(presenceCount: 1, isConnected: false)
}

#Preview("Hidden - 0 organizers") {
    PresenceIndicatorView(presenceCount: 0, isConnected: true)
}
