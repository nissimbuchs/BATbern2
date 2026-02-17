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

// MARK: - EventSyncMockURLProtocol

/// Separate URLProtocol subclass for EventSyncServiceTests.
/// Has its OWN static requestHandler so it never interferes with MockURLProtocol.requestHandler
/// used by ArrivalTrackerTests (which runs concurrently in a different suite).
final class EventSyncMockURLProtocol: URLProtocol {
    static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = EventSyncMockURLProtocol.requestHandler else {
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
}

// MARK: - Mock PortraitCache

/// M2/M3 fix: Conforms to PortraitCacheable so it can be injected into EventSyncService.
/// Replaces real network calls with in-memory stubs, making portrait download assertions reliable.
final class MockPortraitCache: PortraitCacheable, @unchecked Sendable {
    var downloadCallCount = 0
    var downloadedURLs: [URL] = []

    @discardableResult
    func downloadAndCache(url: URL) async throws -> Data {
        downloadCallCount += 1
        downloadedURLs.append(url)
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

private func makeActiveEventsJSONWithRole(speakerRole: String) -> Data {
    let json = """
    {
        "activeEvents": [{
            "eventCode": "BATbern99",
            "title": "BATbern 99 - Test Night",
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
                    "profilePictureUrl": null,
                    "bio": null,
                    "speakerRole": "\(speakerRole)"
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
    config.protocolClasses = [EventSyncMockURLProtocol.self]
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
        EventSyncMockURLProtocol.requestHandler = { _ in
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
        EventSyncMockURLProtocol.requestHandler = { _ in
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

    @Test("syncActiveEvent: progress starts at 0.0 and reaches 1.0 on completion")
    func test_syncActiveEvent_progressAdvances() async throws {
        // Given
        let authManager = MockAuthManager()
        let modelContext = try makeModelContext()
        EventSyncMockURLProtocol.requestHandler = { _ in
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

        // Verify initial state before sync
        #expect(service.syncProgress == 0.0, "Progress should start at 0.0")
        #expect(service.syncState == .idle, "State should start idle")

        // When
        try await service.syncActiveEvent()

        // Then: final progress should be 1.0
        // NOTE (M4): Intermediate steps (0.1 → 0.2 → 0.8 → 0.9) require concurrent
        // observation with withObservationTracking or AsyncStream — out of scope for this
        // synchronous unit test. Verified via manual testing and E2E sync flow.
        #expect(service.syncProgress == 1.0, "Progress should reach 100% on completion")
        #expect(service.syncState == .completed, "State should be completed at 100%")
    }

    // MARK: - Portrait download triggered for speakers with profilePictureUrl

    @Test("syncActiveEvent: downloads portrait via MockPortraitCache when profilePictureUrl is present")
    func test_syncActiveEvent_downloadsPortraitForSpeaker() async throws {
        // Given — inject MockPortraitCache to intercept portrait downloads (M3 fix)
        // Previously used URLProtocol interception on URLSession.shared, which didn't
        // actually capture calls since PortraitCache used URLSession.shared (not the mock session).
        EventSyncMockURLProtocol.requestHandler = { _ in
            let response = HTTPURLResponse(
                url: URL(string: "https://example.com")!,
                statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, makeActiveEventsJSON(includePortrait: true))
        }
        let authManager = MockAuthManager()
        let modelContext = try makeModelContext()
        let mockPortraitCache = MockPortraitCache()
        let service = EventSyncService(
            authManager: authManager,
            modelContext: modelContext,
            portraitCache: mockPortraitCache,
            session: makeSession()
        )

        // When
        try await service.syncActiveEvent()

        // Then: portrait was downloaded exactly once (1 speaker with profilePictureUrl)
        #expect(service.syncState == .completed, "Sync should complete")
        #expect(mockPortraitCache.downloadCallCount == 1,
                "downloadAndCache should be called once for the speaker portrait")
        #expect(mockPortraitCache.downloadedURLs.first?.absoluteString == "https://example.com/speaker.jpg",
                "Should download the correct portrait URL")
        #expect(service.currentEvent?.sessions.first?.speakers.first?.profilePictureUrl
                == "https://example.com/speaker.jpg",
                "Speaker profile picture URL should be persisted in CachedSpeaker")
    }

    // MARK: - 401 triggers JWT refresh and rethrows

    @Test("syncActiveEvent: calls refreshJWT and throws on 401 response")
    func test_syncActiveEvent_callsRefreshJWT_on401() async throws {
        // Given
        let authManager = MockAuthManager()
        let modelContext = try makeModelContext()
        EventSyncMockURLProtocol.requestHandler = { _ in
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

    // MARK: - D2: SpeakerRole mapping from backend lowercase snake_case

    // MARK: - D2: SpeakerRole decoding from backend lowercase snake_case

    @Test("syncActiveEvent: decodes primary_speaker to .primarySpeaker (D2 debt)")
    func test_syncActiveEvent_decodesRolePrimarySpeaker() async throws {
        // Given — backend sends "primary_speaker" (EMS enum.name().toLowerCase())
        EventSyncMockURLProtocol.requestHandler = { _ in
            let response = HTTPURLResponse(
                url: URL(string: "https://example.com")!,
                statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, makeActiveEventsJSONWithRole(speakerRole: "primary_speaker"))
        }
        let service = EventSyncService(
            authManager: MockAuthManager(),
            modelContext: try makeModelContext(),
            session: makeSession()
        )

        // When
        try await service.syncActiveEvent()

        // Then: Watch enum raw values now match backend (D2: enum adapted to backend)
        let role = service.currentEvent?.sessions.first?.speakers.first?.speakerRole
        #expect(role == .primarySpeaker, "primary_speaker should decode to .primarySpeaker")
    }

    @Test("syncActiveEvent: decodes co_speaker to .coSpeaker (D2 debt)")
    func test_syncActiveEvent_decodesRoleCoSpeaker() async throws {
        EventSyncMockURLProtocol.requestHandler = { _ in
            let response = HTTPURLResponse(
                url: URL(string: "https://example.com")!,
                statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, makeActiveEventsJSONWithRole(speakerRole: "co_speaker"))
        }
        let service = EventSyncService(
            authManager: MockAuthManager(),
            modelContext: try makeModelContext(),
            session: makeSession()
        )

        try await service.syncActiveEvent()

        let role = service.currentEvent?.sessions.first?.speakers.first?.speakerRole
        #expect(role == .coSpeaker, "co_speaker should decode to .coSpeaker")
    }
}
