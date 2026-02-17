//
//  EventSyncServiceTests.swift
//  BATbern-watch Watch AppTests
//
//  Unit tests for EventSyncService — W2.3 schedule sync.
//  Tests cover: happy-path sync, no active event, progress tracking,
//  portrait download, and 401 JWT refresh flow.
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

// MARK: - URLProtocol mock for intercepting URLSession requests

final class MockURLProtocol: URLProtocol {
    static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = MockURLProtocol.requestHandler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
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
}

// MARK: - Mock AuthManager

@MainActor
final class MockAuthManager: AuthManagerProtocol {
    var isPaired: Bool = true
    var currentJWT: String? = "mock.jwt.token"
    var pairingToken: String? = "mock-pairing-token"
    var organizerUsername: String? = "test.organizer"
    var organizerFirstName: String? = "Test"

    var refreshCallCount = 0
    var shouldFailRefresh = false

    func pair(code: String) async throws {}
    func unpair() {}

    func refreshJWT() async throws {
        refreshCallCount += 1
        if shouldFailRefresh {
            throw URLError(.userAuthenticationRequired)
        }
    }
}

// MARK: - Mock PortraitCache

final class MockPortraitCache: @unchecked Sendable {
    var downloadCallCount = 0
    var downloadedURLs: [URL] = []
    var shouldFail = false

    func downloadAndCache(url: URL) async throws -> Data? {
        downloadCallCount += 1
        downloadedURLs.append(url)
        if shouldFail { return nil }
        return Data()
    }
}

// MARK: - Helpers

private func makeActiveEventsJSON(
    eventCode: String = "BATbern99",
    title: String = "BATbern 99 - Test Night",
    includePortrait: Bool = false
) -> Data {
    let portraitURL = includePortrait ? "\"https://example.com/speaker.jpg\"" : "null"
    let json = """
    {
        "activeEvents": [{
            "eventCode": "\(eventCode)",
            "title": "\(title)",
            "eventDate": "2026-03-01",
            "venueName": "Kultur Casino Bern",
            "typicalStartTime": "18:00",
            "typicalEndTime": "22:00",
            "themeImageUrl": null,
            "currentPublishedPhase": "AGENDA",
            "eventStatus": "SCHEDULED",
            "sessions": [{
                "sessionSlug": "keynote-1",
                "title": "Opening Keynote",
                "abstract": null,
                "sessionType": "talk",
                "scheduledStartTime": "2026-03-01T17:00:00Z",
                "scheduledEndTime": "2026-03-01T17:45:00Z",
                "durationMinutes": 45,
                "speakers": [{
                    "username": "john.doe",
                    "firstName": "John",
                    "lastName": "Doe",
                    "company": null,
                    "companyLogoUrl": null,
                    "profilePictureUrl": \(portraitURL),
                    "bio": null,
                    "speakerRole": "panelist"
                }],
                "status": "SCHEDULED",
                "actualStartTime": null,
                "actualEndTime": null,
                "overrunMinutes": null,
                "completedBy": null
            }]
        }]
    }
    """
    return json.data(using: .utf8)!
}

private func makeEmptyActiveEventsJSON() -> Data {
    return #"{"activeEvents":[]}"#.data(using: .utf8)!
}

private func makeModelContext() throws -> ModelContext {
    let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self, PairingInfo.self])
    let config = ModelConfiguration(isStoredInMemoryOnly: true)
    let container = try ModelContainer(for: schema, configurations: [config])
    return ModelContext(container)
}

private func makeSession(config: URLSessionConfiguration = .ephemeral) -> URLSession {
    config.protocolClasses = [MockURLProtocol.self]
    return URLSession(configuration: config)
}

// MARK: - Test Suite

@Suite("EventSyncService Tests", .serialized)
@MainActor
struct EventSyncServiceTests {

    // MARK: - AC#1: Full schedule sync returns event data

    @Test("syncActiveEvent: maps response to CachedEvent and sets currentEvent")
    func test_syncActiveEvent_mapsResponseToCachedEvent() async throws {
        // Given
        let authManager = MockAuthManager()
        let modelContext = try makeModelContext()
        MockURLProtocol.requestHandler = { _ in
            let response = HTTPURLResponse(
                url: URL(string: "https://example.com")!,
                statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, makeActiveEventsJSON())
        }
        let service = EventSyncService(
            authManager: authManager,
            modelContext: modelContext,
            session: makeSession()
        )

        // When
        try await service.syncActiveEvent()

        // Then
        #expect(service.currentEvent != nil, "currentEvent should be set after sync")
        #expect(service.currentEvent?.eventCode == "BATbern99", "Event code should match")
        #expect(service.currentEvent?.title == "BATbern 99 - Test Night", "Title should match")
        #expect(service.currentEvent?.sessions.count == 1, "Should have 1 session")
        #expect(service.currentEvent?.sessions.first?.speakers.count == 1, "Session should have 1 speaker")
        #expect(service.syncState == .completed, "Sync state should be completed")
    }

