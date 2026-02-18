//
//  WebSocketClientTests.swift
//  BATbern-watch Watch AppTests
//
//  Unit tests for WebSocketClient stream delivery contract.
//  Story W4.1 Task 1.9: verify stream delivery using MockWebSocketClient.emit().
//  Tests the WebSocketClientProtocol contract — behaviour that WebSocketClient must match.
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("WebSocketClient stream contract", .serialized)
@MainActor
struct WebSocketClientTests {

    // MARK: - stateUpdates() stream delivery

    @Test("stateUpdates: emit delivers EventStateMessage to consumer")
    func stateUpdates_emitDeliversMessage() async throws {
        let client = MockWebSocketClient()
        let stream = client.stateUpdates()

        let expected = EventStateMessage(type: .heartbeat, timestamp: Date())
        client.emit(expected)

        var received: EventStateMessage?
        for await message in stream {
            received = message
            break
        }

        #expect(received?.type == expected.type)
    }

    @Test("stateUpdates: emit carries WatchStateUpdate payload")
    func stateUpdates_emitCarriesStateUpdate() async throws {
        let client = MockWebSocketClient()
        let stream = client.stateUpdates()

        let organizer = ConnectedOrganizer(username: "marco.organizer", firstName: "Marco")
        let stateUpdate = WatchStateUpdate(
            sessions: [
                SessionStateUpdate(
                    sessionSlug: "cloud-talk",
                    status: "STARTED",
                    actualStartTime: Date(),
                    actualEndTime: nil,
                    overrunMinutes: nil,
                    completedByUsername: nil
                )
            ],
            connectedOrganizers: [organizer],
            serverTimestamp: Date()
        )
        let message = EventStateMessage(type: .sessionStarted, timestamp: Date(), stateUpdate: stateUpdate)
        client.emit(message)

        var received: EventStateMessage?
        for await msg in stream {
            received = msg
            break
        }

        #expect(received?.stateUpdate?.sessions.first?.sessionSlug == "cloud-talk")
        #expect(received?.stateUpdate?.connectedOrganizers.first?.username == "marco.organizer")
    }

    // MARK: - arrivalUpdates() stream delivery

    @Test("arrivalUpdates: emitArrival delivers SpeakerArrivalMessage to consumer")
    func arrivalUpdates_emitArrivalDeliversMessage() async throws {
        let client = MockWebSocketClient()
        let stream = client.arrivalUpdates()

        let message = SpeakerArrivalMessage(
            speakerUsername: "anna.meier",
            speakerFirstName: "Anna",
            speakerLastName: "Meier",
            confirmedBy: "marco",
            arrivedAt: Date(),
            arrivedCount: 1,
            totalCount: 3
        )
        client.emitArrival(message)

        var received: SpeakerArrivalMessage?
        for await msg in stream {
            received = msg
            break
        }

        #expect(received?.speakerUsername == "anna.meier")
        #expect(received?.arrivedCount == 1)
    }

    // MARK: - disconnect() finishes streams

    @Test("disconnect: finishes both streams")
    func disconnect_finishesBothStreams() async throws {
        let client = MockWebSocketClient()
        let stateStream = client.stateUpdates()
        let arrivalStream = client.arrivalUpdates()

        client.disconnect()

        var stateFinished = true
        for await _ in stateStream { stateFinished = false; break }
        #expect(stateFinished, "state stream should be finished after disconnect")

        var arrivalFinished = true
        for await _ in arrivalStream { arrivalFinished = false; break }
        #expect(arrivalFinished, "arrival stream should be finished after disconnect")
    }

    // MARK: - connect() records call

    @Test("connect: records eventCode and accessToken")
    func connect_recordsEventCodeAndToken() async throws {
        let client = MockWebSocketClient()
        try await client.connect(eventCode: "BATbern56", accessToken: "jwt-abc")

        #expect(client.connectCalls.count == 1)
        #expect(client.connectCalls.first?.eventCode == "BATbern56")
        #expect(client.connectCalls.first?.accessToken == "jwt-abc")
        #expect(client.isConnected == true)
    }

    @Test("connect: marks isConnected true after successful connect")
    func connect_marksIsConnectedTrue() async throws {
        let client = MockWebSocketClient()
        #expect(client.isConnected == false)

        try await client.connect(eventCode: "BATbern56", accessToken: "jwt-abc")
        #expect(client.isConnected == true)
    }

    // MARK: - sendAction()

    @Test("sendAction: records sent action")
    func sendAction_recordsAction() async throws {
        let client = MockWebSocketClient()
        client._isConnected = true

        try await client.sendAction(.startSession(sessionSlug: "cloud-talk"))
        #expect(client.sentActions == [.startSession(sessionSlug: "cloud-talk")])
    }
}
