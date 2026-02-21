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
    /// Injected via init so tests can pass a short value without mutating production state.
    let offlineDebounceSeconds: TimeInterval

    /// Callback triggered when connectivity state changes (for reactive observation)
    @ObservationIgnored
    var onConnectivityChanged: (@MainActor @Sendable (Bool) -> Void)?

    // MARK: - Init

    init(offlineDebounceSeconds: TimeInterval = 30) {
        self.offlineDebounceSeconds = offlineDebounceSeconds
    }

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
    /// Must be called on MainActor — `offlineDebounceTask` is accessed under MainActor isolation.
    @MainActor
    func stop() {
        offlineDebounceTask?.cancel()
        offlineDebounceTask = nil
        monitor.cancel()
    }

    deinit {
        // NWPathMonitor.cancel() is thread-safe and must be called to release resources.
        // offlineDebounceTask exits cleanly via [weak self] guard when self is deallocated —
        // we cannot access it here without MainActor isolation, and explicit cancel is not needed.
        monitor.cancel()
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
