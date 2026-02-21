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
    @Test("DTO mapping: EventDetail correctly converts to WatchEvent")
    func test_dtoMapping_eventDetailToWatchEvent() {
        // Given: EventDetail DTO (generated type)
        let dto = EventDetail(
            eventCode: "BATbern57",
            title: "Test Event",
            eventNumber: 57,
            date: Date(timeIntervalSince1970: 1742040000), // 2025-03-15
            registrationDeadline: Date(timeIntervalSince1970: 1742040000),
            venueName: "Bern",
            venueAddress: "Street 1",
            venueCapacity: 100,
            organizerUsername: "test.organizer",
            themeImageUrl: "https://example.com/theme.jpg",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            currentPublishedPhase: .agenda
        )

        // When: Converting to WatchEvent
        let watchEvent = dto.toWatchEvent()

        // Then: Conversion is correct
        #expect(watchEvent.id == "BATbern57", "Event code should match")
        #expect(watchEvent.title == "Test Event", "Title should match")
        #expect(watchEvent.venueName == "Bern", "Venue should match")
        #expect(watchEvent.currentPublishedPhase == "AGENDA", "Phase should match")
    }

    @Test("DTO mapping: EventDetail with sessions converts correctly")
    func test_dtoMapping_eventDetailWithSessions() {
        // Given: Session DTO with speaker (generated types)
        let sessionDTO = Session(
            sessionSlug: "keynote-1",
            eventCode: "BATbern57",
            title: "Opening Keynote",
            description: "Welcome to BATbern",
            sessionType: .keynote,
            startTime: Date(timeIntervalSince1970: 1742040000),
            endTime: Date(timeIntervalSince1970: 1742043600),
            speakers: [
                SessionSpeaker(
                    username: "jane.doe",
                    firstName: "Jane",
                    lastName: "Doe",
                    company: "TechCorp",
                    profilePictureUrl: "https://example.com/jane.jpg",
                    bio: "Expert speaker",
                    speakerRole: .primarySpeaker,
                    isConfirmed: true
                )
            ]
        )

        let dto = EventDetail(
            eventCode: "BATbern57",
            title: "Test Event",
            eventNumber: 57,
            date: Date(timeIntervalSince1970: 1742040000),
            registrationDeadline: Date(timeIntervalSince1970: 1742040000),
            venueName: "Bern",
            venueAddress: "Street 1",
            venueCapacity: 100,
            organizerUsername: "test.organizer",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            sessions: [sessionDTO],
            currentPublishedPhase: .agenda
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
