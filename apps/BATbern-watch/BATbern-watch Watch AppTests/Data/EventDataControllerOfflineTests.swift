//
//  EventDataControllerOfflineTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for EventDataController offline action replay.
//  Story W5.2 Task 6.3 — AC#3 (replay on reconnect), AC#4 (server reconciles).
//
//  Tests simulate the full flow:
//  1. Connectivity lost  → wasOffline = true
//  2. Actions queued     → OfflineActionQueue persisted
//  3. Connectivity back  → replayPendingActions() drains queue via WebSocket
//  4. Sync reconciles    → syncIfNeeded() called after drain
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

@Suite("EventDataController offline replay", .serialized)
@MainActor
struct EventDataControllerOfflineTests {

    // MARK: - Factory

    private struct Fixture {
        let controller: EventDataController
        let queue: OfflineActionQueue
        let wsClient: MockWebSocketClient
        let monitor: MockConnectivityMonitor
        let apiClient: MockAPIClient
    }

    private func makeFixture(wsConnected: Bool = false) throws -> Fixture {
        // Single in-memory container with all model types — mirrors production schema.
        // Using ModelContext(container) (not mainContext) matches the pattern used by other
        // passing EventDataController tests and avoids SwiftData EXC_BREAKPOINT crashes.
        let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self, OfflineAction.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: [config])
        let context = ModelContext(container)

        let queue = OfflineActionQueue(modelContext: context)

        let wsClient = MockWebSocketClient()
        wsClient._isConnected = wsConnected

        let monitor = MockConnectivityMonitor()
        let auth = MockAuthManager()
        let apiClient = MockAPIClient()
        // Sync fails gracefully (no active event) — we verify replay, not sync
        apiClient.fetchCurrentEventResult = .failure(MockError.notConfigured)

