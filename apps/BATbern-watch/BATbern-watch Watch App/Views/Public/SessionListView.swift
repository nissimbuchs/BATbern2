//
//  SessionListView.swift
//  BATbern-watch Watch App
//
//  Vertical paging container for Digital Crown scroll through event hero and session cards.
//  Source: W1.2 - Session Card Browsing (AC#1, AC#4)
//

import SwiftUI
import SwiftData

struct SessionListView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: PublicViewModel?
    @State private var selectedPageIndex: Int = 0

    var body: some View {
        Group {
            if let vm = viewModel {
                verticalPagingView(vm: vm)
            } else {
                ProgressView()
                    .tint(.white)
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = PublicViewModel(modelContext: modelContext)
            }
        }
    }

    // MARK: - Vertical Paging View (AC#1, AC#4)

    @ViewBuilder
    private func verticalPagingView(vm: PublicViewModel) -> some View {
        ZStack(alignment: .top) {
            TabView(selection: $selectedPageIndex) {
                // Page 0: Event Hero (P1)
                EventHeroView()
                    .tag(0)

                // Pages 1..N: Session Cards (P2)
                ForEach(Array(vm.displayableSessions.enumerated()), id: \.element.sessionSlug) { index, session in
                    SessionCardView(
                        session: session,
                        phase: vm.event?.currentPublishedPhase
                    )
                    .tag(index + 1)
                }
            }
            .tabViewStyle(.verticalPage)  // Crown-driven vertical paging

            // Connection status bar overlay (AC#4, AC#7)
            // Visible on P1 (EventHero) and P2 (session cards) only
            ConnectionStatusBar(
                isOffline: vm.isOffline,
                lastSynced: vm.lastSynced
            )
        }
    }
}

// MARK: - Previews

#Preview("With Sessions") {
    SessionListView()
        .modelContainer(for: [CachedEvent.self, CachedSession.self], inMemory: true)
}

#Preview("Empty State") {
    SessionListView()
        .modelContainer(for: [CachedEvent.self], inMemory: true)
}
