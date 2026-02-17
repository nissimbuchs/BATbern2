//
//  BATbernWatchApp.swift
//  BATbern-watch Watch App
//
//  App entry point with SwiftData ModelContainer configuration.
//  W2.2: Added AuthManager and EventStateManager as environment objects.
//  Source: docs/watch-app/architecture.md#Data-Architecture
//

import SwiftUI
import SwiftData

@main
struct BATbernWatchApp: App {
    // SwiftData ModelContainer for local cache
    let modelContainer: ModelContainer

    // W2.2: Auth + event state managers injected as environment objects
    @State private var authManager = AuthManager()
    @State private var eventStateManager = EventStateManager()

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

            modelContainer = try ModelContainer(
                for: schema,
                configurations: [modelConfiguration]
            )
        } catch {
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(authManager)
                .environment(eventStateManager)
        }
        .modelContainer(modelContainer)
    }
}
