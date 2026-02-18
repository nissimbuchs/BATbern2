//
//  OrganizerZoneView.swift
//  BATbern-watch Watch App
//
//  State-dependent entry screen selector for the organizer zone.
//  W2.2: Routes to O1 (PairingView), O2 (SpeakerArrivalView), O3 (LiveCountdownView),
//        or EventPreviewView based on auth + event state.
//  W2.3: Delegates event sync to EventDataController on appear; shows loading view during sync.
//  Source: docs/watch-app/architecture.md#Navigation-Architecture
//

import SwiftUI
import SwiftData
import os

private let logger = Logger(subsystem: "ch.batbern.watch", category: "OrganizerZoneView")

struct OrganizerZoneView: View {
    @Environment(AuthManager.self) private var authManager
    @Environment(EventStateManager.self) private var eventState
    @Environment(EventDataController.self) private var eventDataController

    var body: some View {
        Group {
            if !authManager.isPaired {
                // O1: Not paired — show pairing screen
                PairingView()
            } else if eventDataController.isLoading && !eventState.hasActiveEvent {
                // AC#2: Show loading view during initial sync (not background refreshes)
                EventLoadingView(
                    eventTitle: eventState.currentEvent?.title ?? "",
                    progress: eventDataController.syncProgress
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
            guard authManager.isPaired else { return }
            if authManager.currentJWT != nil {
                Task { await eventDataController.syncIfNeeded() }
            } else {
                // JWT not yet loaded — wait for onChange, with a 3s fallback
                Task {
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                    if !eventDataController.isLoading {
                        logger.warning("JWT not ready after 3s, attempting sync anyway")
                        await eventDataController.forceSync()
                    }
                }
            }
        }
        .onChange(of: authManager.currentJWT) { _, newJWT in
            // JWT became available or was refreshed — sync if data is stale.
            // EventDataController's 60s cooldown prevents retry loops from
            // 401 → refreshJWT → onChange → 401.
            guard newJWT != nil && authManager.isPaired else { return }
            Task { await eventDataController.syncIfNeeded() }
        }
    }
}

#Preview {
    let container = try! ModelContainer(for: CachedSpeaker.self)
    let auth = AuthManager()
    let controller = EventDataController(
        authManager: auth,
        modelContext: container.mainContext
    )
    OrganizerZoneView()
        .environment(auth)
        .environment(controller)
        .environment(EventStateManager(eventDataController: controller))
        .environment(ArrivalTracker(
            authManager: auth,
            modelContext: container.mainContext
        ))
}
