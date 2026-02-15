//
//  PublicViewModelTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for PublicViewModel cache-first loading and background refresh.
//  Uses MockAPIClient and MockClock for deterministic testing.
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

@Suite("PublicViewModel Tests")
struct PublicViewModelTests {
    private var modelContainer: ModelContainer
    private var modelContext: ModelContext

    init() throws {
        // In-memory model container for testing
        let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self, PairingInfo.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        modelContainer = try ModelContainer(for: schema, configurations: [config])
        modelContext = ModelContext(modelContainer)
    }

    @Test("Cache-first loading: ViewModel populates event and sessions on success")
    func test_cacheFirstLoading_populatesEventAndSessions() async throws {
        // Given: Mock API client returns a test event
        let mockAPI = MockAPIClient()
        let testEvent = TestData.event()
        mockAPI.fetchCurrentEventResult = .success(testEvent)

        let mockClock = MockClock(fixedDate: Date())

        // When: ViewModel initializes and refreshes
        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        // Wait for background refresh to complete
        try await Task.sleep(nanoseconds: 500_000_000)  // 0.5s

        // Then: Event and sessions are populated
        #expect(viewModel.event != nil, "Event should be populated")
        #expect(viewModel.sessions.count > 0, "Sessions should be populated")
        #expect(viewModel.event?.eventCode == testEvent.id, "Event code should match test data")
    }

    @Test("Background refresh: API client is called on init")
    func test_backgroundRefresh_callsAPIClient() async throws {
        // Given: Mock API client
        let mockAPI = MockAPIClient()
        mockAPI.fetchCurrentEventResult = .success(TestData.event())
        let mockClock = MockClock(fixedDate: Date())

        // When: ViewModel initializes
        _ = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        // Wait for background refresh
        try await Task.sleep(nanoseconds: 500_000_000)

        // Then: API client was called
        #expect(mockAPI.fetchCurrentEventCallCount > 0, "API client should be called for refresh")
    }

    @Test("Empty state: No event when API returns failure")
    func test_emptyState_noEventOnAPIFailure() async throws {
        // Given: Mock API client returns failure
        let mockAPI = MockAPIClient()
        mockAPI.fetchCurrentEventResult = .failure(MockError.simulatedFailure)
        let mockClock = MockClock(fixedDate: Date())

        // When: ViewModel initializes
        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        // Wait for background refresh
        try await Task.sleep(nanoseconds: 500_000_000)

        // Then: No event is set
        #expect(viewModel.event == nil, "Event should be nil on API failure")
        #expect(viewModel.sessions.isEmpty, "Sessions should be empty")
    }

    @Test("Error recovery: ViewModel recovers after initial failure")
    func test_errorRecovery_recoversAfterFailure() async throws {
        // Given: Mock API client initially fails, then succeeds
        let mockAPI = MockAPIClient()
        mockAPI.fetchCurrentEventResult = .failure(MockError.simulatedFailure)
        let mockClock = MockClock(fixedDate: Date())

        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        // Wait for first refresh (failure)
        try await Task.sleep(nanoseconds: 500_000_000)
        #expect(viewModel.event == nil, "Event should be nil after first failure")

        // When: API now returns success
        mockAPI.fetchCurrentEventResult = .success(TestData.event())
        await viewModel.refreshEvent()

        // Wait for refresh
        try await Task.sleep(nanoseconds: 500_000_000)

        // Then: Event is now populated
        #expect(viewModel.event != nil, "Event should be populated after recovery")
    }

    @Test("Offline indicator: Shows offline state on network error")
    func test_offlineIndicator_showsOfflineOnNetworkError() async throws {
        // Given: Mock API client returns network error
        let mockAPI = MockAPIClient()
        mockAPI.fetchCurrentEventResult = .failure(APIError.networkError(MockError.simulatedFailure))
        let mockClock = MockClock(fixedDate: Date())

        // When: ViewModel refreshes
        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        // Wait for refresh
        try await Task.sleep(nanoseconds: 500_000_000)

        // Then: Offline indicator is set
        #expect(viewModel.isOffline, "isOffline should be true on network error")
    }

    // MARK: - W1.2 Session Browsing Tests

    @Test("displayableSessions filters out placeholder sessions (null sessionType)")
    func test_displayableSessions_filtersPlaceholders() async throws {
        // Given: Event with valid and placeholder sessions
        let mockAPI = MockAPIClient()
        let mockClock = MockClock(fixedDate: Date())

        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

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
            sessionType: nil,  // Null type = placeholder
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

        // When: Event is loaded
        viewModel.event = event
        viewModel.sessions = [validSession, placeholderSession]

        // Then: Only valid session is displayable
        #expect(viewModel.displayableSessions.count == 1)
        #expect(viewModel.displayableSessions.first?.sessionSlug == "valid")
    }

