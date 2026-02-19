//
//  EventDataControllerApplyServerStateTests.swift
//  BATbern-watch Watch AppTests
//
//  Unit tests for EventDataController.applyServerState().
//  Story W4.1 Task 2.5.
//
//  Verifies:
//  - currentEvent sessions updated with server state
//  - isOffline set to false
//  - lastSynced updated to clock.now
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

@Suite("EventDataController.applyServerState", .serialized)
@MainActor
struct EventDataControllerApplyServerStateTests {

    private func makeController(clock: ClockProtocol = SystemClock()) throws -> (EventDataController, ModelContext) {
        let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: [config])
        let context = ModelContext(container)
        let auth = MockAuthManager(currentJWT: nil)
        let controller = EventDataController(
            authManager: auth,
            modelContext: context,
            clock: clock,
            skipAutoSync: true
        )
        return (controller, context)
    }

    private func makeEventWithSession(slug: String, context: ModelContext) -> (CachedEvent, CachedSession) {
        let session = CachedSession(sessionSlug: slug, title: "Test Talk")
        let event = CachedEvent(
            eventCode: "BAT56",
            title: "BATbern 56",
            eventDate: Date(),
            themeImageUrl: nil,
            venueName: "Uni Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "21:00",
            currentPublishedPhase: nil,
            lastSyncTimestamp: Date()
        )
        event.sessions = [session]
        context.insert(event)
        try? context.save()
        return (event, session)
    }

    // MARK: - Session field mutations

    @Test("applyServerState: updates actualStartTime on matching session")
    func applyServerState_updatesActualStartTime() throws {
        let (controller, context) = try makeController()
        let (event, session) = makeEventWithSession(slug: "cloud-talk", context: context)
        controller.currentEvent = event

        let startTime = Date()
        let update = WatchStateUpdate(
            sessions: [
                SessionStateUpdate(
                    sessionSlug: "cloud-talk",
                    status: "STARTED",
                    actualStartTime: startTime,
                    actualEndTime: nil,
                    overrunMinutes: nil,
                    completedByUsername: nil
                )
            ],
            connectedOrganizers: [],
            serverTimestamp: Date()
        )

        controller.applyServerState(update)

        #expect(session.actualStartTime != nil)
        #expect(session.actualEndTime == nil)
        #expect(session.overrunMinutes == nil)
    }

    @Test("applyServerState: updates all session fields when fully specified")
    func applyServerState_updatesAllSessionFields() throws {
        let (controller, context) = try makeController()
        let (event, session) = makeEventWithSession(slug: "talk-1", context: context)
        controller.currentEvent = event

        let startTime = Date().addingTimeInterval(-2700)
        let endTime = Date().addingTimeInterval(-100)
        let update = WatchStateUpdate(
            sessions: [
                SessionStateUpdate(
                    sessionSlug: "talk-1",
                    status: "ENDED",
                    actualStartTime: startTime,
                    actualEndTime: endTime,
                    overrunMinutes: 5,
                    completedByUsername: "marco.organizer"
                )
            ],
            connectedOrganizers: [],
            serverTimestamp: Date()
        )

        controller.applyServerState(update)

        #expect(session.actualStartTime != nil)
        #expect(session.actualEndTime != nil)
        #expect(session.overrunMinutes == 5)
        #expect(session.completedByUsername == "marco.organizer")
    }

    @Test("applyServerState: ignores updates for unknown sessionSlug")
    func applyServerState_ignoresUnknownSlug() throws {
        let (controller, context) = try makeController()
        let (event, session) = makeEventWithSession(slug: "known-talk", context: context)
        controller.currentEvent = event

        let update = WatchStateUpdate(
            sessions: [
                SessionStateUpdate(
                    sessionSlug: "unknown-talk",
                    status: "STARTED",
                    actualStartTime: Date(),
                    actualEndTime: nil,
                    overrunMinutes: nil,
                    completedByUsername: nil
                )
            ],
            connectedOrganizers: [],
            serverTimestamp: Date()
        )

        controller.applyServerState(update)

        // Known session remains unchanged
        #expect(session.actualStartTime == nil)
    }

    // MARK: - isOffline and lastSynced

    @Test("applyServerState: sets isOffline to false")
    func applyServerState_setsIsOfflineFalse() throws {
        let (controller, context) = try makeController()
        let (event, _) = makeEventWithSession(slug: "talk-1", context: context)
        controller.currentEvent = event

        // Simulate offline state
        controller.isOffline = true

        controller.applyServerState(WatchStateUpdate(sessions: [], connectedOrganizers: [], serverTimestamp: Date()))

        #expect(controller.isOffline == false)
    }

    @Test("applyServerState: updates lastSynced to clock.now")
    func applyServerState_updatesLastSynced() throws {
        let clock = MockClock(fixedDate: Date(timeIntervalSince1970: 1_000_000))
        let (controller, context) = try makeController(clock: clock)
        let (event, _) = makeEventWithSession(slug: "talk-1", context: context)
        controller.currentEvent = event

        controller.applyServerState(WatchStateUpdate(sessions: [], connectedOrganizers: [], serverTimestamp: clock.now))

        #expect(controller.lastSynced == clock.now)
    }

    @Test("applyServerState: does nothing when currentEvent is nil")
    func applyServerState_noopWhenNoCurrentEvent() throws {
        let (controller, _) = try makeController()
        // currentEvent is nil (no event loaded)

        // Should not crash
        controller.applyServerState(WatchStateUpdate(
            sessions: [SessionStateUpdate(sessionSlug: "any", status: "STARTED", actualStartTime: nil, actualEndTime: nil, overrunMinutes: nil, completedByUsername: nil)],
            connectedOrganizers: [],
            serverTimestamp: Date()
        ))
    }

    // MARK: - W4.3 Schedule Cascade (Task 6.4)

    @Test("SESSION_EXTENDED: scheduledEndTime updated on active session + downstream shifted")
    func applyServerState_sessionExtended_updatesScheduledTimes() throws {
        let (controller, context) = try makeController()
        let activeSession = CachedSession(
            sessionSlug: "cloud-talk", title: "Cloud Talk",
            startTime: Date(), endTime: Date().addingTimeInterval(2700)
        )
        let downstreamSession = CachedSession(
            sessionSlug: "next-talk", title: "Next Talk",
            startTime: Date().addingTimeInterval(2700), endTime: Date().addingTimeInterval(5400)
        )
        let event = CachedEvent(
            eventCode: "BAT56", title: "BATbern 56", eventDate: Date(),
            venueName: "Uni Bern", typicalStartTime: "18:00", typicalEndTime: "21:00"
        )
        event.sessions = [activeSession, downstreamSession]
        context.insert(event)
        try? context.save()
        controller.currentEvent = event

        let newEnd = Date().addingTimeInterval(3300)
        let newDownstreamStart = Date().addingTimeInterval(3300)
        let newDownstreamEnd = Date().addingTimeInterval(6000)
        let update = WatchStateUpdate(
            sessions: [
                SessionStateUpdate(sessionSlug: "cloud-talk", status: "ACTIVE",
                                    newScheduledEndTime: newEnd),
                SessionStateUpdate(sessionSlug: "next-talk", status: "SCHEDULED",
                                    newScheduledStartTime: newDownstreamStart,
                                    newScheduledEndTime: newDownstreamEnd)
            ],
            connectedOrganizers: [],
            serverTimestamp: Date()
        )

        controller.applyServerState(update)

        #expect(activeSession.endTime == newEnd)
        #expect(downstreamSession.startTime == newDownstreamStart)
        #expect(downstreamSession.endTime == newDownstreamEnd)
    }

    @Test("SESSION_DELAYED: previous session extended, current session actualStartTime cleared")
    func applyServerState_sessionDelayed_resetsCurrentSession() throws {
        let (controller, context) = try makeController()
        let previousSession = CachedSession(
            sessionSlug: "cloud-talk", title: "Cloud Talk",
            startTime: Date().addingTimeInterval(-2700), endTime: Date(),
            actualStartTime: Date().addingTimeInterval(-2700)
        )
        let currentSession = CachedSession(
            sessionSlug: "next-talk", title: "Next Talk",
            startTime: Date(), endTime: Date().addingTimeInterval(2700),
            actualStartTime: Date()
        )
        let event = CachedEvent(
            eventCode: "BAT56", title: "BATbern 56", eventDate: Date(),
            venueName: "Uni Bern", typicalStartTime: "18:00", typicalEndTime: "21:00"
        )
        event.sessions = [previousSession, currentSession]
        context.insert(event)
        try? context.save()
        controller.currentEvent = event

        let newPrevEnd = Date().addingTimeInterval(300) // extended by 5 min
        let newCurrentStart = Date().addingTimeInterval(300)
        let newCurrentEnd = Date().addingTimeInterval(3000)
        let update = WatchStateUpdate(
            sessions: [
                SessionStateUpdate(sessionSlug: "cloud-talk", status: "ACTIVE",
                                    actualStartTime: previousSession.actualStartTime,
                                    newScheduledEndTime: newPrevEnd),
                SessionStateUpdate(sessionSlug: "next-talk", status: "SCHEDULED",
                                    actualStartTime: nil,
                                    newScheduledStartTime: newCurrentStart,
                                    newScheduledEndTime: newCurrentEnd)
            ],
            connectedOrganizers: [],
            serverTimestamp: Date()
        )

        controller.applyServerState(update)

        #expect(previousSession.endTime == newPrevEnd)
        #expect(currentSession.actualStartTime == nil)
        #expect(currentSession.startTime == newCurrentStart)
        #expect(currentSession.endTime == newCurrentEnd)
    }

    @Test("Non-cascade: scheduledStartTime/scheduledEndTime unchanged when nil in update")
    func applyServerState_nilScheduledTimesDoNotOverwrite() throws {
        let (controller, context) = try makeController()
        let originalStart = Date()
        let originalEnd = Date().addingTimeInterval(2700)
        let session = CachedSession(
            sessionSlug: "cloud-talk", title: "Cloud Talk",
            startTime: originalStart, endTime: originalEnd
        )
        let event = CachedEvent(
            eventCode: "BAT56", title: "BATbern 56", eventDate: Date(),
            venueName: "Uni Bern", typicalStartTime: "18:00", typicalEndTime: "21:00"
        )
        event.sessions = [session]
        context.insert(event)
        try? context.save()
        controller.currentEvent = event

        let update = WatchStateUpdate(
            sessions: [
                SessionStateUpdate(sessionSlug: "cloud-talk", status: "ACTIVE",
                                    actualStartTime: Date(),
                                    newScheduledStartTime: nil,
                                    newScheduledEndTime: nil)
            ],
            connectedOrganizers: [],
            serverTimestamp: Date()
        )

        controller.applyServerState(update)

        #expect(session.startTime == originalStart)
        #expect(session.endTime == originalEnd)
    }
}
