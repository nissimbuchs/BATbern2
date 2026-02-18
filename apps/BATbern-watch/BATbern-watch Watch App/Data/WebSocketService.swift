//
//  WebSocketService.swift
//  BATbern-watch Watch App
//
//  Orchestration layer: connects WebSocketClient → EventDataController → LiveCountdownViewModel.
//  Story W4.1 Task 3.1-3.12.
//
//  ARCHITECTURE (Area 4 mandate):
//  webSocketClient.stateUpdates() → applyServerState() → EventDataController → EventStateManager → LiveCountdownViewModel
//
//  JWT REFRESH RULE (MEMORY.md):
//  NEVER call authManager.refreshJWT() from WebSocketService — causes infinite loop.
//  JWT refresh is handled by AuthManager's own timer. WebSocketService ONLY reacts to JWT changes.
//

import Foundation
import Observation
import OSLog

private let logger = Logger(subsystem: "ch.batbern.watch", category: "WebSocketService")

@Observable
@MainActor
final class WebSocketService {

    // MARK: - Published State (Task 3.8, W4.2)

    var presenceCount: Int = 0
    var connectedOrganizers: [ConnectedOrganizer] = []
    /// W4.2 Task 5.2/5.3: Set when SESSION_ENDED broadcast received. LiveCountdownView observes
    /// this via .onChange and resets it to nil after consuming (avoids persistent stale signal).
    var sessionEndedEvent: SessionEndedEvent?

    // MARK: - Connection State

    var isConnected: Bool { webSocketClient.isConnected }

    // MARK: - Dependencies (Task 3.2)

    private let webSocketClient: WebSocketClientProtocol
    private let eventDataController: EventDataController
    private let authManager: AuthManagerProtocol
    private let hapticService: HapticServiceProtocol

    // MARK: - Private Tasks

    private var stateConsumerTask: Task<Void, Never>?
    private var jwtObservationTask: Task<Void, Never>?
    private var currentEventCode: String?

    // MARK: - Init

    init(
        webSocketClient: WebSocketClientProtocol,
        eventDataController: EventDataController,
        authManager: AuthManagerProtocol,
        hapticService: HapticServiceProtocol = WatchHapticService()
    ) {
        self.webSocketClient = webSocketClient
        self.eventDataController = eventDataController
        self.authManager = authManager
        self.hapticService = hapticService
    }

    // MARK: - Public API

    /// Connect to event WebSocket and start consuming state updates.
    /// Task 3.3: guard JWT, connect client, start stream consumers.
    func connect(eventCode: String) async {
        guard let jwt = authManager.currentJWT else {
            logger.warning("WebSocketService.connect: no JWT available, skipping")
            return
        }
        currentEventCode = eventCode

        // Subscribe to streams BEFORE calling connect so no messages are missed.
        // Task 1.4: stateUpdates() stream set up before connection established.
        let stateStream = webSocketClient.stateUpdates()

        do {
            try await webSocketClient.connect(eventCode: eventCode, accessToken: jwt)
        } catch {
            logger.error("WebSocketService.connect: failed: \(error.localizedDescription, privacy: .public)")
            eventDataController.isOffline = true
            return
        }

        // Start state stream consumer in background (Task 3.4)
        startStateConsumer(stream: stateStream)

        // Observe JWT changes for transparent reconnect (Task 3.7)
        startJWTObservation(eventCode: eventCode)
    }

    /// Disconnect from WebSocket, cancel all tasks, reset presence state.
    /// Task 3.9.
    func disconnect() {
        stateConsumerTask?.cancel()
        stateConsumerTask = nil
        jwtObservationTask?.cancel()
        jwtObservationTask = nil
        currentEventCode = nil

        webSocketClient.disconnect()
        presenceCount = 0
        connectedOrganizers = []
        logger.info("WebSocketService disconnected")
    }

    /// W4.2 Task 5.1: Send a WatchAction to the server via the WebSocket client.
    /// Called from LiveCountdownView's Done button tap.
    /// NEVER throws to caller — errors are logged and swallowed (view doesn't need to handle them).
    func sendAction(_ action: WatchAction) async {
        do {
            try await webSocketClient.sendAction(action)
        } catch {
            logger.error("WebSocketService.sendAction failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Reconnect with a new JWT (called by JWT observation when token rotates).
    /// Task 3.7: NEVER calls authManager.refreshJWT().
    func reconnect(with jwt: String) async {
        guard let eventCode = currentEventCode else { return }
        logger.info("WebSocketService: reconnecting with refreshed JWT")
        webSocketClient.disconnect()

        let stateStream = webSocketClient.stateUpdates()

        do {
            try await webSocketClient.connect(eventCode: eventCode, accessToken: jwt)
            startStateConsumer(stream: stateStream)
        } catch {
            logger.error("WebSocketService.reconnect: failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    // MARK: - Private: State Stream Consumer (Task 3.4)

    private func startStateConsumer(stream: AsyncStream<EventStateMessage>) {
        stateConsumerTask?.cancel()
        stateConsumerTask = Task { @MainActor [weak self] in
            guard let self else { return }
            var wasConnected = true

            for await message in stream {
                guard !Task.isCancelled else { break }

                // Apply full server state to EventDataController (Area 4 mandate)
                if let stateUpdate = message.stateUpdate {
                    await eventDataController.applyServerState(stateUpdate)
                    connectedOrganizers = stateUpdate.connectedOrganizers
                    presenceCount = stateUpdate.connectedOrganizers.count
                }
                // W4.2 Task 5.2: Set sessionEndedEvent for LiveCountdownView O6 trigger.
                if message.type == .sessionEnded, let slug = message.sessionSlug {
                    sessionEndedEvent = SessionEndedEvent(
                        sessionSlug: slug,
                        completedBy: message.initiatedBy ?? "",
                        timestamp: message.timestamp
                    )
                }
                wasConnected = true
            }

            // Stream finished = disconnected unexpectedly
            guard !Task.isCancelled else { return }
            if wasConnected {
                onStreamFinished()
            }
        }
    }

    /// Called when the state stream finishes unexpectedly (WebSocket dropped).
    /// Task 3.5: set isOffline, fire connectionLost haptic exactly once.
    private func onStreamFinished() {
        eventDataController.isOffline = true
        hapticService.play(.connectionLost)
        logger.warning("WebSocketService: connection lost, haptic fired")
    }

    // MARK: - Private: JWT Observation (Task 3.7)

    /// Observe authManager.currentJWT changes using withObservationTracking loop.
    /// When JWT rotates (non-nil, different value), reconnect transparently.
    /// NEVER calls authManager.refreshJWT().
    private func startJWTObservation(eventCode: String) {
        jwtObservationTask?.cancel()
        jwtObservationTask = Task { @MainActor [weak self] in
            guard let self else { return }
            var previousJWT = self.authManager.currentJWT

            while !Task.isCancelled {
                await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                    withObservationTracking {
                        _ = self.authManager.currentJWT
                    } onChange: {
                        continuation.resume()
                    }
                }
                guard !Task.isCancelled else { return }
                let newJWT = self.authManager.currentJWT
                if newJWT != previousJWT, let jwt = newJWT, self.isConnected {
                    previousJWT = newJWT
                    await self.reconnect(with: jwt)
                } else {
                    previousJWT = newJWT
                }
            }
        }
    }
}
