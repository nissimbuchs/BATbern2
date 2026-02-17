//
//  OrganizerZoneView.swift
//  BATbern-watch Watch App
//
//  State-dependent entry screen selector for the organizer zone.
//  W2.2: Routes to O1 (PairingView), O2 (SpeakerArrivalView), O3 (LiveCountdownView),
//        or EventPreviewView based on auth + event state.
//  Source: docs/watch-app/architecture.md#Navigation-Architecture
//

import SwiftUI

struct OrganizerZoneView: View {
    @Environment(AuthManager.self) private var authManager
    @Environment(EventStateManager.self) private var eventState

    var body: some View {
        if !authManager.isPaired {
            // O1: Not paired — show pairing screen
            PairingView()
        } else if eventState.isLive {
            // O3: Event active — live countdown (W3.1 placeholder)
            LiveCountdownView()
        } else if eventState.isPreEvent {
            // O2: <1h before event — speaker arrival (W2.4 placeholder)
            SpeakerArrivalView()
        } else {
            // No active event or >1h before event
            EventPreviewView()
        }
    }
}

#Preview {
    OrganizerZoneView()
        .environment(AuthManager())
        .environment(EventStateManager())
}
