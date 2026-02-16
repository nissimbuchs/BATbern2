//
//  LocalCacheTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for LocalCache SwiftData persistence operations.
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

@Suite("LocalCache Tests")
struct LocalCacheTests {
    private var modelContainer: ModelContainer
    private var modelContext: ModelContext

    init() throws {
        // In-memory model container for testing
        let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self, PairingInfo.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        modelContainer = try ModelContainer(for: schema, configurations: [config])
        modelContext = ModelContext(modelContainer)
    }

    @Test("Save event: Successfully stores event in cache")
    func test_saveEvent_storesEventInCache() throws {
        // Given: LocalCache instance and test event
        let cache = LocalCache(modelContext: modelContext)
        let testEvent = CachedEvent(
            eventCode: "BATbern57",
            title: "Cloud Native Evening",
            eventDate: Date(),
            venueName: "Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00"
        )

        // When: Event is saved
        cache.saveEvent(testEvent)

        // Then: Event can be retrieved
        let retrieved = cache.getCachedEvent()
        #expect(retrieved != nil, "Event should be retrieved from cache")
        #expect(retrieved?.eventCode == "BATbern57", "Event code should match")
        #expect(retrieved?.title == "Cloud Native Evening", "Title should match")
    }

    @Test("Upsert behavior: Replaces existing event with same eventCode")
    func test_upsertBehavior_replacesExistingEvent() throws {
        // Given: LocalCache with existing event
        let cache = LocalCache(modelContext: modelContext)
        let originalEvent = CachedEvent(
            eventCode: "BATbern57",
            title: "Original Title",
            eventDate: Date(),
            venueName: "Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00"
        )
        cache.saveEvent(originalEvent)

        // When: New event with same eventCode is saved
        let updatedEvent = CachedEvent(
            eventCode: "BATbern57",
            title: "Updated Title",
            eventDate: Date(),
            venueName: "Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00"
        )
        cache.saveEvent(updatedEvent)

        // Then: Only the updated event exists
        let retrieved = cache.getCachedEvent()
        #expect(retrieved?.title == "Updated Title", "Title should be updated")

        // Verify only one event exists
        let descriptor = FetchDescriptor<CachedEvent>()
        let allEvents = try modelContext.fetch(descriptor)
        #expect(allEvents.count == 1, "Only one event should exist after upsert")
    }

    @Test("Clear cache: Removes all cached events")
    func test_clearCache_removesAllEvents() throws {
        // Given: LocalCache with multiple events
        let cache = LocalCache(modelContext: modelContext)
        cache.saveEvent(CachedEvent(
            eventCode: "BATbern57",
            title: "Event 1",
            eventDate: Date(),
            venueName: "Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00"
        ))
        cache.saveEvent(CachedEvent(
            eventCode: "BATbern58",
            title: "Event 2",
            eventDate: Date(),
            venueName: "Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00"
        ))

        // When: Cache is cleared
        cache.clearCache()

        // Then: No events remain
        let retrieved = cache.getCachedEvent()
        #expect(retrieved == nil, "No events should remain after clearing cache")
    }

    @Test("Get cached event: Returns most recent event by lastSyncTimestamp")
    func test_getCachedEvent_returnsMostRecentEvent() throws {
        // Given: LocalCache with events at different sync times
        let cache = LocalCache(modelContext: modelContext)
        let olderEvent = CachedEvent(
            eventCode: "BATbern56",
            title: "Older Event",
            eventDate: Date(),
            venueName: "Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            lastSyncTimestamp: Date(timeIntervalSinceNow: -3600)  // 1 hour ago
        )
        let newerEvent = CachedEvent(
            eventCode: "BATbern57",
            title: "Newer Event",
            eventDate: Date(),
            venueName: "Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            lastSyncTimestamp: Date()  // Now
        )

        cache.saveEvent(olderEvent)
        cache.saveEvent(newerEvent)

        // When: Getting cached event
        let retrieved = cache.getCachedEvent()

        // Then: Most recent event is returned
        #expect(retrieved?.eventCode == "BATbern57", "Should return most recently synced event")
    }
}
