//
//  EventDataController.swift
//  BATbern-watch Watch App
//
//  Unified event data source for both public and organizer zones.
//  Single 5-min refresh timer, single SwiftData write path, single portrait cache.
//  Always uses GET /api/v1/events/current — same endpoint for both zones.
//  The organizer zone adds behavior (arrival tracking, countdown) on top of this shared data.
//

import Foundation
import SwiftData
import os

private let logger = Logger(subsystem: "ch.batbern.watch", category: "EventDataController")

// MARK: - EventDataController

@Observable
@MainActor
final class EventDataController {

    // MARK: - Published State

    private(set) var currentEvent: CachedEvent?
    private(set) var isLoading: Bool = false
    private(set) var syncProgress: Double = 0.0
    private(set) var lastSynced: Date?
    private(set) var isOffline: Bool = false

    // MARK: - Dependencies

    private let publicClient: APIClientProtocol
    private let authManager: AuthManagerProtocol
    private let portraitCache: any PortraitCacheable
    private let modelContext: ModelContext
    private let connectivityMonitor: ConnectivityMonitor
    private let clock: ClockProtocol

    // MARK: - Sync Guard

    private var lastSyncAttempt: Date?
    private var wasOffline: Bool = false

    // MARK: - Init

    init(
        publicClient: APIClientProtocol = PublicEventService(),
        authManager: AuthManagerProtocol,
        portraitCache: any PortraitCacheable = PortraitCache.shared,
        modelContext: ModelContext,
        connectivityMonitor: ConnectivityMonitor = ConnectivityMonitor(),
        clock: ClockProtocol = SystemClock(),
        skipAutoSync: Bool = false
    ) {
        self.publicClient = publicClient
        self.authManager = authManager
        self.portraitCache = portraitCache
        self.modelContext = modelContext
        self.connectivityMonitor = connectivityMonitor
        self.clock = clock

        // Load SwiftData cache immediately so views have data before first network response
        loadCachedData()

        // Wire connectivity changes
        connectivityMonitor.onConnectivityChanged = { @Sendable [weak self] isConnected in
            Task { @MainActor in
                await self?.handleConnectivityChange(isConnected: isConnected)
            }
        }
        connectivityMonitor.start()

        guard !skipAutoSync else { return }

        // Initial sync
        Task { await syncIfNeeded() }

        // 5-minute periodic refresh — single timer for the whole app
        Task { await startPeriodicRefresh() }
    }

    // MARK: - Public API

    /// Sync with 60s minimum guard + 5-min skip when data is fresh and live.
    /// Called by OrganizerZoneView.onAppear and onChange(currentJWT).
    func syncIfNeeded() async {
        let now = clock.now

        // Skip if data is live/pre-event and was fetched within the last 5 minutes
        if currentEvent != nil,
           let last = lastSyncAttempt,
           now.timeIntervalSince(last) < 300 {
            logger.debug("syncIfNeeded: data fresh, skipping")
            return
        }

        // Hard minimum 60s between any syncs (prevents rate-limit floods on tab swipes)
        if let last = lastSyncAttempt, now.timeIntervalSince(last) < 60 {
            logger.debug("syncIfNeeded: within 60s cooldown, skipping")
            return
        }

        // Stamp before launching Task to prevent concurrent onAppear calls from all firing
        lastSyncAttempt = clock.now
        await performSync()
    }

    /// Force a sync regardless of guards — for pull-to-refresh or first zone entry.
    func forceSync() async {
        lastSyncAttempt = nil
        await performSync()
    }

    // MARK: - Private: Core Sync

    private func performSync() async {
        guard !isLoading else {
            logger.debug("performSync: already loading, skip")
            return
        }

        // TESTING_MODE: return mock data without hitting the network
        if ProcessInfo.processInfo.environment["TESTING_MODE"] == "1",
           let mockResponse = ProcessInfo.processInfo.environment["MOCK_SYNC_RESPONSE"] {
            await handleMockSyncResponse(mockResponse)
            return
        }

        isLoading = true
        syncProgress = 0.0

        do {
            let cachedEvent = try await fetchFromPublicEndpoint()
            await persistEvent(cachedEvent)
            currentEvent = cachedEvent
            lastSynced = clock.now
            isOffline = false
            syncProgress = 1.0
        } catch SyncError.notAuthenticated, SyncError.authenticationRequired {
            logger.warning("performSync: auth error — will retry when JWT refreshes")
            // Don't set isOffline; auth errors are not connectivity failures
        } catch SyncError.networkError {
            logger.warning("performSync: network error")
            isOffline = true
        } catch SyncError.noActiveEvent {
            logger.info("performSync: no active event")
            currentEvent = nil
        } catch {
            logger.error("performSync: unexpected error: \(error.localizedDescription)")
        }

        isLoading = false
    }

    // MARK: - Private: Fetch

