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
    @Environment(AuthManager.self) private var authManager
    @Environment(EventStateManager.self) private var eventState
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
        // TabView(.verticalPage) on watchOS ignores safeAreaInset — ConnectionStatusBar
        // overlays the content. We instead pass statusBarVisible to each SessionCardView
        // so it can add extra top padding to push the time slot below the bar.
        TabView(selection: $selectedPageIndex) {
            // Page 0: Event Hero (P1)
            EventHeroView()
                .tag(0)

            // Pages 1..N: Session Cards (P2)
            ForEach(Array(vm.displayableSessions.enumerated()), id: \.element.sessionSlug) { index, session in
                SessionCardView(
                    session: session,
                    phase: vm.event?.currentPublishedPhase,
                    statusBarVisible: statusBarVisible(vm: vm),
                    showStatusBadge: authManager.isPaired && eventState.isLive
                )
                .tag(index + 1)
            }
        }
        .tabViewStyle(.verticalPage)  // Crown-driven vertical paging
        .safeAreaInset(edge: .top, spacing: 0) {
            // Connection status bar (AC#2, AC#4, AC#7)
            ConnectionStatusBar(
                isOffline: vm.isOffline,
                lastSynced: vm.lastSynced
            )
        }
    }

    /// Mirrors ConnectionStatusBar.shouldShow — true when the bar is visible
    private func statusBarVisible(vm: PublicViewModel) -> Bool {
        if vm.isOffline { return true }
        guard let lastSync = vm.lastSynced else { return false }
        return Date().timeIntervalSince(lastSync) > 15 * 60
    }
}

// MARK: - Previews

#Preview("With Sessions") {
    SessionListView()
        .modelContainer(for: [CachedEvent.self, CachedSession.self], inMemory: true)
        .environment(AuthManager())
        .environment(EventStateManager())
}

#Preview("Empty State") {
    SessionListView()
        .modelContainer(for: [CachedEvent.self], inMemory: true)
        .environment(AuthManager())
        .environment(EventStateManager())
}
