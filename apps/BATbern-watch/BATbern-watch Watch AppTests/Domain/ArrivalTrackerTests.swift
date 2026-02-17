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

// MARK: - MockURLProtocol

/// URLProtocol subclass for mocking URLSession network responses in unit tests.
final class MockURLProtocol: URLProtocol {
    static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = MockURLProtocol.requestHandler else {
            client?.urlProtocol(self, didFailWithError: URLError(.resourceUnavailable))
            return
        }
        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}

    /// Creates a URLSession pre-configured to use MockURLProtocol.
    static func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        return URLSession(configuration: config)
    }

    /// Makes a session that returns empty arrivals list for any GET request.
    static func makeEmptyArrivalsSession() -> URLSession {
        requestHandler = { _ in
            let response = HTTPURLResponse(
                url: URL(string: "https://localhost")!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["Content-Type": "application/json"]
            )!
            return (response, "{\"arrivals\":[]}".data(using: .utf8)!)
        }
        return makeSession()
    }
}

// MARK: - ArrivalTrackerTests

@Suite("ArrivalTracker")
@MainActor
struct ArrivalTrackerTests {

    private var modelContainer: ModelContainer
    private var modelContext: ModelContext

    init() throws {
        let schema = Schema([CachedSpeaker.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        modelContainer = try ModelContainer(for: schema, configurations: [config])
        modelContext = ModelContext(modelContainer)
        MockURLProtocol.requestHandler = nil
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

    private func makeTracker(
        organizerUsername: String = "marco.muster",
        jwt: String? = "mock-jwt",
        wsClient: MockWebSocketClient? = nil,
        urlSession: URLSession? = nil
    ) -> ArrivalTracker {
        let auth = MockAuthManager(
            organizerUsername: organizerUsername,
            currentJWT: jwt
        )
        return ArrivalTracker(
            authManager: auth,
            modelContext: modelContext,
            webSocketClient: wsClient,
            urlSession: urlSession ?? MockURLProtocol.makeEmptyArrivalsSession()
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
        #expect(speaker.arrivedConfirmedBy == "marco")
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

        // Mock URLSession to return pre-populated arrivals
        MockURLProtocol.requestHandler = { request in
            #expect(request.httpMethod == "GET")
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["Content-Type": "application/json"]
            )!
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
            return (response, json.data(using: .utf8)!)
        }

        let session = MockURLProtocol.makeSession()
        let tracker = makeTracker(urlSession: session)

        await tracker.startListening(eventCode: "BATbern56")

        #expect(speaker.arrived == true)
        #expect(speaker.arrivedConfirmedBy == "marco")
    }

    // MARK: - Test 11.6: REST fallback when WebSocket disconnected

    @Test("confirmArrival: uses REST fallback when WebSocket is disconnected")
    func usesRESTFallback_whenWebSocketDisconnected() async throws {
        let wsClient = MockWebSocketClient()
        wsClient._isConnected = false  // Not connected

        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            if request.httpMethod == "POST" {
                capturedRequest = request
                let response = HTTPURLResponse(
                    url: request.url!,
                    statusCode: 201,
                    httpVersion: nil,
                    headerFields: nil
                )!
                return (response, Data())
            }
            // GET for initial arrivals
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["Content-Type": "application/json"]
            )!
            return (response, "{\"arrivals\":[]}".data(using: .utf8)!)
        }

        let session = MockURLProtocol.makeSession()
        let tracker = makeTracker(wsClient: wsClient, urlSession: session)

        // Set event code via startListening
        await tracker.startListening(eventCode: "BATbern56")

        let speaker = makeSpeaker(username: "anna.meier", arrived: false)

        try await tracker.confirmArrival(speaker: speaker)

        // Verify REST was used (not WebSocket)
        #expect(wsClient.sentActions.isEmpty)
        let urlString = capturedRequest?.url?.absoluteString ?? ""
        #expect(urlString.contains("/api/v1/watch/events/BATbern56/arrivals"))
        #expect(capturedRequest?.httpMethod == "POST")
    }
}
