//
//  MockConnectivityMonitor.swift
//  BATbern-watch Watch AppTests
//
//  Mock connectivity monitor for deterministic testing.
//  Allows tests to simulate connectivity state changes.
//

import Foundation
import Observation
@testable import BATbern_watch_Watch_App

@Observable
class MockConnectivityMonitor: ConnectivityMonitor {
    // Override the parent's isConnected property
    override var isConnected: Bool {
        get { _isConnected }
        set { _isConnected = newValue }
    }

    private var _isConnected: Bool = true

    // Track method calls for verification
    private(set) var startCallCount: Int = 0
    private(set) var stopCallCount: Int = 0

    override func start() {
        startCallCount += 1
        // Don't actually start NWPathMonitor in tests
    }

    override func stop() {
        stopCallCount += 1
        // Don't actually stop NWPathMonitor in tests
    }

    // Test helpers
    func simulateConnected() {
        let wasOffline = !_isConnected
        _isConnected = true

        // Trigger callback if state changed
        if wasOffline, let callback = onConnectivityChanged {
            Task { @MainActor in
                callback(true)
            }
        }
    }

    func simulateDisconnected() {
        let wasOnline = _isConnected
        _isConnected = false

        // Trigger callback if state changed
        if wasOnline, let callback = onConnectivityChanged {
            Task { @MainActor in
                callback(false)
            }
        }
    }

    func resetCallCounts() {
        startCallCount = 0
        stopCallCount = 0
    }
}
