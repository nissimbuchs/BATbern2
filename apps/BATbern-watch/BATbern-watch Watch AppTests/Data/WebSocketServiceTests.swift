//
//  WebSocketServiceTests.swift
//  BATbern-watch Watch AppTests
//
//  Unit tests for WebSocketService orchestration layer.
//  Story W4.1 Task 3.12.
//
//  Verifies:
//  - MockWebSocketClient.emit() → EventDataController.applyServerState called
//  - Disconnect haptic fires exactly once per disconnect event (not per retry)
//  - Reconnect does NOT call authManager.refreshJWT() (MEMORY.md infinite loop prevention)
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

@Suite("WebSocketService", .serialized)
@MainActor
struct WebSocketServiceTests {

    // MARK: - Helpers

    private func makeContainer() throws -> (ModelContainer, ModelContext) {
        let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: [config])
        return (container, ModelContext(container))
    }

    /// Polls `condition` every 10 ms until it returns true or `timeout` elapses.
    /// Replaces Task.sleep(100ms) hardcoded waits — avoids flaky CI timing. (M4 fix)
    private func waitFor(
        _ condition: @MainActor () -> Bool,
        timeout: Duration = .seconds(2),
        description: String = "condition"
    ) async throws {
        let clock = ContinuousClock()
        let deadline = clock.now.advanced(by: timeout)
        while !condition() {
            guard clock.now < deadline else {
                Issue.record("Timeout waiting for: \(description)")
                return
            }
            try await Task.sleep(for: .milliseconds(10))
        }
    }

    // H4 fix: default parameter expressions are evaluated in nonisolated context,
    // causing Swift concurrency warnings when MockAuthManager/MockHapticService
    // initializers require MainActor isolation. Move defaults into function body.
    private func makeService(
        wsClient: MockWebSocketClient? = nil,
        auth: MockAuthManager? = nil,
        haptic: MockHapticService? = nil,
        modelContext: ModelContext
    ) -> (WebSocketService, EventDataController) {
        let wsClient = wsClient ?? MockWebSocketClient()
        let auth = auth ?? MockAuthManager(currentJWT: "test-jwt")
        let haptic = haptic ?? MockHapticService()
        let dataController = EventDataController(
            authManager: auth,
            modelContext: modelContext,
            skipAutoSync: true
        )
        let service = WebSocketService(
            webSocketClient: wsClient,
            eventDataController: dataController,
            authManager: auth,
            hapticService: haptic
        )
        return (service, dataController)
    }

    // MARK: - applyServerState called on state message

    @Test("connect: state update triggers EventDataController.applyServerState")
    func connect_stateUpdateTriggersApplyServerState() async throws {
        let (container, ctx) = try makeContainer()
        _ = container
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, dataController) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        // Insert a cached event with a session so applyServerState has something to update
        let session = CachedSession(sessionSlug: "cloud-talk", title: "Cloud Talk")
        let event = CachedEvent(
            eventCode: "BATbern56",
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
        ctx.insert(event)
        try ctx.save()
        dataController.currentEvent = event

        // Start WebSocket
        await service.connect(eventCode: "BATbern56")

        // Emit a state update with actualStartTime set
        let startTime = Date()
        let stateUpdate = WatchStateUpdate(
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
        let message = EventStateMessage(
            type: .sessionStarted,
            timestamp: Date(),
            stateUpdate: stateUpdate
        )
        wsClient.emit(message)

        try await waitFor(
            { dataController.currentEvent?.sessions.first?.actualStartTime != nil },
            description: "session.actualStartTime set by applyServerState"
        )

        #expect(dataController.currentEvent?.sessions.first?.actualStartTime != nil)
        #expect(dataController.isOffline == false)
    }

    // MARK: - Disconnect haptic fires exactly once

    @Test("disconnect: connection lost haptic fires exactly once per disconnect event")
    func disconnect_connectionLostHapticFiresOnce() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let haptic = MockHapticService()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, haptic: haptic, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        // Simulate disconnect
        wsClient.simulateDisconnect()

        try await waitFor(
            { haptic.playedAlerts.contains(.connectionLost) },
            description: "connectionLost haptic fired"
        )

        let connectionLostCount = haptic.playedAlerts.filter { $0 == .connectionLost }.count
        #expect(connectionLostCount == 1, "connectionLost haptic should fire exactly once per disconnect")
    }

    // MARK: - Reconnect does NOT call refreshJWT

    @Test("reconnect: does NOT call authManager.refreshJWT on connection lost")
    func reconnect_doesNotCallRefreshJWT() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        // Simulate unexpected disconnect
        wsClient.simulateDisconnect()

        // Give service time to process disconnect — use a brief sleep here because
        // the assertion is negative (refreshCallCount stays 0) and waitFor can't
        // poll for the absence of a call. 50ms is sufficient for @MainActor processing.
        try await Task.sleep(for: .milliseconds(50))

        #expect(auth.refreshCallCount == 0, "WebSocketService must NEVER call refreshJWT (infinite loop risk)")
    }

    // MARK: - Presence count updated from state broadcast

    @Test("connect: state update updates presenceCount from connectedOrganizers")
    func connect_presenceCountUpdatedFromStateUpdate() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        let stateUpdate = WatchStateUpdate(
            sessions: [],
            connectedOrganizers: [
                ConnectedOrganizer(username: "marco.organizer", firstName: "Marco"),
                ConnectedOrganizer(username: "lisa.organizer", firstName: "Lisa")
            ],
            serverTimestamp: Date()
        )
        wsClient.emit(EventStateMessage(type: .heartbeat, timestamp: Date(), stateUpdate: stateUpdate))

        try await waitFor(
            { service.presenceCount == 2 },
            description: "presenceCount updated from state broadcast"
        )

        #expect(service.presenceCount == 2)
        #expect(service.connectedOrganizers.count == 2)
    }

    // MARK: - sendAction delegation (W4.2 Task 5.4)

    @Test("sendAction: delegates to webSocketClient.sentActions with correct action")
    func sendAction_delegatesToWebSocketClient() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        let action = WatchAction.endSession(sessionSlug: "cloud-native-pitfalls")
        await service.sendAction(action)

        #expect(wsClient.sentActions.count == 1)
        #expect(wsClient.sentActions.first == action)
    }

    @Test("sendAction: swallows error when client throws (never re-throws to caller)")
    func sendAction_swallowsClientError() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        wsClient.sendActionShouldFail = true
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        // Should not throw — error is logged and swallowed
        await service.sendAction(.endSession(sessionSlug: "cloud-native-pitfalls"))
        // wsClient recorded nothing (it threw before appending)
        #expect(wsClient.sentActions.isEmpty)
    }

    @Test("sendAction: multiple actions are sent in order")
    func sendAction_multipleActionsInOrder() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        await service.sendAction(.endSession(sessionSlug: "talk-1"))
        await service.sendAction(.startSession(sessionSlug: "talk-2"))

        #expect(wsClient.sentActions.count == 2)
        #expect(wsClient.sentActions[0] == .endSession(sessionSlug: "talk-1"))
        #expect(wsClient.sentActions[1] == .startSession(sessionSlug: "talk-2"))
    }

    // MARK: - sessionEndedEvent (W4.2 Task 5.2/5.3)

    @Test("SESSION_ENDED message sets sessionEndedEvent on service")
    func sessionEndedMessage_setsSessionEndedEvent() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        #expect(service.sessionEndedEvent == nil)

        let timestamp = Date()
        let message = EventStateMessage(
            type: .sessionEnded,
            sessionSlug: "cloud-native-pitfalls",
            initiatedBy: "marco.organizer",
            timestamp: timestamp
        )
        wsClient.emit(message)

        try await waitFor(
            { service.sessionEndedEvent != nil },
            description: "sessionEndedEvent set by SESSION_ENDED message"
        )

        #expect(service.sessionEndedEvent?.sessionSlug == "cloud-native-pitfalls")
        #expect(service.sessionEndedEvent?.completedBy == "marco.organizer")
    }

    @Test("Non-SESSION_ENDED message does NOT set sessionEndedEvent")
    func nonSessionEndedMessage_doesNotSetSessionEndedEvent() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        let message = EventStateMessage(
            type: .heartbeat,
            timestamp: Date()
        )
        wsClient.emit(message)

        try await Task.sleep(for: .milliseconds(50))
        #expect(service.sessionEndedEvent == nil)
    }

    @Test("Rapid SESSION_ENDED messages: second is queued and delivered after consume (review fix item 1)")
    func rapidSessionEndedMessages_secondDeliveredAfterConsume() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        let first = EventStateMessage(
            type: .sessionEnded,
            sessionSlug: "talk-1",
            initiatedBy: "marco.organizer",
            timestamp: Date()
        )
        let second = EventStateMessage(
            type: .sessionEnded,
            sessionSlug: "talk-2",
            initiatedBy: "lisa.organizer",
            timestamp: Date()
        )
        // Emit both before .onChange fires
        wsClient.emit(first)
        wsClient.emit(second)

        try await waitFor(
            { service.sessionEndedEvent != nil },
            description: "first sessionEndedEvent set"
        )

        // First event is delivered; second is queued
        #expect(service.sessionEndedEvent?.sessionSlug == "talk-1")

        // Consume first — second should be delivered from queue
        service.consumeSessionEndedEvent()

        try await waitFor(
            { service.sessionEndedEvent?.sessionSlug == "talk-2" },
            description: "second sessionEndedEvent delivered from queue"
        )

        #expect(service.sessionEndedEvent?.sessionSlug == "talk-2")

        // Consume second — queue now empty
        service.consumeSessionEndedEvent()
        #expect(service.sessionEndedEvent == nil)
    }

    @Test("SESSION_ENDED without sessionSlug does NOT set sessionEndedEvent")
    func sessionEndedWithoutSlug_doesNotSetEvent() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        let message = EventStateMessage(
            type: .sessionEnded,
            sessionSlug: nil,  // no slug
            timestamp: Date()
        )
        wsClient.emit(message)

        try await Task.sleep(for: .milliseconds(50))
        #expect(service.sessionEndedEvent == nil)
    }

    // MARK: - SESSION_EXTENDED / SESSION_DELAYED (W4.3 Task 5.8)

    @Test("SESSION_EXTENDED: applyServerState called (schedule update via stateUpdate)")
    func sessionExtended_appliesServerState() async throws {
        let (container, ctx) = try makeContainer()
        _ = container
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, dataController) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        let session = CachedSession(sessionSlug: "cloud-talk", title: "Cloud Talk",
                                     startTime: Date(), endTime: Date().addingTimeInterval(2700))
        let event = CachedEvent(eventCode: "BATbern56", title: "BATbern 56",
                                 eventDate: Date(), venueName: "Uni Bern",
                                 typicalStartTime: "18:00", typicalEndTime: "21:00")
        event.sessions = [session]
        ctx.insert(event)
        try ctx.save()
        dataController.currentEvent = event

        await service.connect(eventCode: "BATbern56")

        let newEnd = Date().addingTimeInterval(3300) // extended by 10 min
        let stateUpdate = WatchStateUpdate(
            sessions: [SessionStateUpdate(sessionSlug: "cloud-talk", status: "ACTIVE",
                                           newScheduledEndTime: newEnd)],
            connectedOrganizers: [],
            serverTimestamp: Date()
        )
        wsClient.emit(EventStateMessage(type: .sessionExtended, sessionSlug: "cloud-talk",
                                         timestamp: Date(), stateUpdate: stateUpdate))

        try await waitFor(
            { dataController.isOffline == false && dataController.lastSynced != nil },
            description: "applyServerState called for SESSION_EXTENDED"
        )

        #expect(service.sessionDelayedEvent == nil, "SESSION_EXTENDED should NOT set sessionDelayedEvent")
    }

    @Test("SESSION_DELAYED: applyServerState called and sessionDelayedEvent set")
    func sessionDelayed_setsSessionDelayedEvent() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        let message = EventStateMessage(
            type: .sessionDelayed,
            sessionSlug: "microservices-mistakes",
            timestamp: Date(),
            previousSessionSlug: "cloud-native-pitfalls"
        )
        wsClient.emit(message)

        try await waitFor(
            { service.sessionDelayedEvent != nil },
            description: "sessionDelayedEvent set by SESSION_DELAYED"
        )

        #expect(service.sessionDelayedEvent?.previousSessionSlug == "cloud-native-pitfalls")
        #expect(service.sessionDelayedEvent?.currentSessionSlug == "microservices-mistakes")
    }

    @Test("consumeSessionDelayedEvent: returns event and sets to nil")
    func consumeSessionDelayedEvent_returnsAndNils() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        wsClient.emit(EventStateMessage(
            type: .sessionDelayed,
            sessionSlug: "talk-2",
            timestamp: Date(),
            previousSessionSlug: "talk-1"
        ))

        try await waitFor(
            { service.sessionDelayedEvent != nil },
            description: "sessionDelayedEvent set"
        )

        let consumed = service.consumeSessionDelayedEvent()
        #expect(consumed?.previousSessionSlug == "talk-1")
        #expect(service.sessionDelayedEvent == nil)
    }

    // MARK: - disconnect() cancels tasks

    @Test("disconnect: resets presence state")
    func disconnect_resetsPresenceState() async throws {
        let (_, ctx) = try makeContainer()
        let wsClient = MockWebSocketClient()
        let auth = MockAuthManager(currentJWT: "jwt-test")
        let (service, _) = makeService(wsClient: wsClient, auth: auth, modelContext: ctx)

        await service.connect(eventCode: "BATbern56")

        // Set some presence
        let stateUpdate = WatchStateUpdate(
            sessions: [],
            connectedOrganizers: [ConnectedOrganizer(username: "marco", firstName: "Marco")],
            serverTimestamp: Date()
        )
        wsClient.emit(EventStateMessage(type: .heartbeat, timestamp: Date(), stateUpdate: stateUpdate))
        try await waitFor(
            { service.presenceCount == 1 },
            description: "presence set before disconnect"
        )

        service.disconnect()

        #expect(service.presenceCount == 0)
        #expect(service.connectedOrganizers.isEmpty)
    }
}
