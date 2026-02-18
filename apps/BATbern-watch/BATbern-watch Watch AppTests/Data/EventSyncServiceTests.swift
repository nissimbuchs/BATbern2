//
//  EventSyncServiceTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for event sync behaviour — migrated to EventDataController.
//  EventSyncService was the old sync owner; EventDataController is the new unified source.
//  The organizer-endpoint network layer is now injected via OrganizerEventClientProtocol,
//  enabling clean protocol-based mocking without URLProtocol.
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

// MARK: - MockOrganizerEventClient

final class MockOrganizerEventClient: OrganizerEventClientProtocol, @unchecked Sendable {
    var result: Result<[ActiveEventResponse], Error> = .success([])
    private(set) var callCount = 0

    func fetchActiveEvents(jwt: String) async throws -> [ActiveEventResponse] {
        callCount += 1
        return try result.get()
    }
}

// MARK: - MockPortraitCache (reused from original tests)

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

private func makeActiveEventResponse(
    eventCode: String = "BATbern99",
    title: String = "BATbern 99 - Test Night",
    speakerRole: String = "panelist",
    profilePictureUrl: String? = nil
) -> ActiveEventResponse {
    ActiveEventResponse(
        eventCode: eventCode,
        title: title,
        eventDate: "2026-03-01",
        venueName: "Kultur Casino Bern",
        typicalStartTime: "18:00",
        typicalEndTime: "22:00",
        themeImageUrl: nil,
        currentPublishedPhase: "AGENDA",
        eventStatus: "SCHEDULED",
        sessions: [
            WatchSessionResponse(
                sessionSlug: "keynote-1",
                title: "Opening Keynote",
                abstract: nil,
                sessionType: "talk",
                scheduledStartTime: "2026-03-01T17:00:00Z",
                scheduledEndTime: "2026-03-01T17:45:00Z",
                durationMinutes: 45,
                speakers: [
                    WatchSpeakerResponse(
                        username: "john.doe",
                        firstName: "John",
                        lastName: "Doe",
                        company: nil,
                        companyLogoUrl: nil,
                        profilePictureUrl: profilePictureUrl,
                        bio: nil,
                        speakerRole: speakerRole
                    )
                ],
                status: "SCHEDULED",
                actualStartTime: nil,
                actualEndTime: nil,
                overrunMinutes: nil,
                completedBy: nil
            )
        ]
    )
}

@MainActor
private func makeController(
    authManager: MockAuthManager? = nil,
    organizerClient: MockOrganizerEventClient,
    portraitCache: MockPortraitCache? = nil,
    modelContext: ModelContext
) -> EventDataController {
    EventDataController(
        publicClient: MockAPIClient(),
        organizerClient: organizerClient,
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

    @Test("forceSync: maps organizer response to CachedEvent when paired")
    func test_forceSync_mapsToCachedEvent_whenPaired() async throws {
        // Given
        let organizerClient = MockOrganizerEventClient()
        organizerClient.result = .success([makeActiveEventResponse()])
        let controller = makeController(
            organizerClient: organizerClient,
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
    func test_forceSync_currentEventNil_whenEmptyList() async throws {
        // Given
        let organizerClient = MockOrganizerEventClient()
        organizerClient.result = .success([])
        let controller = makeController(
            organizerClient: organizerClient,
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
        let organizerClient = MockOrganizerEventClient()
        organizerClient.result = .success([makeActiveEventResponse()])
        let controller = makeController(
            organizerClient: organizerClient,
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
        let organizerClient = MockOrganizerEventClient()
        organizerClient.result = .success([
            makeActiveEventResponse(profilePictureUrl: "https://example.com/speaker.jpg")
        ])
        let mockPortraitCache = MockPortraitCache()
        let controller = makeController(
            organizerClient: organizerClient,
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
        let organizerClient = MockOrganizerEventClient()
        organizerClient.result = .failure(SyncError.authenticationRequired)
        let controller = makeController(
            authManager: authManager,
            organizerClient: organizerClient,
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

    // MARK: - D2: SpeakerRole decoding from backend lowercase snake_case

    @Test("forceSync: decodes primary_speaker to .primarySpeaker")
    func test_forceSync_decodesRolePrimarySpeaker() async throws {
        // Given
        let organizerClient = MockOrganizerEventClient()
        organizerClient.result = .success([makeActiveEventResponse(speakerRole: "primary_speaker")])
        let controller = makeController(
            organizerClient: organizerClient,
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
        let organizerClient = MockOrganizerEventClient()
        organizerClient.result = .success([makeActiveEventResponse(speakerRole: "co_speaker")])
        let controller = makeController(
            organizerClient: organizerClient,
            modelContext: try makeModelContext()
        )

        // When
        await controller.forceSync()

        // Then
        let role = controller.currentEvent?.sessions.first?.speakers.first?.speakerRole
        #expect(role == .coSpeaker)
    }

    // MARK: - Public endpoint fallback

    @Test("forceSync: uses public endpoint when not paired")
    func test_forceSync_usesPublicEndpoint_whenNotPaired() async throws {
        // Given
        let authManager = MockAuthManager(isPaired: false, currentJWT: nil)
        let organizerClient = MockOrganizerEventClient()
        organizerClient.result = .failure(SyncError.notAuthenticated)  // Should never be called

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
            organizerClient: organizerClient,
            authManager: authManager,
            modelContext: try makeModelContext(),
            skipAutoSync: true
        )

        // When
        await controller.forceSync()

        // Then: public endpoint was used, organizer endpoint not called
        #expect(controller.currentEvent?.eventCode == "public-event")
        #expect(mockPublicClient.fetchCurrentEventCallCount == 1)
    }

    // MARK: - 60s guard

    @Test("syncIfNeeded: skips second call within 60s cooldown")
    func test_syncIfNeeded_skipsWithin60sCooldown() async throws {
        // Given
        let organizerClient = MockOrganizerEventClient()
        organizerClient.result = .success([makeActiveEventResponse()])
        let controller = makeController(
            organizerClient: organizerClient,
            modelContext: try makeModelContext()
        )

        // When: first sync
        await controller.syncIfNeeded()
        let callCountAfterFirst = organizerClient.callCount

        // Second call immediately after — should be skipped by 60s cooldown
        await controller.syncIfNeeded()

        // Then: organizer client called exactly once
        #expect(organizerClient.callCount == callCountAfterFirst,
                "Second syncIfNeeded within 60s should be skipped")
    }
}
