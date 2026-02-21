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
    @Environment(AuthManager.self) private var authManager
    @Environment(EventStateManager.self) private var eventState
    @Environment(EventDataController.self) private var eventDataController
    @State private var selectedPageIndex: Int = 0

    // PublicViewModel is a pure presentation pass-through — no async init.
    // Creating it inline ensures the TabView (and its Crown sequencer) is always
    // present from the very first render, preventing the "CrownSequencer set up
    // without a view property" warning that corrupts page indicator state.
    private var viewModel: PublicViewModel {
        PublicViewModel(eventDataController: eventDataController)
    }

    var body: some View {
        verticalPagingView(vm: viewModel)
    }

    // MARK: - Vertical Paging View (AC#1, AC#4)

    @ViewBuilder
    private func verticalPagingView(vm: PublicViewModel) -> some View {
        TabView(selection: $selectedPageIndex) {
            // Page 0: Event Hero (P1)
            EventHeroView()
                .tag(0)

            // Pages 1..N: Session Cards (P2)
            ForEach(Array(vm.displayableSessions.enumerated()), id: \.element.sessionSlug) { index, session in
                SessionCardView(
                    session: session,
                    phase: vm.event?.currentPublishedPhase,
                    showStatusBadge: authManager.isPaired && eventState.isLive
                )
                .tag(index + 1)
            }
        }
        .tabViewStyle(.verticalPage)  // Crown-driven vertical paging
    }
}

// MARK: - Previews

#Preview("With Sessions") {
    let auth = AuthManager()
    let container = try! ModelContainer(for: CachedEvent.self, CachedSession.self)
    let controller = EventDataController(
        authManager: auth,
        modelContext: container.mainContext
    )
    SessionListView()
        .modelContainer(container)
        .environment(auth)
        .environment(controller)
        .environment(EventStateManager(eventDataController: controller))
}

#Preview("Empty State") {
    let auth = AuthManager()
    let container = try! ModelContainer(for: CachedEvent.self)
    let controller = EventDataController(
        authManager: auth,
        modelContext: container.mainContext
    )
    SessionListView()
        .modelContainer(container)
        .environment(auth)
        .environment(controller)
        .environment(EventStateManager(eventDataController: controller))
}
