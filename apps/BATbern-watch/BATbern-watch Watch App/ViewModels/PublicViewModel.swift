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
    let connectivityMonitor: ConnectivityMonitor
    private let modelContext: ModelContext
    private var wasOffline: Bool = false  // Track previous connectivity state

    // MARK: - Published State

    var event: CachedEvent?
    var sessions: [CachedSession] = []
    var isLoading: Bool = false
    var isOffline: Bool = false
    var lastSynced: Date?
    var errorMessage: String?

    // MARK: - Computed Properties (W1.2 - Session Card Browsing)

    /// Returns displayable sessions: filters out placeholders, sorted by startTime (AC#7)
    var displayableSessions: [CachedSession] {
        sessions
            .filter { session in
                // Exclude placeholder sessions (null sessionType or null startTime/endTime)
                session.sessionType != nil && session.startTime != nil && session.endTime != nil
            }
            .sorted { ($0.startTime ?? Date()) < ($1.startTime ?? Date()) }
    }

    /// True when currentPublishedPhase is SPEAKERS or AGENDA (AC#6)
    var hasSpeakerPhase: Bool {
        guard let phase = event?.currentPublishedPhase else { return false }
        return phase == "SPEAKERS" || phase == "AGENDA"
    }

    /// True when currentPublishedPhase is AGENDA (AC#6)
    var hasAgendaPhase: Bool {
        event?.currentPublishedPhase == "AGENDA"
    }

    /// Checks if session is a break/networking/lunch session (AC#3)
    func isBreakSession(_ session: CachedSession) -> Bool {
        guard let type = session.sessionType else { return false }
        return type == .breakTime || type == .lunch || type == .networking
    }

    // MARK: - Initialization

    init(
        apiClient: APIClientProtocol = PublicEventService(),
        clock: ClockProtocol = SystemClock(),
        connectivityMonitor: ConnectivityMonitor = ConnectivityMonitor(),
        modelContext: ModelContext
    ) {
        self.apiClient = apiClient
        self.clock = clock
        self.connectivityMonitor = connectivityMonitor
        self.modelContext = modelContext

        // Load cached data immediately on init
        loadCachedData()

        // Start connectivity monitoring
        connectivityMonitor.start()

        // Observe connectivity changes
        Task {
            await observeConnectivity()
        }

        // Start periodic background refresh (every 5 minutes)
        Task {
            await startPeriodicRefresh()
        }

        // Trigger initial refresh
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

    // MARK: - Periodic Background Refresh

    /// Start periodic background refresh every 5 minutes when app is active
    /// AC#5: Silent background refresh
    /// AC#8: Phase transition handling
    @MainActor
    private func startPeriodicRefresh() async {
        while !Task.isCancelled {
            // Wait 5 minutes
            try? await Task.sleep(for: .seconds(300))

            // Only refresh if connected
            if connectivityMonitor.isConnected {
                print("🔄 PublicViewModel: Periodic refresh triggered (5-min interval)")
                await refreshEvent()
            }
        }
    }

    // MARK: - Connectivity Monitoring

    /// Observe connectivity changes and react accordingly
    /// AC#5: Auto-refresh when reconnecting
    /// AC#4: Set offline flag when disconnecting
    @MainActor
    private func observeConnectivity() async {
        // This runs continuously while the app is active
        while !Task.isCancelled {
            let currentlyConnected = connectivityMonitor.isConnected

            // Detect transition from disconnected → connected
            if wasOffline && currentlyConnected {
                print("🔄 PublicViewModel: Connectivity restored - triggering refresh")
                await refreshEvent()
            }

            // Detect transition from connected → disconnected
            if !wasOffline && !currentlyConnected {
                print("⚠️ PublicViewModel: Connectivity lost - setting offline mode")
                isOffline = true
            }

            // Update state for next iteration
            wasOffline = !currentlyConnected

            // Check every second
            try? await Task.sleep(for: .seconds(1))
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
