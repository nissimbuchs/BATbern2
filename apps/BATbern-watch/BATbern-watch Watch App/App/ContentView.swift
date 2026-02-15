//
//  ContentView.swift
//  BATbern-watch Watch App
//
//  Root TabView container for dual-zone navigation (public + organizer).
//  Source: docs/watch-app/architecture.md#Structure-Patterns
//

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
                EventHeroView()
            }
            .tag(Zone.publicZone)

            // Tab 1 (right): Organizer zone — authenticated workflows (Epic 2+)
            NavigationStack {
                OrganizerPlaceholderView()
            }
            .tag(Zone.organizer)
        }
        .tabViewStyle(.page)  // Horizontal paging
    }
}

// MARK: - Organizer Placeholder (Epic 2)

struct OrganizerPlaceholderView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "lock.shield")
                .font(.largeTitle)
                .foregroundStyle(.secondary)

            Text("Organizer Zone")
                .font(.headline)

            Text("Available in Epic 2")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
    }
}

#Preview {
    ContentView()
}