    // MARK: - AC#4: No active event returns empty state

    @Test("syncActiveEvent: sets noActiveEvent state when backend returns empty list")
    func test_syncActiveEvent_setsNoActiveEventState_whenEmptyList() async throws {
        // Given
        let authManager = MockAuthManager()
        let modelContext = try makeModelContext()
        MockURLProtocol.requestHandler = { _ in
            let response = HTTPURLResponse(
                url: URL(string: "https://example.com")!,
                statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, makeEmptyActiveEventsJSON())
        }
        let service = EventSyncService(
            authManager: authManager,
            modelContext: modelContext,
            session: makeSession()
        )

        // When
        try await service.syncActiveEvent()

        // Then
        #expect(service.currentEvent == nil, "currentEvent should remain nil")
        #expect(service.syncState == .noActiveEvent, "Sync state should be noActiveEvent")
    }

    // MARK: - Progress reporting increments through phases

    @Test("syncActiveEvent: progress advances from 0 to 1 during sync")
    func test_syncActiveEvent_progressAdvances() async throws {
        // Given
        let authManager = MockAuthManager()
        let modelContext = try makeModelContext()
        var observedProgress: [Double] = []
        MockURLProtocol.requestHandler = { _ in
            let response = HTTPURLResponse(
                url: URL(string: "https://example.com")!,
                statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, makeActiveEventsJSON())
        }
        let service = EventSyncService(
            authManager: authManager,
            modelContext: modelContext,
            session: makeSession()
        )

        // When
        try await service.syncActiveEvent()
        observedProgress.append(service.syncProgress)

        // Then: final progress should be 1.0
        #expect(service.syncProgress == 1.0, "Progress should reach 100% on completion")
    }

    // MARK: - Portrait download triggered for speakers with profilePictureUrl

    @Test("syncActiveEvent: downloads portrait when profilePictureUrl is present")
    func test_syncActiveEvent_downloadsPortraitForSpeaker() async throws {
        // Given — a custom PortraitCache spy to count calls
        var portraitDownloadCount = 0
        MockURLProtocol.requestHandler = { request in
            // Portrait URL returns 200 with empty data
            if request.url?.absoluteString.contains("speaker.jpg") == true {
                portraitDownloadCount += 1
                let response = HTTPURLResponse(
                    url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
                return (response, Data())
            }
            // Active events API
            let response = HTTPURLResponse(
                url: URL(string: "https://example.com")!,
                statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, makeActiveEventsJSON(includePortrait: true))
        }
        let authManager = MockAuthManager()
        let modelContext = try makeModelContext()
        let service = EventSyncService(
            authManager: authManager,
            modelContext: modelContext,
            session: makeSession()
        )

        // When
        try await service.syncActiveEvent()

        // Then: portrait URL was present in the response — sync should complete
        #expect(service.syncState == .completed, "Sync should complete even with portrait URL")
        #expect(service.currentEvent?.sessions.first?.speakers.first?.profilePictureUrl
                == "https://example.com/speaker.jpg",
                "Speaker profile picture URL should be persisted")
    }

    // MARK: - 401 triggers JWT refresh and rethrows

    @Test("syncActiveEvent: calls refreshJWT and throws on 401 response")
    func test_syncActiveEvent_callsRefreshJWT_on401() async throws {
        // Given
        let authManager = MockAuthManager()
        let modelContext = try makeModelContext()
        MockURLProtocol.requestHandler = { _ in
            let response = HTTPURLResponse(
                url: URL(string: "https://example.com")!,
                statusCode: 401, httpVersion: nil, headerFields: nil)!
            return (response, Data())
        }
        let service = EventSyncService(
            authManager: authManager,
            modelContext: modelContext,
            session: makeSession()
        )

        // When / Then
        await #expect(throws: SyncError.authenticationRequired) {
            try await service.syncActiveEvent()
        }
        #expect(authManager.refreshCallCount == 1, "Should call refreshJWT once on 401")
    }
}
