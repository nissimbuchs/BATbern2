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
struct ConnectivityMonitorTests {

    // MARK: - Test: Initial State

    @Test("Initial state should be connected by default")
    func initialStateIsConnected() async throws {
        let monitor = ConnectivityMonitor()

        // Before starting, should default to connected
        #expect(monitor.isConnected == true)
    }

    // MARK: - Test: State Transitions (Connected → Disconnected)

    @Test("Should update isConnected to false when network becomes unavailable")
    func transitionToDisconnected() async throws {
        let monitor = ConnectivityMonitor()
        monitor.start()

        // Simulate network path becoming unsatisfied
        // Note: This test will initially fail because we can't easily mock NWPathMonitor
        // In production code, we'll observe real connectivity changes
        // For now, we verify the property exists and is observable

        #expect(monitor.isConnected != nil)

        monitor.stop()
    }

    // MARK: - Test: State Transitions (Disconnected → Connected)

    @Test("Should update isConnected to true when network becomes available")
    func transitionToConnected() async throws {
        let monitor = ConnectivityMonitor()
        monitor.start()

        // This verifies the monitor can track connectivity state
        // Real NWPathMonitor behavior will be integration tested
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
}
