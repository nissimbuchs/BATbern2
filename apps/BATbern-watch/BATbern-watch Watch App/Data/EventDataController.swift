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
import WidgetKit
import os

private let logger = Logger(subsystem: "ch.batbern.watch", category: "EventDataController")

// MARK: - EventDataController

@Observable
@MainActor
final class EventDataController {

    // MARK: - Published State

    var currentEvent: CachedEvent?
    private(set) var isLoading: Bool = false
    private(set) var syncProgress: Double = 0.0
    private(set) var lastSynced: Date?
    /// W4.1: Settable by WebSocketService on disconnect (Area 3 — single isOffline flag).
    var isOffline: Bool = false

    // MARK: - Dependencies

    private let publicClient: APIClientProtocol
    private let authManager: AuthManagerProtocol
    private let portraitCache: any PortraitCacheable
    private let modelContext: ModelContext
    private let connectivityMonitor: ConnectivityMonitor
    private let clock: ClockProtocol
    /// W5.2 Task 4.1: Persistent queue for actions queued while offline.
    private let offlineActionQueue: (any OfflineActionQueueProtocol)?
    /// W5.2 Task 4.1: WebSocket client used to replay queued actions on reconnect.
    private let webSocketClient: (any WebSocketClientProtocol)?

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
        offlineActionQueue: (any OfflineActionQueueProtocol)? = nil,
        webSocketClient: (any WebSocketClientProtocol)? = nil,
        skipAutoSync: Bool = false
    ) {
        self.publicClient = publicClient
        self.authManager = authManager
        self.portraitCache = portraitCache
        self.modelContext = modelContext
        self.connectivityMonitor = connectivityMonitor
        self.clock = clock
        self.offlineActionQueue = offlineActionQueue
        self.webSocketClient = webSocketClient

        // Load SwiftData cache immediately so views have data before first network response
        loadCachedData()
        writeComplicationSnapshot()  // seed complication before LiveCountdownView opens
        ComplicationDataStore.reloadTimeline()  // force WidgetKit to call getTimeline on every launch

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

    // MARK: - W4.1: Server State Application

    /// Apply a server-broadcast state update to the local session cache.
    /// Called by WebSocketService on every STATE_UPDATE from /topic/events/{eventCode}/state.
    /// Source: story W4.1 Task 2.1-2.4, Area 4 mandate (single write path).
    @MainActor
    func applyServerState(_ update: WatchStateUpdate) {
        guard let event = currentEvent else { return }

        for sessionUpdate in update.sessions {
            guard let session = event.sessions.first(where: { $0.sessionSlug == sessionUpdate.sessionSlug })
            else { continue }
            session.actualStartTime = sessionUpdate.actualStartTime
            session.actualEndTime = sessionUpdate.actualEndTime
            session.overrunMinutes = sessionUpdate.overrunMinutes
            session.completedByUsername = sessionUpdate.completedByUsername
            // W4.3 Task 6.2: Apply schedule cascade from extend/delay broadcasts.
            // Server sends authoritative scheduled times — nil means no change.
            if let newStart = sessionUpdate.newScheduledStartTime {
                session.startTime = newStart
            }
            if let newEnd = sessionUpdate.newScheduledEndTime {
                session.endTime = newEnd
            }
        }

        // W4.4: Persist server-driven event completion — triggers OrganizerZoneView routing.
        if update.eventCompleted {
            currentEvent?.workflowState = "EVENT_COMPLETED"
        }

        isOffline = false
        lastSynced = clock.now
        try? modelContext.save()
    }

    // MARK: - Private: Core Sync

    private func performSync() async {
        guard !isLoading else {
            logger.debug("performSync: already loading, skip")
            return
        }
        // Guard: never attempt a sync when offline. In the simulator, WiFi can be
        // disabled while localhost remains reachable — so the request succeeds but the
        // local backend may return noCurrentEvent, which clears the cached event and
        // wipes it from the UI. The connectivity monitor auto-syncs when online again.
        guard !isOffline else {
            logger.debug("performSync: offline — preserving cached event")
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
            writeComplicationSnapshot()
            ComplicationDataStore.reloadTimeline()  // push fresh data to complication immediately
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
        // Pre-fetch theme image first (real watch: AsyncImage is unreliable for large CDN images)
        if let themeUrlString = cachedEvent.themeImageUrl,
           let themeUrl = URL(string: themeUrlString) {
            _ = try? await portraitCache.downloadAndCache(url: themeUrl)
        }

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
            // Only refresh on event day — battery conservation (no polling between events)
            if connectivityMonitor.isConnected && isEventDay() {
                logger.debug("Periodic 5-min refresh triggered (event day)")
                await syncIfNeeded()
            }
        }
    }

    /// True when the cached event date is today (Europe/Zurich calendar).
    private func isEventDay() -> Bool {
        guard let event = currentEvent else { return false }
        var zurichCalendar = Calendar(identifier: .gregorian)
        zurichCalendar.timeZone = TimeZone(identifier: "Europe/Zurich") ?? .current
        return zurichCalendar.isDateInToday(event.eventDate)
    }

    // MARK: - Private: Connectivity

    private func handleConnectivityChange(isConnected: Bool) async {
        if isConnected && wasOffline {
            logger.info("Connectivity restored — replaying offline queue then syncing")
            wasOffline = false
            // Clear offline indicator immediately on WiFi restore (AC3 semantics: indicator tracks
            // WiFi availability, not server reachability). Must happen BEFORE syncIfNeeded() so
            // performSync() can proceed past its `guard !isOffline` gate. If the subsequent sync
            // encounters a network error, performSync sets isOffline = true again.
            isOffline = false
            // W5.2 Task 4.2 (Dev Notes: replay BEFORE sync — replay first, then get fresh state)
            await replayPendingActions()
            await syncIfNeeded()
        } else if !isConnected && !wasOffline {
            logger.warning("Connectivity lost")
            wasOffline = true
            isOffline = true
        }
    }

    // MARK: - Private: Offline Action Replay (W5.2 Task 4.3)

    /// Replay queued offline actions through the WebSocket in chronological order.
    /// Requires WebSocket to be connected — skips silently if not yet reconnected.
    /// On success per action: removes from queue.
    /// On failure: increments attemptCount; drops action after 3 failures.
    /// Calls syncIfNeeded() after drain to reconcile Watch state with server.
    private func replayPendingActions() async {
        guard let queue = offlineActionQueue,
              let webSocket = webSocketClient else { return }

        let pending = queue.pendingActions()
        guard !pending.isEmpty else { return }

        // Skip if WebSocket not yet reconnected — queue persists for next connectivity event.
        guard webSocket.isConnected else {
            logger.info("replayPendingActions: WebSocket not connected yet, deferring \(pending.count) actions")
            return
        }

        logger.info("replayPendingActions: replaying \(pending.count) queued offline actions")

        for action in pending {
            guard let watchAction = decodeOfflineAction(action) else {
                logger.warning("replayPendingActions: dropping undecodable action \(action.actionType, privacy: .public)")
                queue.remove(action)
                continue
            }
            do {
                try await webSocket.sendAction(watchAction)
                queue.remove(action)
                logger.info("replayPendingActions: replayed \(action.actionType, privacy: .public)")
            } catch {
                logger.warning("replayPendingActions: send failed for \(action.actionType, privacy: .public): \(error.localizedDescription, privacy: .public)")
                if queue.markFailed(action) {
                    logger.warning("replayPendingActions: dropping stale action \(action.actionType, privacy: .public) after 3 attempts")
                    queue.remove(action)
                }
            }
        }

        // W5.2 Task 4.3: sync after drain to reconcile Watch with authoritative server state.
        await syncIfNeeded()
    }

    /// Decode an OfflineAction payload to a WatchAction for replay.
    private func decodeOfflineAction(_ offlineAction: OfflineAction) -> WatchAction? {
        guard let dto = try? JSONDecoder().decode(WatchActionDto.self, from: offlineAction.payload) else {
            return nil
        }
        return dto.toWatchAction()
    }

    // MARK: - Complication Snapshot (pre-session)

    /// Write a basic complication snapshot from event data, without requiring LiveCountdownView
    /// to be active. Called at app launch (loadCachedData) and after each successful sync.
    ///
    /// Handles the non-session contexts only (noEvent, eventFar, eventDayPreSession,
    /// eventComplete). If a session is currently running, LiveCountdownViewModel.refreshState()
    /// will overwrite with richer data (speaker names, urgency, second-level countdown).
    private func writeComplicationSnapshot() {
        let now = clock.now
        let sessions = (currentEvent?.sessions ?? [])
            .compactMap { $0.toWatchSession() }
            .sorted { $0.startTime < $1.startTime }

        // If a session is actively running, skip — LiveCountdownViewModel writes richer data.
        if sessions.contains(where: { $0.startTime <= now && $0.endTime >= now }) { return }

        let context: ComplicationContext
        if sessions.isEmpty {
            context = .noEvent
        } else if let last = sessions.last, now > last.endTime {
            context = .eventComplete
        } else if let next = sessions.first(where: { $0.startTime > now }) {
            let timeUntilNext = next.startTime.timeIntervalSince(now)
            if timeUntilNext > 24 * 3600 {
                let formatter = DateFormatter()
                formatter.dateFormat = "dd.MM"
                context = .eventFar(dateString: formatter.string(from: next.startTime))
            } else {
                let minutesUntil = max(0, Int(timeUntilNext / 60))
                let calendar = Calendar.current
                let startOfDay = calendar.startOfDay(for: now)
                let elapsedSinceMidnight = now.timeIntervalSince(startOfDay)
                let sessionStartSinceMidnight = next.startTime.timeIntervalSince(startOfDay)
                let progress = sessionStartSinceMidnight > 0
                    ? min(1.0, max(0.0, elapsedSinceMidnight / sessionStartSinceMidnight))
                    : 0.0
                context = .eventDayPreSession(minutesUntil: minutesUntil, progress: progress)
            }
        } else {
            context = .noEvent
        }

        ComplicationDataStore.write(ComplicationSnapshot(
            sessionTitle: nil,
            speakerNames: nil,
            scheduledEndTime: nil,   // no entry-level countdown needed for pre-session
            sessionDuration: nil,
            scheduledStartTime: nil,
            isLive: false,
            urgencyLevel: "normal",
            updatedAt: now,
            complicationContext: context
        ))
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
