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

// W3.3 AC4: Notification fired by BATbernWatchApp.onOpenURL when complication is tapped
extension Notification.Name {
    static let openOrganizerZone = Notification.Name("ch.batbern.watch.openOrganizerZone")
}

enum Zone {
    case publicZone
    case organizer
}

struct ContentView: View {
    @State private var selectedZone: Zone = .publicZone
    @State private var isStarting: Bool = true
    @Environment(EventDataController.self) private var eventDataController
    @Environment(WebSocketService.self) private var webSocketService

    var body: some View {
        Group {
            if isStarting {
                // Startup splash: TabView never renders during this phase, so no
                // old spinner can show through. Fades out after ≥1 second.
                BATbernSpinnerView(size: 52)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(.black)
            } else {
                ZStack {
                    TabView(selection: $selectedZone) {
                        // Tab 0 (left, default): Public zone — unauthenticated event browsing
                        NavigationStack {
                            SessionListView()  // W1.2: Vertical paging container (Hero + Session cards)
                        }
                        .tag(Zone.publicZone)

                        // Tab 1 (right): Organizer zone — state-dependent entry (O1/O2/O3)
                        OrganizerZoneView()
                            .tag(Zone.organizer)
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))  // Horizontal paging, no dots
                    .overlay(alignment: .top) {
                        // Single unified connectivity badge — covers every screen in both zones.
                        // Centered; organizer presence count appears here when in organizer zone.
                        ConnectionStatusBar(
                            isOffline: eventDataController.isOffline,
                            lastSynced: eventDataController.lastSynced,
                            presenceCount: webSocketService.presenceCount,
                            isConnected: webSocketService.isConnected
                        )
                        .offset(y: -40)
                    }
                }
            }
        }
        .onAppear {
            selectedZone = .publicZone  // Always launch in Public Zone
            Task {
                try? await Task.sleep(for: .seconds(1))
                withAnimation(.easeOut(duration: 0.3)) {
                    isStarting = false
                }
            }
        }
        // W3.3 AC4: Switch to organizer zone when complication is tapped
        .onReceive(NotificationCenter.default.publisher(for: .openOrganizerZone)) { _ in
            selectedZone = .organizer
        }
    }
}
