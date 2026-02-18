//
//  EventSyncServiceTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for EventDataController sync behaviour.
//  Always uses GET /api/v1/events/current (same endpoint for both zones).
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

// MARK: - MockPortraitCache

/// Replaces real portrait download with in-memory stub, making assertions reliable.
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

private func makeModelContext() throws -> ModelContext {
    let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self, PairingInfo.self])
    let config = ModelConfiguration(isStoredInMemoryOnly: true)
    let container = try ModelContainer(for: schema, configurations: [config])
    return ModelContext(container)
}

private func makePublicEvent(
    code: String = "BATbern99",
    title: String = "BATbern 99 - Test Night",
    speakerRole: SpeakerRole = .panelist,
    profilePictureUrl: String? = nil,
    currentPublishedPhase: String? = "AGENDA"
) -> WatchEvent {
    let speaker = WatchSpeaker(
        id: "john.doe",
        firstName: "John",
        lastName: "Doe",
        company: nil,
        companyLogoUrl: nil,
        profilePictureUrl: profilePictureUrl,
        bio: nil,
        speakerRole: speakerRole,
        arrived: false,
        arrivedConfirmedBy: nil,
        arrivedAt: nil
    )
    let session = WatchSession(
        id: "keynote-1",
        title: "Opening Keynote",
        abstract: nil,
        sessionType: .presentation,
        startTime: Date(),
        endTime: Date().addingTimeInterval(2700),
        speakers: [speaker],
        state: .scheduled,
        actualStartTime: nil,
        overrunMinutes: nil
    )
    return WatchEvent(
        id: code,
        title: title,
        date: Date(),
        themeImageUrl: nil,
        venueName: "Kultur Casino Bern",
        sessions: [session],
        currentPublishedPhase: currentPublishedPhase,
        typicalStartTime: "18:00",
        typicalEndTime: "22:00"
    )
}

@MainActor
private func makeController(
    authManager: MockAuthManager? = nil,
    publicClient: MockAPIClient? = nil,
    portraitCache: MockPortraitCache? = nil,
    modelContext: ModelContext
) -> EventDataController {
    EventDataController(
        publicClient: publicClient ?? MockAPIClient(),
        authManager: authManager ?? MockAuthManager(),
        portraitCache: portraitCache ?? MockPortraitCache(),
        modelContext: modelContext,
        skipAutoSync: true
    )
}

// MARK: - Test Suite

@Suite("EventDataController Tests", .serialized)
@MainActor
struct EventSyncServiceTests {

    // MARK: - AC#1: Full schedule sync returns event data

    @Test("forceSync: maps public endpoint response to CachedEvent")
    func test_forceSync_mapsToCachedEvent() async throws {
        // Given
        let mockPublicClient = MockAPIClient()
        mockPublicClient.fetchCurrentEventResult = .success(makePublicEvent())
        let controller = makeController(
            publicClient: mockPublicClient,
            modelContext: try makeModelContext()
        )

        // When
        await controller.forceSync()

        // Then
        #expect(controller.currentEvent != nil, "currentEvent should be set after sync")
        #expect(controller.currentEvent?.eventCode == "BATbern99")
        #expect(controller.currentEvent?.title == "BATbern 99 - Test Night")
        #expect(controller.currentEvent?.sessions.count == 1)
        #expect(controller.currentEvent?.sessions.first?.speakers.count == 1)
        #expect(!controller.isLoading, "isLoading should be false after sync")
        #expect(controller.syncProgress == 1.0)
    }

    // MARK: - AC#4: No active event returns nil currentEvent

    @Test("forceSync: currentEvent remains nil when backend returns empty list")
    func test_forceSync_currentEventNil_whenNoCurrentEvent() async throws {
        // Given
        let mockPublicClient = MockAPIClient()
        mockPublicClient.fetchCurrentEventResult = .failure(APIError.noCurrentEvent)
        let controller = makeController(
            publicClient: mockPublicClient,
            modelContext: try makeModelContext()
        )

        // When
        await controller.forceSync()

        // Then
        #expect(controller.currentEvent == nil)
        #expect(!controller.isLoading)
    }

    // MARK: - Progress reporting

    @Test("forceSync: progress reaches 1.0 on completion")
    func test_forceSync_progressReaches1_onCompletion() async throws {
        // Given
        let mockPublicClient = MockAPIClient()
        mockPublicClient.fetchCurrentEventResult = .success(makePublicEvent())
        let controller = makeController(
            publicClient: mockPublicClient,
            modelContext: try makeModelContext()
        )
        #expect(controller.syncProgress == 0.0, "Progress should start at 0.0")

        // When
        await controller.forceSync()

        // Then
        #expect(controller.syncProgress == 1.0)
        #expect(!controller.isLoading)
    }

    // MARK: - Portrait download

