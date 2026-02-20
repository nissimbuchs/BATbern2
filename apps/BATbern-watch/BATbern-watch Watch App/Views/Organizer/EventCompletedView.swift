//
//  EventCompletedView.swift
//  BATbern-watch Watch App
//
//  Shown when the server broadcasts EVENT_COMPLETED for today's event.
//  W4.4 AC4: Replaces LiveCountdownView (O3) when all completeable sessions are done.
//  Source: docs/watch-app/ux-design-specification.md#Organizer-Zone
//

import SwiftUI

struct EventCompletedView: View {
    let eventTitle: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 40))
                .foregroundStyle(.teal)

            Text(NSLocalizedString("event.completed.title", comment: "Event complete heading"))
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)

            if !eventTitle.isEmpty {
                Text(eventTitle)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundStyle(.white.opacity(0.75))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }

            Text(NSLocalizedString("event.completed.subtitle", comment: "Event complete subtitle"))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

// MARK: - Previews

#Preview {
    EventCompletedView(eventTitle: "BATbern Spring 2026")
}
