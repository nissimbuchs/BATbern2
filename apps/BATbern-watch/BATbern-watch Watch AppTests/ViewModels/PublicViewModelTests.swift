//
//  PublicViewModelTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for PublicViewModel presentation logic.
//  PublicViewModel is now presentation-only — data sourced from EventDataController.
//  Network/cache/connectivity behaviour is tested in EventSyncServiceTests.swift.
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

@Suite("PublicViewModel Tests", .serialized)
@MainActor
struct PublicViewModelTests {

    // MARK: - Helpers

    private func makeContainer() throws -> ModelContainer {
        let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self, PairingInfo.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        return try ModelContainer(for: schema, configurations: [config])
    }

    private func makeController(event: CachedEvent? = nil, modelContext: ModelContext) -> EventDataController {
        if let event = event {
            modelContext.insert(event)
            try? modelContext.save()
        }
        return EventDataController(
            authManager: MockAuthManager(isPaired: false, currentJWT: nil),
            modelContext: modelContext,
            skipAutoSync: true
        )
    }

    // MARK: - displayableSessions

    @Test("displayableSessions filters out placeholder sessions (nil sessionType)")
    func test_displayableSessions_filtersPlaceholders() throws {
        let container = try makeContainer()

        let validSession = CachedSession(
            sessionSlug: "valid",
            title: "Valid Session",
            sessionType: .presentation,
            startTime: Date(),
            endTime: Date().addingTimeInterval(3600)
        )
        let placeholderSession = CachedSession(
            sessionSlug: "placeholder",
            title: "Placeholder",
            sessionType: nil,
            startTime: Date(),
            endTime: Date()
        )
        let event = CachedEvent(
            eventCode: "test",
            title: "Test",
            eventDate: Date(),
            venueName: "Venue",
            typicalStartTime: "14:00",
            typicalEndTime: "18:00",
            sessions: [validSession, placeholderSession]
        )

        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.displayableSessions.count == 1)
        #expect(viewModel.displayableSessions.first?.sessionSlug == "valid")
    }

    @Test("displayableSessions sorts by startTime")
    func test_displayableSessions_sortsByStartTime() throws {
        let container = try makeContainer()
        let now = Date()

        let session3h = CachedSession(
            sessionSlug: "session-3",
            title: "Third",
            sessionType: .presentation,
            startTime: now.addingTimeInterval(3 * 3600),
            endTime: now.addingTimeInterval(4 * 3600)
        )
        let session1h = CachedSession(
            sessionSlug: "session-1",
            title: "First",
            sessionType: .keynote,
            startTime: now.addingTimeInterval(1 * 3600),
            endTime: now.addingTimeInterval(2 * 3600)
        )
        let event = CachedEvent(
            eventCode: "test",
            title: "Test",
            eventDate: now,
            venueName: "Venue",
            typicalStartTime: "14:00",
            typicalEndTime: "18:00",
            sessions: [session3h, session1h]
        )

        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        let displayable = viewModel.displayableSessions
        #expect(displayable.count == 2)
        #expect(displayable[0].sessionSlug == "session-1")
        #expect(displayable[1].sessionSlug == "session-3")
    }

    // MARK: - hasSpeakerPhase

    @Test("hasSpeakerPhase is true for SPEAKERS phase")
    func test_hasSpeakerPhase_trueSPEAKERS() throws {
        let container = try makeContainer()
        let event = CachedEvent(
            eventCode: "test", title: "Test", eventDate: Date(), venueName: "Venue",
            typicalStartTime: "14:00", typicalEndTime: "18:00", currentPublishedPhase: "SPEAKERS"
        )
        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.hasSpeakerPhase == true)
    }

    @Test("hasSpeakerPhase is true for AGENDA phase")
    func test_hasSpeakerPhase_trueAGENDA() throws {
        let container = try makeContainer()
        let event = CachedEvent(
            eventCode: "test", title: "Test", eventDate: Date(), venueName: "Venue",
            typicalStartTime: "14:00", typicalEndTime: "18:00", currentPublishedPhase: "AGENDA"
        )
        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.hasSpeakerPhase == true)
    }

    @Test("hasSpeakerPhase is false for TOPIC phase")
    func test_hasSpeakerPhase_falseTOPIC() throws {
        let container = try makeContainer()
        let event = CachedEvent(
            eventCode: "test", title: "Test", eventDate: Date(), venueName: "Venue",
            typicalStartTime: "14:00", typicalEndTime: "18:00", currentPublishedPhase: "TOPIC"
        )
        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.hasSpeakerPhase == false)
    }

    // MARK: - hasAgendaPhase

    @Test("hasAgendaPhase is true for AGENDA phase")
    func test_hasAgendaPhase_trueForAGENDA() throws {
        let container = try makeContainer()
        let event = CachedEvent(
            eventCode: "test", title: "Test", eventDate: Date(), venueName: "Venue",
            typicalStartTime: "14:00", typicalEndTime: "18:00", currentPublishedPhase: "AGENDA"
        )
        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.hasAgendaPhase == true)
    }

    @Test("hasAgendaPhase is false for SPEAKERS phase")
    func test_hasAgendaPhase_falseForSPEAKERS() throws {
        let container = try makeContainer()
        let event = CachedEvent(
            eventCode: "test", title: "Test", eventDate: Date(), venueName: "Venue",
            typicalStartTime: "14:00", typicalEndTime: "18:00", currentPublishedPhase: "SPEAKERS"
        )
        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.hasAgendaPhase == false)
    }

    // MARK: - isBreakSession

    @Test("isBreakSession returns true for break types")
    func test_isBreakSession_trueForBreaks() throws {
        let container = try makeContainer()
        let controller = makeController(modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        let breakSession = CachedSession(
            sessionSlug: "break", title: "Break",
            sessionType: .breakTime, startTime: Date(), endTime: Date()
        )
        let lunchSession = CachedSession(
            sessionSlug: "lunch", title: "Lunch",
            sessionType: .lunch, startTime: Date(), endTime: Date()
        )
        let networkingSession = CachedSession(
            sessionSlug: "networking", title: "Networking",
            sessionType: .networking, startTime: Date(), endTime: Date()
        )

        #expect(viewModel.isBreakSession(breakSession) == true)
        #expect(viewModel.isBreakSession(lunchSession) == true)
        #expect(viewModel.isBreakSession(networkingSession) == true)
    }

    @Test("isBreakSession returns false for presentation types")
    func test_isBreakSession_falseForPresentations() throws {
        let container = try makeContainer()
        let controller = makeController(modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        let keynote = CachedSession(
            sessionSlug: "keynote", title: "Keynote",
            sessionType: .keynote, startTime: Date(), endTime: Date()
        )
        let presentation = CachedSession(
            sessionSlug: "pres", title: "Presentation",
            sessionType: .presentation, startTime: Date(), endTime: Date()
        )

        #expect(viewModel.isBreakSession(keynote) == false)
        #expect(viewModel.isBreakSession(presentation) == false)
    }

    // MARK: - isTBDEvent (Course Correction 2026-02-19)

    @Test("isTBDEvent is true when title is TBD and sessions are empty")
    func test_isTBDEvent_trueWhenTitleIsTBDAndNoSessions() throws {
        let container = try makeContainer()
        let event = CachedEvent(
            eventCode: "bat99", title: "TBD", eventDate: Date(), venueName: "Bern",
            typicalStartTime: "18:00", typicalEndTime: "21:00", sessions: []
        )
        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.isTBDEvent == true)
    }

    @Test("isTBDEvent is case-insensitive (lowercase tbd matches)")
    func test_isTBDEvent_caseInsensitive() throws {
        let container = try makeContainer()
        let event = CachedEvent(
            eventCode: "bat99", title: "tbd", eventDate: Date(), venueName: "Bern",
            typicalStartTime: "18:00", typicalEndTime: "21:00", sessions: []
        )
        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.isTBDEvent == true)
    }

    @Test("isTBDEvent is false when title is TBD but event has sessions")
    func test_isTBDEvent_falseWhenTitleIsTBDButHasSessions() throws {
        let container = try makeContainer()
        let session = CachedSession(
            sessionSlug: "s1", title: "Talk",
            sessionType: .presentation,
            startTime: Date(), endTime: Date().addingTimeInterval(3600)
        )
        let event = CachedEvent(
            eventCode: "bat99", title: "TBD", eventDate: Date(), venueName: "Bern",
            typicalStartTime: "18:00", typicalEndTime: "21:00", sessions: [session]
        )
        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.isTBDEvent == false)
    }

    @Test("isTBDEvent is false when title is a normal event name")
    func test_isTBDEvent_falseWhenTitleIsNormal() throws {
        let container = try makeContainer()
        let event = CachedEvent(
            eventCode: "bat57", title: "Cloud Native Evening", eventDate: Date(), venueName: "Bern",
            typicalStartTime: "18:00", typicalEndTime: "21:00", sessions: []
        )
        let controller = makeController(event: event, modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.isTBDEvent == false)
    }

    @Test("isTBDEvent is false when no event is loaded")
    func test_isTBDEvent_falseWhenNoEvent() throws {
        let container = try makeContainer()
        let controller = makeController(modelContext: container.mainContext)
        let viewModel = PublicViewModel(eventDataController: controller)

        #expect(viewModel.isTBDEvent == false)
    }
}
