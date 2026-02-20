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
import OSLog

private let logger = Logger(subsystem: "ch.batbern.watch", category: "Connectivity")

@Observable
class ConnectivityMonitor {
    // MARK: - Published State

    /// True when WiFi/cellular is available, false when offline
    var isConnected: Bool = true

    // MARK: - Private Properties

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "ch.batbern.connectivity-monitor")
    private var previousConnectionState: Bool = true

    @ObservationIgnored
    private var offlineDebounceTask: Task<Void, Never>?

    /// Duration in seconds before the offline indicator notifies downstream after path loss.
    /// Suppresses transient drops under this threshold (AC3 — 30 s in production).
    /// Set to a lower value in unit tests to avoid slow test runs.
    var offlineDebounceSeconds: TimeInterval = 30

    /// Callback triggered when connectivity state changes (for reactive observation)
    @ObservationIgnored
    var onConnectivityChanged: (@MainActor @Sendable (Bool) -> Void)?

    // MARK: - Lifecycle

    /// Start monitoring network path changes.
    /// Updates isConnected on main actor when status changes.
    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                guard let self = self else { return }
                let newState = (path.status == .satisfied)

                // Only react when state actually changes
                if newState != self.previousConnectionState {
                    self.previousConnectionState = newState
                    self.isConnected = newState
                    self.processConnectivityChange(isConnected: newState)
                } else {
                    self.isConnected = newState
                }
            }
        }
        monitor.start(queue: queue)
        logger.info("ConnectivityMonitor started")
    }

    /// Stop monitoring and cancel NWPathMonitor. Also cancels any pending offline debounce.
    func stop() {
        offlineDebounceTask?.cancel()
        offlineDebounceTask = nil
        monitor.cancel()
    }

    deinit {
        stop()
    }

    // MARK: - Internal (visible to tests)

    /// Process a connectivity state change, applying a debounce to offline transitions only.
    ///
    /// - Online → immediate: cancels any pending debounce and notifies downstream right away.
    /// - Offline → debounced: waits `offlineDebounceSeconds` before notifying downstream,
    ///   suppressing transient drops that recover within that window.
    ///
    /// Marked `internal` (not `private`) so unit tests can exercise this logic directly
    /// without depending on `NWPathMonitor`.
    @MainActor
    func processConnectivityChange(isConnected: Bool) {
        if isConnected {
            offlineDebounceTask?.cancel()
            offlineDebounceTask = nil
            logger.info("📶 ONLINE — network path satisfied")
            onConnectivityChanged?(true)
        } else {
            logger.info("📵 Path lost — debounce started (\(self.offlineDebounceSeconds)s)")
            offlineDebounceTask?.cancel()
            offlineDebounceTask = Task { [weak self] in
                guard let self else { return }
                try? await Task.sleep(for: .seconds(self.offlineDebounceSeconds))
                guard !Task.isCancelled else { return }
                logger.warning("📵 OFFLINE — debounce expired, notifying downstream")
                onConnectivityChanged?(false)
            }
        }
    }
}
