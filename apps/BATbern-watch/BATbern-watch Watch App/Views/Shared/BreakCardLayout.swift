//
//  BreakCardLayout.swift
//  BATbern-watch Watch App
//
//  Shared break session card: type-specific SF Symbol icon + session title.
//  Reused by SessionCardView (public zone) and BreakGongView (O5, organizer zone).
//  W4.4 Task 0: Extracted from SessionCardView.breakCardLayout to enable O5 reuse.
//  Source: docs/watch-app/ux-design-specification.md#Session-Card-Pages
//

import SwiftUI

/// Compact break card body: SF Symbol icon + session title text.
///
/// Takes a minimal `(sessionType, title)` interface so it works with both
/// `CachedSession` (public zone) and `WatchSession` (organizer zone, O5).
/// Callers layer context-specific chrome (time slot, status badge) on top.
struct BreakCardLayout: View {
    let sessionType: SessionType?
    let title: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: Self.breakIcon(for: sessionType))
                .font(.system(size: 36))
                .foregroundStyle(.secondary)

            Text(title)
                .font(.system(size: 14, design: .rounded))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .lineLimit(3)
        }
    }

    /// Maps break session type to its SF Symbol icon name.
    /// Mirrors `SessionCardView.breakIcon` (pre-W4.4) and the server-side type set.
    static func breakIcon(for sessionType: SessionType?) -> String {
        switch sessionType {
        case .breakTime, .lunch:
            return "cup.and.saucer.fill"
        case .networking:
            return "person.2.fill"
        default:
            return "questionmark.circle"
        }
    }
}

// MARK: - Previews

#Preview("Coffee Break") {
    BreakCardLayout(sessionType: .breakTime, title: "Coffee Break")
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
}

#Preview("Networking") {
    BreakCardLayout(sessionType: .networking, title: "Apéro & Networking")
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
}

#Preview("Lunch") {
    BreakCardLayout(sessionType: .lunch, title: "Lunch Break")
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
}
