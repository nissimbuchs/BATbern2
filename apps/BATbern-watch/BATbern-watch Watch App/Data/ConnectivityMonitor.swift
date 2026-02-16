//
//  ConnectivityMonitor.swift
//  BATbern-watch Watch App
//
//  NWPathMonitor wrapper for connectivity state monitoring.
//  Publishes isConnected state for SwiftUI observation.
//  Source: docs/watch-app/architecture.md#Connectivity-Monitoring
//  AC: #4 (Offline Browsing), #5 (Silent Background Refresh), #6 (Cold Launch Offline)
//

import Foundation
import Network
import Observation

@Observable
class ConnectivityMonitor {
    // MARK: - Published State

    /// True when WiFi/cellular is available, false when offline
    var isConnected: Bool = true

    // MARK: - Private Properties

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "ch.batbern.connectivity-monitor")
    private var previousConnectionState: Bool = true

    /// Callback triggered when connectivity state changes (for reactive observation)
    @ObservationIgnored
    var onConnectivityChanged: (@MainActor @Sendable (Bool) -> Void)?

    // MARK: - Lifecycle

    /// Start monitoring network path changes
    /// Updates isConnected on main actor when status changes
    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                guard let self = self else { return }
                let newState = (path.status == .satisfied)

                // Only trigger callback if state actually changed
                if newState != self.previousConnectionState {
                    self.previousConnectionState = newState
                    self.isConnected = newState
                    self.onConnectivityChanged?(newState)
                } else {
                    self.isConnected = newState
                }
            }
        }
        monitor.start(queue: queue)
    }

    /// Stop monitoring and cancel NWPathMonitor
    func stop() {
        monitor.cancel()
    }

    deinit {
        stop()
    }
}
