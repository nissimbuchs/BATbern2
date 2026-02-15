//
//  PublicEventServiceTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for PublicEventService DTO mapping.
//  NOTE: Full API integration tests removed due to URLSession override limitations.
//  Network behavior validated via integration tests in Xcode Previews.
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("PublicEventService Tests")
struct PublicEventServiceTests {
    @Test("DTO mapping: EventResponse correctly converts to WatchEvent")
    func test_dtoMapping_eventResponseToWatchEvent() {
        // Given: EventResponse DTO
        let dto = EventResponse(
            eventCode: "BATbern57",
            eventNumber: 57,
            title: "Test Event",
            date: "2026-03-15",
            themeImageUrl: "https://example.com/theme.jpg",
            venueName: "Bern",
            venueAddress: "Street 1",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            workflowState: "EVENT_LIVE",
            currentPublishedPhase: "AGENDA",
            sessions: []
        )

        // When: Converting to WatchEvent
        let watchEvent = dto.toWatchEvent()

        // Then: Conversion is correct
        #expect(watchEvent.id == "BATbern57", "Event code should match")
        #expect(watchEvent.title == "Test Event", "Title should match")
        #expect(watchEvent.venueName == "Bern", "Venue should match")
    }

    @Test("DTO mapping: EventResponse with sessions converts correctly")
    func test_dtoMapping_eventResponseWithSessions() {
        // Given: EventResponse with session data
        let sessionDTO = SessionResponse(
            sessionSlug: "keynote-1",
            title: "Opening Keynote",
            description: "Welcome to BATbern",
            sessionType: "keynote",
            startTime: "2026-03-15T18:00:00Z",
            endTime: "2026-03-15T19:00:00Z",
            speakers: [
                SessionSpeakerResponse(
                    username: "jane.doe",
                    firstName: "Jane",
                    lastName: "Doe",
                    company: "TechCorp",
                    profilePictureUrl: "https://example.com/jane.jpg",
                    bio: "Expert speaker",
                    speakerRole: "PRIMARY_SPEAKER"
                )
            ]
        )

        let dto = EventResponse(
            eventCode: "BATbern57",
            eventNumber: 57,
            title: "Test Event",
            date: "2026-03-15",
            themeImageUrl: nil,
            venueName: "Bern",
            venueAddress: nil,
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            workflowState: "EVENT_LIVE",
            currentPublishedPhase: "AGENDA",
            sessions: [sessionDTO]
        )

        // When: Converting to WatchEvent
        let watchEvent = dto.toWatchEvent()

        // Then: Sessions are converted correctly
        #expect(watchEvent.sessions.count == 1, "Should have one session")
        #expect(watchEvent.sessions[0].title == "Opening Keynote", "Session title should match")
        #expect(watchEvent.sessions[0].speakers.count == 1, "Should have one speaker")
        #expect(watchEvent.sessions[0].speakers[0].fullName == "Jane Doe", "Speaker name should match")
    }
}
