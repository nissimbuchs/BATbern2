//
//  PublicViewModel.swift
//  BATbern-watch Watch App
//
//  View model for public zone with cache-first loading and background refresh.
//  Uses protocol-based dependency injection for testability.
//  Source: docs/watch-app/architecture.md#Public-Zone-Data-Flow
//

import Foundation
import SwiftData

@Observable
class PublicViewModel {
    // MARK: - Dependencies (protocol-based for testing)

    let apiClient: APIClientProtocol
    let clock: ClockProtocol
    private let modelContext: ModelContext

    // MARK: - Published State

    var event: CachedEvent?
    var sessions: [CachedSession] = []
    var isLoading: Bool = false
    var isOffline: Bool = false
    var lastSynced: Date?
    var errorMessage: String?

    // MARK: - Initialization

    init(
        apiClient: APIClientProtocol = PublicEventService(),
        clock: ClockProtocol = SystemClock(),
        modelContext: ModelContext
    ) {
        self.apiClient = apiClient
        self.clock = clock
        self.modelContext = modelContext

        // Load cached data immediately on init
        loadCachedData()

        // Trigger background refresh
        Task {
            await refreshEvent()
        }
    }

    // MARK: - Cache-First Loading

    private func loadCachedData() {
        let cache = LocalCache(modelContext: modelContext)

        if let cachedEvent = cache.getCachedEvent() {
            self.event = cachedEvent
            self.sessions = cachedEvent.sessions
            self.lastSynced = cachedEvent.lastSyncTimestamp
        }
    }

    // MARK: - Background Refresh

    @MainActor
    func refreshEvent() async {
        // Only show spinner on cold launch with no cache
        if event == nil {
            isLoading = true
        }

        isOffline = false
        errorMessage = nil

        do {
            // Fetch fresh data from API
            let watchEvent = try await apiClient.fetchCurrentEvent()

            // Convert to DTO for cache persistence
            let eventResponse = EventResponse(
                eventCode: watchEvent.id,
                eventNumber: nil,
                title: watchEvent.title,
                date: ISO8601DateFormatter().string(from: watchEvent.date),
                themeImageUrl: watchEvent.themeImageUrl,
                venueName: watchEvent.venueName,
                venueAddress: nil,
                typicalStartTime: "",  // Not needed for display
                typicalEndTime: "",
                workflowState: "",
                currentPublishedPhase: nil,
                sessions: watchEvent.sessions.map { session in
                    SessionResponse(
                        sessionSlug: session.id,
                        title: session.title,
                        description: session.abstract,
                        sessionType: session.sessionType.rawValue,
                        startTime: ISO8601DateFormatter().string(from: session.startTime),
                        endTime: ISO8601DateFormatter().string(from: session.endTime),
                        speakers: session.speakers.map { speaker in
                            SessionSpeakerResponse(
                                username: speaker.id,
                                firstName: speaker.firstName,
                                lastName: speaker.lastName,
                                company: speaker.company,
                                profilePictureUrl: speaker.profilePictureUrl,
                                bio: speaker.bio,
                                speakerRole: speaker.speakerRole.rawValue
                            )
                        }
                    )
                }
            )

            // Convert DTO → SwiftData model
            let cachedEvent = eventResponse.toCachedEvent()

            // Update cache
            let cache = LocalCache(modelContext: modelContext)
            cache.saveEvent(cachedEvent)

            // Update published properties
            self.event = cachedEvent
            self.sessions = cachedEvent.sessions
            self.lastSynced = clock.now

        } catch let error as APIError {
            // Handle API-specific errors
            switch error {
            case .noCurrentEvent:
                // No current event — clear cache and show empty state
                self.event = nil
                self.sessions = []
                self.errorMessage = nil  // Empty state, not an error

            case .networkError:
                // Network failure — continue showing cached data
                isOffline = true
                errorMessage = "Offline — showing cached data"

            default:
                // Other errors — continue with cached data
                errorMessage = error.localizedDescription
            }

        } catch {
            // Unexpected errors
            isOffline = true
            errorMessage = "Failed to refresh: \(error.localizedDescription)"
        }

        isLoading = false
    }
}