        let controller = EventDataController(
            publicClient: apiClient,
            authManager: auth,
            modelContext: context,
            connectivityMonitor: monitor,
            offlineActionQueue: queue,
            webSocketClient: wsClient,
            skipAutoSync: true
        )
        return Fixture(
            controller: controller,
            queue: queue,
            wsClient: wsClient,
            monitor: monitor,
            apiClient: apiClient
        )
    }

    /// Drive wasOffline=true by triggering the disconnect callback and waiting.
    private func goOffline(_ fixture: Fixture) async throws {
        fixture.monitor.simulateDisconnected()
        try await AsyncTestHelpers.waitFor { fixture.controller.isOffline == true }
    }

    // MARK: - isOffline flag lifecycle (W5.1 AC#3)

    @Test("isOffline goes true when connectivity-lost callback fires (W5.1 AC#3)")
    func isOffline_becomesTrueWhenConnectivityLost() async throws {
        let fixture = try makeFixture()

        #expect(fixture.controller.isOffline == false)

        // Simulate sustained WiFi drop (MockConnectivityMonitor bypasses the 30s debounce)
        fixture.monitor.simulateDisconnected()

        try await AsyncTestHelpers.waitFor(timeout: 2.0) { fixture.controller.isOffline == true }
        #expect(fixture.controller.isOffline == true)
    }

    @Test("isOffline clears automatically on WiFi restore — H1 regression guard")
    func isOffline_clearsAutomaticallyOnReconnect_noManualReset() async throws {
        // Regression guard: before H1 fix, handleConnectivityChange(true) never set isOffline=false,
        // so performSync() hit `guard !isOffline` and returned early, leaving isOffline=true forever.
        let fixture = try makeFixture()

        // Go offline (sets wasOffline=true, isOffline=true)
        try await goOffline(fixture)
        #expect(fixture.controller.isOffline == true)

        // Reconnect — without ANY manual isOffline=false workaround.
        // The fix in handleConnectivityChange(true) must clear isOffline itself.
        fixture.monitor.simulateConnected()

        try await AsyncTestHelpers.waitFor(timeout: 2.0) { fixture.controller.isOffline == false }
        #expect(fixture.controller.isOffline == false)
    }

    // MARK: - Replay on connectivity restored (AC#3)

    @Test("connectivity restored: replays queued actions via WebSocket (W5.2 AC#3)")
    func connectivityRestored_replaysQueuedActions() async throws {
        let fixture = try makeFixture()

        // Enqueue two actions while "offline"
        fixture.queue.enqueue(.endSession(sessionSlug: "cloud-talk"))
        fixture.queue.enqueue(.speakerArrived(speakerUsername: "anna.meier"))

        // Set wasOffline = true via disconnect callback
        try await goOffline(fixture)

        // WebSocket has reconnected before connectivity change fires
        fixture.wsClient._isConnected = true
        // Simulate WebSocket state update clearing isOffline
        fixture.controller.isOffline = false

        // Restore connectivity → triggers replayPendingActions()
        fixture.monitor.simulateConnected()

        // Wait for queue to drain
        try await AsyncTestHelpers.waitFor(timeout: 3.0) {
            fixture.queue.pendingActions().isEmpty
        }

        // Both actions sent via WebSocket in enqueue order
        #expect(fixture.wsClient.sentActions.count == 2)
        #expect(fixture.wsClient.sentActions[0] == .endSession(sessionSlug: "cloud-talk"))
        #expect(fixture.wsClient.sentActions[1] == .speakerArrived(speakerUsername: "anna.meier"))
    }

    @Test("connectivity restored: queue cleared after successful full replay (W5.2 AC#3)")
    func connectivityRestored_queueClearedAfterReplay() async throws {
        let fixture = try makeFixture()

        fixture.queue.enqueue(.startSession(sessionSlug: "t1"))
        fixture.queue.enqueue(.extendSession(sessionSlug: "t2", minutes: 5))
        fixture.queue.enqueue(.skipSession(sessionSlug: "t3"))

        try await goOffline(fixture)
        fixture.wsClient._isConnected = true
        fixture.controller.isOffline = false

        fixture.monitor.simulateConnected()
        try await AsyncTestHelpers.waitFor(timeout: 3.0) {
            fixture.queue.pendingActions().isEmpty
        }

        #expect(fixture.wsClient.sentActions.count == 3)
    }

    // MARK: - Failed replay: attempt cap (AC#4)

    @Test("failed replay: action dropped after 3 consecutive failures (W5.2 AC#4)")
    func failedReplay_actionDroppedAt3Attempts() async throws {
        let fixture = try makeFixture()

        fixture.queue.enqueue(.endSession(sessionSlug: "failing-talk"))

        // Pre-mark 2 prior failures directly (simulates 2 earlier offline cycles that failed).
        // The 3rd failure during the test's single reconnect cycle triggers the drop.
        let action = fixture.queue.pendingActions()[0]
        _ = fixture.queue.markFailed(action) // attempt 1
        _ = fixture.queue.markFailed(action) // attempt 2

        try await goOffline(fixture)

        // WebSocket "connected" but sendAction always fails
        fixture.wsClient._isConnected = true
        fixture.wsClient.sendActionShouldFail = true
        fixture.controller.isOffline = false

        // Single connectivity restore: attempt 3 → action must be dropped
        fixture.monitor.simulateConnected()

        // Queue should be empty after the 3rd-attempt drop
        try await AsyncTestHelpers.waitFor(timeout: 3.0) {
            fixture.queue.pendingActions().isEmpty
        }
    }

    // MARK: - No replay when WebSocket not connected

    @Test("replay skipped when WebSocket not yet reconnected (deferred until WS ready)")
    func replaySkipped_whenWebSocketNotConnected() async throws {
        let fixture = try makeFixture(wsConnected: false)

        fixture.queue.enqueue(.endSession(sessionSlug: "cloud-talk"))

        try await goOffline(fixture)
        // WebSocket is still not connected (not yet reconnected)
        fixture.controller.isOffline = false
        fixture.wsClient._isConnected = false

        fixture.monitor.simulateConnected()
        try await Task.sleep(for: .milliseconds(300))

        // Queue must be preserved (replay was skipped)
        #expect(fixture.queue.pendingActions().count == 1)
        // No actions sent to WebSocket
        #expect(fixture.wsClient.sentActions.isEmpty)
    }

    // MARK: - Empty queue: no-op

    @Test("connectivity restored with empty queue: no sendAction calls")
    func connectivityRestored_emptyQueue_noSendActions() async throws {
        let fixture = try makeFixture()

        // No enqueued actions
        try await goOffline(fixture)
        fixture.wsClient._isConnected = true
        fixture.controller.isOffline = false

        fixture.monitor.simulateConnected()
        try await Task.sleep(for: .milliseconds(200))

        #expect(fixture.wsClient.sentActions.isEmpty)
    }

    // MARK: - syncIfNeeded called after replay drain (AC#4 reconcile)

    @Test("replayPendingActions calls syncIfNeeded after drain for server reconciliation (W5.2 AC#4)")
    func replayPendingActions_callsSyncAfterDrain() async throws {
        let fixture = try makeFixture()

        fixture.queue.enqueue(.endSession(sessionSlug: "talk-1"))

        try await goOffline(fixture)
        fixture.wsClient._isConnected = true
        fixture.controller.isOffline = false

        fixture.monitor.simulateConnected()

        // Wait for drain
        try await AsyncTestHelpers.waitFor(timeout: 3.0) {
            fixture.queue.pendingActions().isEmpty
        }

        // syncIfNeeded was called → fetchCurrentEvent attempted (verifies reconcile sync fired)
        // MockAPIClient returns failure, but call count > 0 proves sync was attempted
        #expect(fixture.apiClient.fetchCurrentEventCallCount > 0)
    }
}
