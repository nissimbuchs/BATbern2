//
//  ArrivalTrackerTests.swift
//  BATbern-watch Watch AppTests
//
//  Unit tests for ArrivalTracker domain logic.
//  W2.4: FR38, FR39 — optimistic updates, WebSocket sync, REST fallback.
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

// MARK: - ArrivalTrackerTests

// .serialized prevents concurrent test execution so SwiftData model context mutations
// from one test do not interfere with another test that runs in parallel.
@Suite("ArrivalTracker", .serialized)
@MainActor
struct ArrivalTrackerTests {

    private var modelContainer: ModelContainer
    private var modelContext: ModelContext

    init() throws {
        let schema = Schema([CachedSpeaker.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        modelContainer = try ModelContainer(for: schema, configurations: [config])
        modelContext = ModelContext(modelContainer)
    }

    // MARK: - Helpers

    private func makeSpeaker(username: String = "anna.meier", arrived: Bool = false) -> CachedSpeaker {
        let speaker = CachedSpeaker(
            username: username,
            firstName: "Anna",
            lastName: "Meier",
            arrived: arrived
        )
        modelContext.insert(speaker)
        try? modelContext.save()
        return speaker
    }

    /// Default fetcher that returns empty arrivals — used when no custom fetcher is needed.
    private static let emptyArrivalsFetcher: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { _ in
        let response = HTTPURLResponse(
            url: URL(string: "https://localhost")!,
            statusCode: 200,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        return ("{\"arrivals\":[]}".data(using: .utf8)!, response)
    }

    private func makeTracker(
        organizerUsername: String = "marco.muster",
        jwt: String? = "mock-jwt",
        wsClient: MockWebSocketClient? = nil,
        fetcher: (@Sendable (URLRequest) async throws -> (Data, URLResponse))? = nil
    ) -> ArrivalTracker {
        let auth = MockAuthManager(
            organizerUsername: organizerUsername,
            currentJWT: jwt
        )
        return ArrivalTracker(
            authManager: auth,
            modelContext: modelContext,
            webSocketClient: wsClient,
            httpFetcher: fetcher ?? Self.emptyArrivalsFetcher
        )
    }

    // MARK: - Test 11.2: confirmArrival updates local state optimistically

    @Test("confirmArrival: updates local speaker state optimistically and sends WebSocket action")
    func confirmArrival_updatesLocalStateOptimistically() async throws {
        let wsClient = MockWebSocketClient()
        wsClient._isConnected = true
        let tracker = makeTracker(organizerUsername: "marco", wsClient: wsClient)

        // Use startListening to set currentEventCode
        await tracker.startListening(eventCode: "BATbern56")

        let speaker = makeSpeaker(username: "anna.meier", arrived: false)

        try await tracker.confirmArrival(speaker: speaker)

        #expect(speaker.arrived == true)
        #expect(speaker.arrivedConfirmedBy == "Marco")  // organizerFirstName takes precedence over username
        #expect(speaker.arrivedAt != nil)
        #expect(wsClient.sentActions.contains(.speakerArrived(speakerUsername: "anna.meier")))
    }

    // MARK: - Test 11.3: processArrivalMessage updates counter from WebSocket

    @Test("WebSocket arrival message: updates arrivedCount and totalCount")
    func processArrivalMessage_updatesCounter() async throws {
        let wsClient = MockWebSocketClient()
        wsClient._isConnected = true

        // Set up 3 speakers in SwiftData, none arrived
        _ = makeSpeaker(username: "anna.meier", arrived: false)
        _ = makeSpeaker(username: "tom.mueller", arrived: false)
        _ = makeSpeaker(username: "sara.weber", arrived: false)

        let tracker = makeTracker(wsClient: wsClient)
        await tracker.startListening(eventCode: "BATbern56")

        // Emit a WebSocket arrival message with server-authoritative counts
        let message = SpeakerArrivalMessage(
            speakerUsername: "anna.meier",
            speakerFirstName: "Anna",
            speakerLastName: "Meier",
            confirmedBy: "marco",
            arrivedAt: Date(),
            arrivedCount: 2,
            totalCount: 3
        )
        wsClient.emitArrival(message)

        // Yield to allow the listening task to process the message
        try await Task.sleep(nanoseconds: 100_000_000)

        #expect(tracker.arrivedCount == 2)
        #expect(tracker.totalCount == 3)
    }

    // MARK: - Test 11.4: confirmArrival is idempotent

    @Test("confirmArrival: is idempotent — no error when speaker already marked arrived")
    func confirmArrival_isIdempotent() async throws {
        let wsClient = MockWebSocketClient()
        wsClient._isConnected = true
        let tracker = makeTracker(wsClient: wsClient)

        await tracker.startListening(eventCode: "BATbern56")

        let speaker = makeSpeaker(username: "anna.meier", arrived: true)
        speaker.arrivedConfirmedBy = "marco"

        // Should not throw even though speaker is already arrived
        try await tracker.confirmArrival(speaker: speaker)

        // State still true, WebSocket sends action (server handles idempotency via UNIQUE constraint)
        #expect(speaker.arrived == true)
        #expect(wsClient.sentActions.contains(.speakerArrived(speakerUsername: "anna.meier")))
    }

    // MARK: - Test 11.5: fetchInitialArrivals applies state to cached speakers

    @Test("fetchInitialArrivals: applies arrival state from REST to CachedSpeakers")
    func fetchInitialArrivals_appliesStateToCachedSpeakers() async throws {
        let speaker = makeSpeaker(username: "anna.meier", arrived: false)

        let json = """
        {
            "arrivals": [
                {
                    "speakerUsername": "anna.meier",
                    "confirmedBy": "marco",
                    "arrivedAt": "2026-02-16T17:15:00.000Z"
                }
            ]
        }
        """
        let fetcher: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["Content-Type": "application/json"]
            )!
            return (json.data(using: .utf8)!, response)
        }

        let tracker = makeTracker(fetcher: fetcher)
        await tracker.startListening(eventCode: "BATbern56")

        #expect(speaker.arrived == true)
        #expect(speaker.arrivedConfirmedBy == "marco")
    }

    // MARK: - Test 11.6: REST fallback when WebSocket disconnected

    @Test("confirmArrival: uses REST fallback when WebSocket is disconnected")
    func usesRESTFallback_whenWebSocketDisconnected() async throws {
        let wsClient = MockWebSocketClient()
        wsClient._isConnected = false  // Not connected

        // Capture the request synchronously via an actor-isolated box to avoid the
        // @Sendable-closure / @MainActor mutable-capture restriction.
        final class RequestCapture: @unchecked Sendable {
            var captured: URLRequest?
        }
        let capture = RequestCapture()

        let fetcher: @Sendable (URLRequest) async throws -> (Data, URLResponse) = { request in
            if request.httpMethod == "POST" {
                capture.captured = request
                let response = HTTPURLResponse(
                    url: request.url!,
                    statusCode: 201,
                    httpVersion: nil,
                    headerFields: nil
                )!
                return (Data(), response)
            }
            // GET for initial arrivals — return empty arrivals
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["Content-Type": "application/json"]
            )!
            return ("{\"arrivals\":[]}".data(using: .utf8)!, response)
        }

        let tracker = makeTracker(wsClient: wsClient, fetcher: fetcher)

        // Set event code via startListening
        await tracker.startListening(eventCode: "BATbern56")

        let speaker = makeSpeaker(username: "anna.meier", arrived: false)

        try await tracker.confirmArrival(speaker: speaker)

        // Verify REST was used (not WebSocket)
        #expect(wsClient.sentActions.isEmpty)
        let urlString = capture.captured?.url?.absoluteString ?? ""
        #expect(urlString.contains("/api/v1/watch/events/BATbern56/arrivals"))
        #expect(capture.captured?.httpMethod == "POST")
    }
}
