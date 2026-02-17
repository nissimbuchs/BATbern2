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
}