    @Test("forceSync: downloads portrait for speaker with profilePictureUrl")
    func test_forceSync_downloadsPortrait_whenUrlPresent() async throws {
        // Given
        let mockPublicClient = MockAPIClient()
        mockPublicClient.fetchCurrentEventResult = .success(
            makePublicEvent(profilePictureUrl: "https://example.com/speaker.jpg")
        )
        let mockPortraitCache = MockPortraitCache()
        let controller = makeController(
            publicClient: mockPublicClient,
            portraitCache: mockPortraitCache,
            modelContext: try makeModelContext()
        )

        // When
        await controller.forceSync()

        // Then
        #expect(!controller.isLoading)
        #expect(mockPortraitCache.downloadCallCount == 1,
                "downloadAndCache should be called once for the speaker portrait")
        #expect(mockPortraitCache.downloadedURLs.first?.absoluteString == "https://example.com/speaker.jpg")
        #expect(controller.currentEvent?.sessions.first?.speakers.first?.profilePictureUrl
                == "https://example.com/speaker.jpg")
    }

    // MARK: - 401 graceful degradation

    @Test("forceSync: graceful degradation on 401 — no crash, no JWT refresh call")
    func test_forceSync_gracefulDegradation_on401() async throws {
        // Given — per MEMORY.md: refreshJWT must NOT be called inside sync to avoid
        // the 401 → refreshJWT → onChange(currentJWT) → sync → 401 infinite loop.
        let authManager = MockAuthManager()
        let mockPublicClient = MockAPIClient()
        mockPublicClient.fetchCurrentEventResult = .failure(SyncError.authenticationRequired)
        let controller = makeController(
            authManager: authManager,
            publicClient: mockPublicClient,
            modelContext: try makeModelContext()
        )

        // When
        await controller.forceSync()

        // Then: no crash, currentEvent stays nil, refreshJWT NOT called
        #expect(controller.currentEvent == nil)
        #expect(!controller.isLoading)
        #expect(authManager.refreshCallCount == 0,
                "refreshJWT must not be called on 401 — prevents auth retry loop")
    }

    // MARK: - D2: SpeakerRole decoded from WatchEvent

    @Test("forceSync: decodes primary_speaker to .primarySpeaker")
    func test_forceSync_decodesRolePrimarySpeaker() async throws {
        // Given
        let mockPublicClient = MockAPIClient()
        mockPublicClient.fetchCurrentEventResult = .success(makePublicEvent(speakerRole: .primarySpeaker))
        let controller = makeController(
            publicClient: mockPublicClient,
            modelContext: try makeModelContext()
        )

        // When
        await controller.forceSync()

        // Then
        let role = controller.currentEvent?.sessions.first?.speakers.first?.speakerRole
        #expect(role == .primarySpeaker)
    }

    @Test("forceSync: decodes co_speaker to .coSpeaker")
    func test_forceSync_decodesRoleCoSpeaker() async throws {
        // Given
        let mockPublicClient = MockAPIClient()
        mockPublicClient.fetchCurrentEventResult = .success(makePublicEvent(speakerRole: .coSpeaker))
        let controller = makeController(
            publicClient: mockPublicClient,
            modelContext: try makeModelContext()
        )

        // When
        await controller.forceSync()

        // Then
        let role = controller.currentEvent?.sessions.first?.speakers.first?.speakerRole
        #expect(role == .coSpeaker)
    }

    // MARK: - Single endpoint (always public)

    @Test("forceSync: uses public endpoint regardless of isPaired state")
    func test_forceSync_alwaysUsesPublicEndpoint() async throws {
        // Given — even when paired, same public endpoint is used
        let authManager = MockAuthManager(isPaired: true, currentJWT: "some-jwt")
        let mockPublicClient = MockAPIClient()
        mockPublicClient.fetchCurrentEventResult = .success(WatchEvent(
            id: "public-event",
            title: "Public Event",
            date: Date(),
            themeImageUrl: nil,
            venueName: "Bern",
            sessions: [],
            currentPublishedPhase: nil
        ))

        let controller = EventDataController(
            publicClient: mockPublicClient,
            authManager: authManager,
            modelContext: try makeModelContext(),
            skipAutoSync: true
        )

        // When
        await controller.forceSync()

        // Then: public endpoint was used
        #expect(controller.currentEvent?.eventCode == "public-event")
        #expect(mockPublicClient.fetchCurrentEventCallCount == 1)
    }

    // MARK: - 60s guard

    @Test("syncIfNeeded: skips second call within 60s cooldown")
    func test_syncIfNeeded_skipsWithin60sCooldown() async throws {
        // Given
        let mockPublicClient = MockAPIClient()
        mockPublicClient.fetchCurrentEventResult = .success(makePublicEvent())
        let controller = makeController(
            publicClient: mockPublicClient,
            modelContext: try makeModelContext()
        )

        // When: first sync
        await controller.syncIfNeeded()
        let callCountAfterFirst = mockPublicClient.fetchCurrentEventCallCount

        // Second call immediately after — should be skipped by 60s cooldown
        await controller.syncIfNeeded()

        // Then: public client called exactly once
        #expect(mockPublicClient.fetchCurrentEventCallCount == callCountAfterFirst,
                "Second syncIfNeeded within 60s should be skipped")
    }
}
