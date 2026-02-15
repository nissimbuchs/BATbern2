//
//  BATbernWatchApp.swift
//  BATbern-watch Watch App
//
//  App entry point with SwiftData ModelContainer configuration.
//  Source: docs/watch-app/architecture.md#Data-Architecture
//

import SwiftUI
import SwiftData

@main
struct BATbernWatchApp: App {
    // SwiftData ModelContainer for local cache
    let modelContainer: ModelContainer

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
        }
        .modelContainer(modelContainer)
    }
}
