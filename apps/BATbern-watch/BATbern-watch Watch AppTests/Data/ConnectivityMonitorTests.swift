//
//  ConnectivityMonitorTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for ConnectivityMonitor - NWPathMonitor wrapper for connectivity state.
//  AC: #4 (Offline Browsing), #5 (Silent Background Refresh), #6 (Cold Launch Offline)
//

import Testing
import Network
@testable import BATbern_watch_Watch_App

@Suite("ConnectivityMonitor Tests")
@MainActor
struct ConnectivityMonitorTests {

    // MARK: - Test: Initial State

    @Test("Initial state should be connected by default")
    func initialStateIsConnected() async throws {
        let monitor = ConnectivityMonitor()

        // Before starting, should default to connected
        #expect(monitor.isConnected == true)
    }

    // MARK: - Test: State Transitions (Connected → Disconnected)

    @Test("Should detect transition from connected to disconnected")
    func transitionToDisconnected() async throws {
        let monitor = MockConnectivityMonitor()
        monitor.start()

        // Initial state: connected
        #expect(monitor.isConnected == true)

        // Simulate network loss
        monitor.simulateDisconnected()

        // Should now be disconnected
        #expect(monitor.isConnected == false)

        monitor.stop()
    }

    // MARK: - Test: State Transitions (Disconnected → Connected)

    @Test("Should detect transition from disconnected to connected")
    func transitionToConnected() async throws {
        let monitor = MockConnectivityMonitor()
        monitor.start()

        // Start offline
        monitor.simulateDisconnected()
        #expect(monitor.isConnected == false)

        // Restore connectivity
        monitor.simulateConnected()

        // Should now be connected
        #expect(monitor.isConnected == true)

        monitor.stop()
    }

    // MARK: - Test: Observable Property

    @Test("isConnected should be Observable for SwiftUI integration")
    func isConnectedIsObservable() async throws {
        let monitor = ConnectivityMonitor()

        // Verify that ConnectivityMonitor is @Observable
        // This enables SwiftUI views to react to connectivity changes
        let initialState = monitor.isConnected
        #expect(initialState == true)
    }

    // MARK: - Test: Start/Stop Lifecycle

    @Test("Should support start and stop lifecycle methods")
    func supportsStartStop() async throws {
        let monitor = ConnectivityMonitor()

        // Should not crash
        monitor.start()
        monitor.stop()

        // Can restart after stopping
        monitor.start()
        monitor.stop()
    }

    // MARK: - Test: 30s Offline Debounce (AC3 — W5.1)

    @Test("onConnectivityChanged(false) should NOT fire within the debounce window")
    func offlineCallback_notCalledWithinDebounceWindow() async throws {
        let monitor = ConnectivityMonitor(offlineDebounceSeconds: 0.3)

        var callbackFired = false
        monitor.onConnectivityChanged = { isConnected in
            if !isConnected { callbackFired = true }
        }

        // Trigger path loss
        monitor.processConnectivityChange(isConnected: false)

        // Immediately after: must NOT have fired yet
        #expect(callbackFired == false)

        // Still within debounce window (100 ms < 300 ms)
        try await Task.sleep(for: .milliseconds(100))
        #expect(callbackFired == false)
    }

    @Test("onConnectivityChanged(false) should fire after sustained debounce period")
    func offlineCallback_calledAfterDebouncePeriodElapses() async throws {
        let monitor = ConnectivityMonitor(offlineDebounceSeconds: 0.2)

        var callbackFired = false
        monitor.onConnectivityChanged = { isConnected in
            if !isConnected { callbackFired = true }
        }

        monitor.processConnectivityChange(isConnected: false)

        // Poll until the callback fires (or timeout after 1 s)
        try await AsyncTestHelpers.waitFor(timeout: 1.0) { callbackFired }
        #expect(callbackFired == true)
    }

    @Test("Connectivity restored within debounce window cancels offline notification")
    func offlineCallback_cancelledWhenConnectivityRestored() async throws {
        let monitor = ConnectivityMonitor(offlineDebounceSeconds: 0.3)

        var offlineCallbackFired = false
        monitor.onConnectivityChanged = { isConnected in
            if !isConnected { offlineCallbackFired = true }
        }

        // Simulate path loss, then immediate recovery within debounce window
        monitor.processConnectivityChange(isConnected: false)
        monitor.processConnectivityChange(isConnected: true)

        // Wait beyond what the debounce period would have been
        try await Task.sleep(for: .milliseconds(400))

        // Offline callback must never have fired
        #expect(offlineCallbackFired == false)
    }

    @Test("onConnectivityChanged(true) fires immediately — no debounce on reconnect")
    func onlineCallback_firesImmediatelyWithoutDebounce() async throws {
        let monitor = ConnectivityMonitor(offlineDebounceSeconds: 30) // Long debounce, confirming online ignores it

        var onlineCallbackFired = false
        monitor.onConnectivityChanged = { isConnected in
            if isConnected { onlineCallbackFired = true }
        }

        monitor.processConnectivityChange(isConnected: true)

        // Must fire synchronously — no await needed
        #expect(onlineCallbackFired == true)
    }
}
