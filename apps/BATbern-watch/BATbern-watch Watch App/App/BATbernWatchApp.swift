//
//  BATbernWatchApp.swift
//  BATbern-watch Watch App
//
//  App entry point with SwiftData ModelContainer configuration.
//  W2.2: Added AuthManager and EventStateManager as environment objects.
//  W2.4: Added ArrivalTracker as environment object.
//  Source: docs/watch-app/architecture.md#Data-Architecture
//

import SwiftUI
import SwiftData

@main
struct BATbernWatchApp: App {
    // SwiftData ModelContainer for local cache
    let modelContainer: ModelContainer

    // W2.2: Auth + event state managers injected as environment objects
    // W2.4: ArrivalTracker initialized alongside authManager so it shares the real instance
    @State private var authManager: AuthManager
    @State private var eventStateManager = EventStateManager()
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

            // W2.4: Initialize both together so ArrivalTracker gets the real AuthManager instance.
            // Do NOT initialize in body via lazy/optional — mutating @State in body causes infinite re-render.
            let auth = AuthManager()
            _authManager = State(wrappedValue: auth)
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
                .environment(eventStateManager)
                .environment(arrivalTracker)
        }
        .modelContainer(modelContainer)
    }
}