    @Test("displayableSessions sorts by startTime")
    func test_displayableSessions_sortsByStartTime() async throws {
        let mockAPI = MockAPIClient()
        let mockClock = MockClock(fixedDate: Date())

        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        let session3pm = CachedSession(
            sessionSlug: "session-3",
            title: "Third",
            sessionType: .presentation,
            startTime: Calendar.current.date(byAdding: .hour, value: 3, to: Date())!,
            endTime: Calendar.current.date(byAdding: .hour, value: 4, to: Date())!
        )

        let session1pm = CachedSession(
            sessionSlug: "session-1",
            title: "First",
            sessionType: .keynote,
            startTime: Calendar.current.date(byAdding: .hour, value: 1, to: Date())!,
            endTime: Calendar.current.date(byAdding: .hour, value: 2, to: Date())!
        )

        // When: Sessions loaded out of order
        viewModel.sessions = [session3pm, session1pm]

        // Then: Sorted by start time
        let displayable = viewModel.displayableSessions
        #expect(displayable.count == 2)
        #expect(displayable[0].sessionSlug == "session-1")
        #expect(displayable[1].sessionSlug == "session-3")
    }

    @Test("hasSpeakerPhase is true for SPEAKERS phase")
    func test_hasSpeakerPhase_trueSPEAKERS() async throws {
        let mockAPI = MockAPIClient()
        let mockClock = MockClock(fixedDate: Date())

        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        let event = CachedEvent(
            eventCode: "test",
            title: "Test",
            eventDate: Date(),
            venueName: "Venue",
            typicalStartTime: "14:00",
            typicalEndTime: "18:00",
            currentPublishedPhase: "SPEAKERS"
        )

        viewModel.event = event

        #expect(viewModel.hasSpeakerPhase == true)
    }

    @Test("hasSpeakerPhase is true for AGENDA phase")
    func test_hasSpeakerPhase_trueAGENDA() async throws {
        let mockAPI = MockAPIClient()
        let mockClock = MockClock(fixedDate: Date())

        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        let event = CachedEvent(
            eventCode: "test",
            title: "Test",
            eventDate: Date(),
            venueName: "Venue",
            typicalStartTime: "14:00",
            typicalEndTime: "18:00",
            currentPublishedPhase: "AGENDA"
        )

        viewModel.event = event

        #expect(viewModel.hasSpeakerPhase == true)
    }

    @Test("hasSpeakerPhase is false for TOPIC phase")
    func test_hasSpeakerPhase_falseTOPIC() async throws {
        let mockAPI = MockAPIClient()
        let mockClock = MockClock(fixedDate: Date())

        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        let event = CachedEvent(
            eventCode: "test",
            title: "Test",
            eventDate: Date(),
            venueName: "Venue",
            typicalStartTime: "14:00",
            typicalEndTime: "18:00",
            currentPublishedPhase: "TOPIC"
        )

        viewModel.event = event

        #expect(viewModel.hasSpeakerPhase == false)
    }

    @Test("hasAgendaPhase is true only for AGENDA")
    func test_hasAgendaPhase_trueOnlyForAgenda() async throws {
        let mockAPI = MockAPIClient()
        let mockClock = MockClock(fixedDate: Date())

        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        let eventAgenda = CachedEvent(
            eventCode: "test",
            title: "Test",
            eventDate: Date(),
            venueName: "Venue",
            typicalStartTime: "14:00",
            typicalEndTime: "18:00",
            currentPublishedPhase: "AGENDA"
        )

        viewModel.event = eventAgenda
        #expect(viewModel.hasAgendaPhase == true)

        let eventSpeakers = CachedEvent(
            eventCode: "test",
            title: "Test",
            eventDate: Date(),
            venueName: "Venue",
            typicalStartTime: "14:00",
            typicalEndTime: "18:00",
            currentPublishedPhase: "SPEAKERS"
        )

        viewModel.event = eventSpeakers
        #expect(viewModel.hasAgendaPhase == false)
    }

    @Test("isBreakSession returns true for break types")
    func test_isBreakSession_trueForBreaks() async throws {
        let mockAPI = MockAPIClient()
        let mockClock = MockClock(fixedDate: Date())

        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        let breakSession = CachedSession(
            sessionSlug: "break",
            title: "Break",
            sessionType: .breakTime,
            startTime: Date(),
            endTime: Date()
        )

        let lunchSession = CachedSession(
            sessionSlug: "lunch",
            title: "Lunch",
            sessionType: .lunch,
            startTime: Date(),
            endTime: Date()
        )

        let networkingSession = CachedSession(
            sessionSlug: "networking",
            title: "Networking",
            sessionType: .networking,
            startTime: Date(),
            endTime: Date()
        )

        #expect(viewModel.isBreakSession(breakSession) == true)
        #expect(viewModel.isBreakSession(lunchSession) == true)
        #expect(viewModel.isBreakSession(networkingSession) == true)
    }

    @Test("isBreakSession returns false for presentation types")
    func test_isBreakSession_falseForPresentations() async throws {
        let mockAPI = MockAPIClient()
        let mockClock = MockClock(fixedDate: Date())

        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: mockClock,
            modelContext: modelContext
        )

        let keynote = CachedSession(
            sessionSlug: "keynote",
            title: "Keynote",
            sessionType: .keynote,
            startTime: Date(),
            endTime: Date()
        )

        let presentation = CachedSession(
            sessionSlug: "pres",
            title: "Presentation",
            sessionType: .presentation,
            startTime: Date(),
            endTime: Date()
        )

        #expect(viewModel.isBreakSession(keynote) == false)
        #expect(viewModel.isBreakSession(presentation) == false)
    }
}
