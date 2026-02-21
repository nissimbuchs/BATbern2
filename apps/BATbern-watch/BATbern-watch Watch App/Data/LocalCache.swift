//
//  LocalCache.swift
//  BATbern-watch Watch App
//
//  SwiftData query wrapper for local event cache persistence.
//  Source: docs/watch-app/architecture.md#Public-Zone-Data-Flow
//

import Foundation
import SwiftData

/// SwiftData cache wrapper for event data
class LocalCache {
    private let modelContext: ModelContext

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    // MARK: - Cache Operations

    /// Get the most recently cached event
    func getCachedEvent() -> CachedEvent? {
        let descriptor = FetchDescriptor<CachedEvent>(
            sortBy: [SortDescriptor(\.lastSyncTimestamp, order: .reverse)]
        )

        do {
            let events = try modelContext.fetch(descriptor)
            return events.first
        } catch {
            print("Error fetching cached event: \(error)")
            return nil
        }
    }

    /// Save (upsert) event data to cache
    func saveEvent(_ event: CachedEvent) {
        // Check if event already exists
        let eventCode = event.eventCode
        let descriptor = FetchDescriptor<CachedEvent>(
            predicate: #Predicate<CachedEvent> { cachedEvent in
                cachedEvent.eventCode == eventCode
            }
        )

        do {
            let existingEvents = try modelContext.fetch(descriptor)

            // Delete existing event to replace (upsert pattern)
            for existingEvent in existingEvents {
                modelContext.delete(existingEvent)
            }

            // Insert new event
            modelContext.insert(event)

            // Persist changes
            try modelContext.save()
        } catch {
            print("Error saving event to cache: \(error)")
        }
    }

    /// Clear all cached event data
    func clearCache() {
        let descriptor = FetchDescriptor<CachedEvent>()

        do {
            let events = try modelContext.fetch(descriptor)

            // Delete all events
            for event in events {
                modelContext.delete(event)
            }

            // Persist deletions
            try modelContext.save()
        } catch {
            print("Error clearing cache: \(error)")
        }
    }
}
