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
}
