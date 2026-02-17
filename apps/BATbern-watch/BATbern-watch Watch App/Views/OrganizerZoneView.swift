//
//  OrganizerZoneView.swift
//  BATbern-watch Watch App
//
//  State-dependent entry screen selector for the organizer zone.
//  W2.2: Routes to O1 (PairingView), O2 (SpeakerArrivalView), O3 (LiveCountdownView),
//        or EventPreviewView based on auth + event state.
//  W2.3: Triggers event sync on appear; shows loading view during sync.
//  Source: docs/watch-app/architecture.md#Navigation-Architecture
//

import SwiftUI
import SwiftData
import os

private let logger = Logger(subsystem: "ch.batbern.watch", category: "OrganizerZoneView")

struct OrganizerZoneView: View {
    @Environment(AuthManager.self) private var authManager
    @Environment(EventStateManager.self) private var eventState
    @Environment(\.modelContext) private var modelContext

    @State private var syncService: EventSyncService?
    @State private var isSyncing = false
    @State private var lastSyncAttempt: Date?

    var body: some View {
        Group {
            if !authManager.isPaired {
                // O1: Not paired — show pairing screen
                PairingView()
            } else if isSyncing {
                // AC#2: Show loading view during sync
                EventLoadingView(
                    eventTitle: eventState.currentEvent?.title ?? "",
                    progress: syncService?.syncProgress ?? 0.0
                )
            } else if eventState.isLive {
                // O3: Event active — live countdown (W3.1 placeholder)
                LiveCountdownView()
            } else if eventState.isPreEvent {
                // O2: <1h before event — speaker arrival (W2.4 placeholder)
                SpeakerArrivalView()
            } else {
                // No active event or >1h before event — AC#4 / AC#5
                EventPreviewView()
            }
        }
        .onAppear {
            guard authManager.isPaired && !isSyncing else { return }
            let now = Date()
            // Don't re-sync if we already have fresh live/pre-event data (within 5 min)
            if (eventState.isLive || eventState.isPreEvent),
               let last = lastSyncAttempt,
               now.timeIntervalSince(last) < 300 {
                return
            }
            // Minimum 60s between any syncs to avoid rate-limit floods on tab swipes
            if let last = lastSyncAttempt, now.timeIntervalSince(last) < 60 {
                return
            }
            // Stamp lastSyncAttempt immediately so concurrent onAppear calls skip
            lastSyncAttempt = Date()
            if authManager.currentJWT != nil {
                Task { await performSync() }
            } else {
                // JWT not yet loaded — wait for onChange, with a 3s fallback
                Task {
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                    if !isSyncing {
                        logger.warning("JWT not ready after 3s, attempting sync anyway")
                        await performSync()
                    }
                }
            }
        }
        .onChange(of: authManager.currentJWT) { _, newJWT in
            // JWT became available or was refreshed — sync if data is stale.
            // 60s debounce prevents retry loops from 401 → refreshJWT → onChange → 401.
            guard newJWT != nil && authManager.isPaired && !isSyncing else { return }
            let now = Date()
            if let last = lastSyncAttempt, now.timeIntervalSince(last) < 60 { return }
            Task { await performSync() }
        }
    }

    // MARK: - Sync

    @MainActor
    private func performSync() async {
        isSyncing = true
        lastSyncAttempt = Date()

        if syncService == nil {
            syncService = EventSyncService(
                authManager: authManager,
                modelContext: modelContext
            )
        }

        do {
            try await syncService?.syncActiveEvent()
            if let synced = syncService?.currentEvent {
                eventState.currentEvent = synced
            }
        } catch {
            // Graceful degradation — show EventPreviewView with cached data if available
            logger.error("Event sync failed: \(error.localizedDescription)")
        }

        isSyncing = false
    }
}

#Preview {
    let container = try! ModelContainer(for: CachedSpeaker.self)
    let auth = AuthManager()
    OrganizerZoneView()
        .environment(auth)
        .environment(EventStateManager())
        .environment(ArrivalTracker(
            authManager: auth,
            modelContext: container.mainContext
        ))
}
