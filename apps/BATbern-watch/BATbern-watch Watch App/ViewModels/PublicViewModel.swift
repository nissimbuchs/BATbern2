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
            print("🔄 PublicViewModel: Starting refresh...")
            // Fetch fresh data from API
            let watchEvent = try await apiClient.fetchCurrentEvent()
            print("✅ PublicViewModel: Received WatchEvent - \(watchEvent.title)")

            // Convert WatchEvent → SwiftData model
            let cachedEvent = watchEvent.toCachedEvent()

            // Update cache
            let cache = LocalCache(modelContext: modelContext)
            cache.saveEvent(cachedEvent)

            // Update published properties
            self.event = cachedEvent
            self.sessions = cachedEvent.sessions
            self.lastSynced = clock.now

        } catch let error as APIError {
            // Handle API-specific errors
            print("❌ PublicViewModel: APIError - \(error)")
            switch error {
            case .noCurrentEvent:
                // No current event — clear cache and show empty state
                print("⚠️ No current event - showing empty state")
                self.event = nil
                self.sessions = []
                self.errorMessage = nil  // Empty state, not an error

            case .networkError:
                // Network failure — continue showing cached data
                print(NSLocalizedString("debug.network_error", comment: "Debug: Network error"))
                isOffline = true
                errorMessage = NSLocalizedString("error.offline", comment: "Offline error message")

            default:
                // Other errors — continue with cached data
                print("⚠️ Other API error: \(error.localizedDescription)")
                errorMessage = error.localizedDescription
            }

        } catch {
            // Unexpected errors
            print(String(format: NSLocalizedString("debug.unexpected_error", comment: "Debug: Unexpected error"), "\(error)"))
            isOffline = true
            errorMessage = String(format: NSLocalizedString("error.refresh_failed", comment: "Refresh failed error"), error.localizedDescription)
        }

        isLoading = false
    }
}
