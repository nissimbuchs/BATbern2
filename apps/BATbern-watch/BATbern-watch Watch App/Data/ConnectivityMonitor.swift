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

    // MARK: - Lifecycle

    /// Start monitoring network path changes
    /// Updates isConnected on main actor when status changes
    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                self?.isConnected = (path.status == .satisfied)
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
