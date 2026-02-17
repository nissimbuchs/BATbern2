//
//  EventPreviewView.swift
//  BATbern-watch Watch App
//
//  Organizer zone: No active event / event >1h away state.
//  W2.2: Placeholder shown when paired but no event is imminent.
//

import SwiftUI

struct EventPreviewView: View {
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 36))
                .foregroundStyle(.secondary)

            Text(NSLocalizedString("organizer.no_event.title", comment: "No active event"))
                .font(.headline)

            Text(NSLocalizedString("organizer.no_event.message", comment: "Check back closer"))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

#Preview {
    EventPreviewView()
}