    private func fetchFromPublicEndpoint() async throws -> CachedEvent {
        syncProgress = 0.1
        do {
            let watchEvent = try await publicClient.fetchCurrentEvent()
            syncProgress = 0.3
            let cachedEvent = watchEvent.toCachedEvent()
            syncProgress = 0.4
            await prefetchPortraits(for: cachedEvent)
            return cachedEvent
        } catch let apiError as APIError {
            switch apiError {
            case .noCurrentEvent: throw SyncError.noActiveEvent
            case .networkError:   throw SyncError.networkError
            default:              throw SyncError.networkError
            }
        }
    }

    // MARK: - Private: Portrait Prefetch

    private func prefetchPortraits(for cachedEvent: CachedEvent) async {
        let allSpeakers = cachedEvent.sessions.flatMap { $0.speakers }
        let total = allSpeakers.count
        let progressStart = syncProgress  // capture before loop

        for (index, speaker) in allSpeakers.enumerated() {
            if let urlString = speaker.profilePictureUrl,
               let url = URL(string: urlString) {
                _ = try? await portraitCache.downloadAndCache(url: url)
            }
            // Portraits span 40%→90% of progress
            syncProgress = progressStart + (0.5 * Double(index + 1) / Double(max(total, 1)))
        }
        syncProgress = 0.9
    }

    // MARK: - Private: SwiftData Write

    private func persistEvent(_ cachedEvent: CachedEvent) async {
        // Delete all stale records (cascade handles the tree; explicit delete clears
        // orphaned CachedSession/CachedSpeaker from before cascade delete was added).
        if let stale = try? modelContext.fetch(FetchDescriptor<CachedEvent>()) {
            stale.forEach { modelContext.delete($0) }
        }
        if let stale = try? modelContext.fetch(FetchDescriptor<CachedSession>()) {
            stale.forEach { modelContext.delete($0) }
        }
        if let stale = try? modelContext.fetch(FetchDescriptor<CachedSpeaker>()) {
            stale.forEach { modelContext.delete($0) }
        }
        modelContext.insert(cachedEvent)
        try? modelContext.save()
    }

    // MARK: - Private: Cache Load on Init

    private func loadCachedData() {
        let cache = LocalCache(modelContext: modelContext)
        if let cachedEvent = cache.getCachedEvent() {
            currentEvent = cachedEvent
            lastSynced = cachedEvent.lastSyncTimestamp
        }
    }

    // MARK: - Private: Periodic Refresh

    private func startPeriodicRefresh() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(300))
            if connectivityMonitor.isConnected {
                logger.debug("Periodic 5-min refresh triggered")
                await syncIfNeeded()
            }
        }
    }

    // MARK: - Private: Connectivity

    private func handleConnectivityChange(isConnected: Bool) async {
        if isConnected && wasOffline {
            logger.info("Connectivity restored — syncing")
            wasOffline = false
            await syncIfNeeded()
        } else if !isConnected && !wasOffline {
            logger.warning("Connectivity lost")
            wasOffline = true
            isOffline = true
        }
    }

    // MARK: - Mock Support (TESTING_MODE only)

    private func handleMockSyncResponse(_ response: String) async {
        isLoading = true
        syncProgress = 0.0

        switch response {
        case "loading":
            try? await Task.sleep(nanoseconds: 60_000_000_000)

        case "no_events":
            currentEvent = nil
            syncProgress = 1.0

        case "event_2h_away":
            currentEvent = makeMockEvent(startOffsetSeconds: 7200, endOffsetSeconds: 10800)
            syncProgress = 1.0

        case "event_30min_away":
            currentEvent = makeMockEvent(startOffsetSeconds: 1800, endOffsetSeconds: 5400)
            syncProgress = 1.0

        default:
            currentEvent = nil
            syncProgress = 1.0
        }

        isLoading = false
    }

    private func makeMockEvent(startOffsetSeconds: TimeInterval, endOffsetSeconds: TimeInterval) -> CachedEvent {
        let now = clock.now
        var zurichCalendar = Calendar(identifier: .gregorian)
        zurichCalendar.timeZone = TimeZone(identifier: "Europe/Zurich") ?? .current

        let startDate = now.addingTimeInterval(startOffsetSeconds)
        let endDate = now.addingTimeInterval(endOffsetSeconds)
        let startHH = zurichCalendar.component(.hour, from: startDate)
        let startMM = zurichCalendar.component(.minute, from: startDate)
        let endHH = zurichCalendar.component(.hour, from: endDate)
        let endMM = zurichCalendar.component(.minute, from: endDate)

        return CachedEvent(
            eventCode: "BAT99",
            title: "BATbern 99 - Cloud Native",
            eventDate: now,
            themeImageUrl: nil,
            venueName: "Uni Bern",
            typicalStartTime: String(format: "%02d:%02d", startHH, startMM),
            typicalEndTime: String(format: "%02d:%02d", endHH, endMM),
            currentPublishedPhase: nil,
            lastSyncTimestamp: now
        )
    }
}
