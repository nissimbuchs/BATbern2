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
    /// this via .onChange and resets it to nil after consuming via consumeSessionEndedEvent().
    /// Review fix (items 1+6): private(set) prevents direct mutation from views;
    /// sessionEndedQueue buffers back-to-back SESSION_ENDED messages so no signal is dropped.
    private(set) var sessionEndedEvent: SessionEndedEvent?
    /// W4.3 Task 5.5: Set when SESSION_DELAYED broadcast received.
    private(set) var sessionDelayedEvent: SessionDelayedEvent?

    /// Internal queue for rapid back-to-back SESSION_ENDED messages (review fix item 1).
    private var sessionEndedQueue: [SessionEndedEvent] = []

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
        // Skip reconnect if already connected to the same event.
        // LiveCountdownView.onDisappear no longer calls disconnect(), so .task(id:) re-fires
        // on every onAppear cycle (sheet open/close). Without this guard each re-appear
        // would create a second WebSocket connection without closing the first.
        if webSocketClient.isConnected, currentEventCode == eventCode {
            logger.debug("WebSocketService.connect: already connected to \(eventCode, privacy: .public), skipping")
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

    /// W4.2 Review fix (items 1+6): Consumes the current SESSION_ENDED event signal and
    /// advances to the next queued event (if any). Called by LiveCountdownView after handling
    /// the event — encapsulates state reset inside the service instead of direct nil mutation from view.
    func consumeSessionEndedEvent() {
        if let next = sessionEndedQueue.first {
            sessionEndedQueue.removeFirst()
            sessionEndedEvent = next
        } else {
            sessionEndedEvent = nil
        }
    }

    /// W4.3 Task 5.7: Consumes the current SESSION_DELAYED event and returns it, then nils it out.
    func consumeSessionDelayedEvent() -> SessionDelayedEvent? {
        let event = sessionDelayedEvent
        sessionDelayedEvent = nil
        return event
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
                // W4.3 Task 5.6: Detect SESSION_DELAYED — record event for optional future UI feedback.
                // applyServerState is already called above for ALL message types — time updates handled there.
                if message.type == .sessionDelayed, let prevSlug = message.previousSessionSlug {
                    sessionDelayedEvent = SessionDelayedEvent(
                        previousSessionSlug: prevSlug,
                        currentSessionSlug: message.sessionSlug ?? "",
                        timestamp: message.timestamp
                    )
                }
                // W4.2 Task 5.2: Signal LiveCountdownView O6 trigger via SESSION_ENDED.
                // Review fix item 1: queue rapid back-to-back events so no signal is dropped.
                if message.type == .sessionEnded, let slug = message.sessionSlug {
                    let event = SessionEndedEvent(
                        sessionSlug: slug,
                        completedBy: message.initiatedBy ?? "",
                        timestamp: message.timestamp
                    )
                    if sessionEndedEvent == nil {
                        sessionEndedEvent = event
                    } else {
                        sessionEndedQueue.append(event)
                    }
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
