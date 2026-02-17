//
//  ContentView.swift
//  BATbern-watch Watch App
//
//  Root TabView container for dual-zone navigation (public + organizer).
//  W2.2: TabView horizontal paging — public zone (left) + organizer zone (right).
//  Source: docs/watch-app/architecture.md#Navigation-Architecture
//

import SwiftData
import SwiftUI

enum Zone {
    case publicZone
    case organizer
}

struct ContentView: View {
    @State private var selectedZone: Zone = .publicZone

    var body: some View {
        TabView(selection: $selectedZone) {
            // Tab 0 (left, default): Public zone — unauthenticated event browsing
            NavigationStack {
                SessionListView()  // W1.2: Vertical paging container (Hero + Session cards)
            }
            .tag(Zone.publicZone)

            // Tab 1 (right): Organizer zone — state-dependent entry (O1/O2/O3)
            // W2.2: Replaced OrganizerPlaceholderView with OrganizerZoneView
            OrganizerZoneView()
                .tag(Zone.organizer)
        }
        .tabViewStyle(.page(indexDisplayMode: .never))  // Horizontal paging, no dots
        .onAppear {
            selectedZone = .publicZone  // Always launch in Public Zone
        }
    }
}

#Preview {
    let container = try! ModelContainer(for: CachedSpeaker.self)
    let auth = AuthManager()
    ContentView()
        .environment(auth)
        .environment(EventStateManager())
        .environment(ArrivalTracker(
            authManager: auth,
            modelContext: container.mainContext
        ))
}
