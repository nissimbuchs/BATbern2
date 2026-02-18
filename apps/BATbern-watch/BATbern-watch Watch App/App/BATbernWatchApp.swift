//
//  BATbernWatchApp.swift
//  BATbern-watch Watch App
//
//  App entry point with SwiftData ModelContainer configuration.
//  W2.2: Added AuthManager and EventStateManager as environment objects.
//  W2.4: Added ArrivalTracker as environment object.
//  Refactor: EventDataController is the unified event data source for both zones.
//  Source: docs/watch-app/architecture.md#Data-Architecture
//

import SwiftUI
import SwiftData

@main
struct BATbernWatchApp: App {
    // SwiftData ModelContainer for local cache
    let modelContainer: ModelContainer

    // Auth + event state managers injected as environment objects.
    // EventDataController is the unified event data source; EventStateManager reads from it.
    // ArrivalTracker initialized alongside authManager so it shares the real instance.
    @State private var authManager: AuthManager
    @State private var eventDataController: EventDataController
    @State private var eventStateManager: EventStateManager
    @State private var arrivalTracker: ArrivalTracker

    init() {
        do {
            // Configure schema with all SwiftData models
            let schema = Schema([
                CachedEvent.self,
                CachedSession.self,
                CachedSpeaker.self,
                PairingInfo.self
            ])

            let modelConfiguration = ModelConfiguration(
                schema: schema,
                isStoredInMemoryOnly: false
            )

            let container = try ModelContainer(
                for: schema,
                configurations: [modelConfiguration]
            )
            modelContainer = container

            // Initialize together so each instance gets the real sibling instance.
            // Do NOT initialize in body via lazy/optional — mutating @State in body causes
            // infinite re-render.
            let auth = AuthManager()
            let controller = EventDataController(
                authManager: auth,
                modelContext: container.mainContext
            )
            _authManager = State(wrappedValue: auth)
            _eventDataController = State(wrappedValue: controller)
            _eventStateManager = State(wrappedValue: EventStateManager(
                eventDataController: controller
            ))
            _arrivalTracker = State(wrappedValue: ArrivalTracker(
                authManager: auth,
                modelContext: container.mainContext
            ))
        } catch {
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(authManager)
                .environment(eventDataController)
                .environment(eventStateManager)
                .environment(arrivalTracker)
                // W3.3 AC4: Complication tap opens O3 (LiveCountdownView)
                .onOpenURL { url in
                    if url.scheme == "batbern-watch",
                       url.host == "organizer",
                       url.path == "/live" {
                        NotificationCenter.default.post(name: .openOrganizerZone, object: nil)
                    }
                }
        }
        .modelContainer(modelContainer)
    }
}
