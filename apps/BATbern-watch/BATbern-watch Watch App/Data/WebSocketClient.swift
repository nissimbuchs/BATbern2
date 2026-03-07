//
//  WebSocketClient.swift
//  BATbern-watch Watch App
//
//  Concrete WebSocketClientProtocol implementation using URLSessionWebSocketTask + manual STOMP.
//  Story W4.1 Task 1.1-1.9.
//
//  STOMP 1.2 over raw WebSocket (no SockJS, no external library):
//  - Endpoint: {baseURL}/api/v1/watch/ws
//  - CONNECT with Bearer JWT + heart-beat:20000,0 headers
//  - Subscribe to /topic/events/{eventCode}/state and /topic/events/{eventCode}/arrivals
//  - Send JOIN to /app/watch/events/{eventCode}/join after CONNECTED
//  - Heartbeat: sends STOMP \n every 20s to keep ALB (120s idle timeout) connection alive
//  - Exponential backoff reconnect: 1s, 2s, 4s, ..., 60s max; retries indefinitely until
//    disconnect() is called (Fix: reconnect loop no longer stops on first failed attempt)
//
//  Thread safety: All public methods are called from @MainActor. The receive loop runs
//  in a background Task but only writes to AsyncStream continuations (safe by design).
//

import Foundation
import OSLog

private let logger = Logger(subsystem: "ch.batbern.watch", category: "WebSocketClient")

final class WebSocketClient: WebSocketClientProtocol, @unchecked Sendable {

    // MARK: - State

    private(set) var isConnected: Bool = false

    // MARK: - Dependencies

    private let urlSession: URLSession

    // MARK: - STOMP State

    private var webSocketTask: URLSessionWebSocketTask?
    private var currentEventCode: String?
    private var currentAccessToken: String?

    // MARK: - AsyncStream Continuations

    private var stateUpdatesContinuation: AsyncStream<EventStateMessage>.Continuation?
    private var arrivalUpdatesContinuation: AsyncStream<SpeakerArrivalMessage>.Continuation?

    // MARK: - Reconnect

    private var reconnectAttempt: Int = 0
    private var reconnectTask: Task<Void, Never>?
    private var receiveTask: Task<Void, Never>?
    private var heartbeatTask: Task<Void, Never>?

    // MARK: - Dependencies: Offline Queue

    private let offlineActionQueue: (any OfflineActionQueueProtocol)?

    // MARK: - Init

    /// - Parameters:
    ///   - urlSession: URLSession used for WebSocket tasks (default `.shared`).
    ///   - offlineActionQueue: Persistent queue for actions sent while offline (W5.2 Task 3.1).
    init(urlSession: URLSession = .shared, offlineActionQueue: (any OfflineActionQueueProtocol)? = nil) {
        self.urlSession = urlSession
        self.offlineActionQueue = offlineActionQueue
    }

    // MARK: - WebSocketClientProtocol

    func stateUpdates() -> AsyncStream<EventStateMessage> {
        AsyncStream { [weak self] continuation in
            self?.stateUpdatesContinuation = continuation
        }
    }

    func arrivalUpdates() -> AsyncStream<SpeakerArrivalMessage> {
        AsyncStream { [weak self] continuation in
            self?.arrivalUpdatesContinuation = continuation
        }
    }

    /// Connect to event WebSocket, perform STOMP handshake, subscribe to topics, send JOIN.
    /// Task 1.2-1.3.
    func connect(eventCode: String, accessToken: String) async throws {
        currentEventCode = eventCode
        currentAccessToken = accessToken

        let httpURLString = "\(BATbernAPIConfig.webSocketBaseURL)/api/v1/watch/ws"
        guard let httpURL = URL(string: httpURLString),
              var components = URLComponents(url: httpURL, resolvingAgainstBaseURL: false) else {
            throw WebSocketClientError.invalidURL
        }
        // URLSessionWebSocketTask requires ws:// or wss:// — convert from http/https.
        components.scheme = components.scheme == "https" ? "wss" : "ws"
        guard let url = components.url else {
            throw WebSocketClientError.invalidURL
        }

        let task = urlSession.webSocketTask(with: url)
        webSocketTask = task
        task.resume()

        // Send STOMP CONNECT frame.
        // heart-beat:20000,0 — client sends a \n ping every 20s; no server pings expected.
        // This keeps the ALB connection alive (ALB idle timeout = 120s). Fix for GH-551.
        let connectFrame = buildFrame(
            command: "CONNECT",
            headers: [
                "accept-version": "1.2",
                "heart-beat": "20000,0",
                "Authorization": "Bearer \(accessToken)"
            ]
        )
        try await task.send(.string(connectFrame))

        // Await CONNECTED response — loop to skip STOMP 1.2 heartbeat (\n) preambles
        // and WebSocket binary frames that may arrive before CONNECTED. (M2 fix)
        var connected = false
        for _ in 0..<20 {
            let response = try await task.receive()
            if case .string(let text) = response, let frame = parseFrame(text) {
                if frame.command == "CONNECTED" {
                    connected = true
                    break
                } else if frame.command == "ERROR" {
                    task.cancel(with: .goingAway, reason: nil)
                    throw WebSocketClientError.handshakeFailed
                }
                // Other frames (heartbeat \n) — skip and keep waiting
            }
            // Binary frames — skip and keep waiting
        }
        guard connected else {
            task.cancel(with: .goingAway, reason: nil)
            throw WebSocketClientError.handshakeFailed
        }

        // Subscribe to state topic (Task 1.3)
        try await task.send(.string(buildFrame(
            command: "SUBSCRIBE",
            headers: [
                "id": "sub-state",
                "destination": "/topic/events/\(eventCode)/state"
            ]
        )))

        // Subscribe to arrivals topic (Task 1.3)
        try await task.send(.string(buildFrame(
            command: "SUBSCRIBE",
            headers: [
                "id": "sub-arrivals",
                "destination": "/topic/events/\(eventCode)/arrivals"
            ]
        )))

        // Send JOIN action (Task 1.3)
        try await task.send(.string(buildFrame(
            command: "SEND",
            headers: ["destination": "/app/watch/events/\(eventCode)/join"]
        )))

        isConnected = true
        reconnectAttempt = 0
        logger.info("WebSocketClient connected to event \(eventCode, privacy: .public)")

        startReceiveLoop()
        startHeartbeat()
    }

    /// STOMP DISCONNECT + close WebSocket task, finish AsyncStream continuations.
    /// Task 1.7.
    func disconnect() {
        reconnectTask?.cancel()
        reconnectTask = nil
        receiveTask?.cancel()
        receiveTask = nil
        heartbeatTask?.cancel()
        heartbeatTask = nil

        if let task = webSocketTask {
            // Notify server of explicit leave before STOMP DISCONNECT (M5: wire leave endpoint)
            if let eventCode = currentEventCode {
                let leaveFrame = buildFrame(
                    command: "SEND",
                    headers: ["destination": "/app/watch/events/\(eventCode)/leave"]
                )
                Task { try? await task.send(.string(leaveFrame)) }
            }
            let disconnectFrame = buildFrame(command: "DISCONNECT", headers: [:])
            Task { try? await task.send(.string(disconnectFrame)) }
            task.cancel(with: .normalClosure, reason: nil)
        }
        webSocketTask = nil

        isConnected = false
        stateUpdatesContinuation?.finish()
        arrivalUpdatesContinuation?.finish()
        currentEventCode = nil
        currentAccessToken = nil
        logger.info("WebSocketClient disconnected")
    }

    /// STOMP SEND to action destination with JSON-encoded WatchAction.
    /// Task 1.6 (W4.1); W5.2 Task 3.2-3.4: enqueue to OfflineActionQueue when offline.
    ///
    /// Offline path: if WebSocket is not connected, the action is enqueued in
    /// `offlineActionQueue` and the call returns normally (no error thrown).
    /// The UI has already updated optimistically — no caller change required.
    ///
    /// Online path: unchanged — action is sent via STOMP SEND frame.
    func sendAction(_ action: WatchAction) async throws {
        guard let task = webSocketTask, isConnected else {
            // W5.2 Task 3.2: enqueue instead of throwing when offline
            offlineActionQueue?.enqueue(action)
            logger.info("WebSocketClient offline — action enqueued")
            return
        }
        let eventCode = currentEventCode ?? ""
        let dto = WatchActionDto(from: action)
        let body = try JSONEncoder().encode(dto)
        let bodyString = String(data: body, encoding: .utf8) ?? "{}"

        let frame = buildFrame(
            command: "SEND",
            headers: [
                "destination": "/app/watch/events/\(eventCode)/action",
                "content-type": "application/json",
                "content-length": "\(body.count)"
            ],
            body: bodyString
        )
        try await task.send(.string(frame))
    }

    // MARK: - Private: STOMP Heartbeat (Fix GH-551)

    /// Sends a STOMP 1.2 heartbeat (\n) every 20 seconds to keep the ALB connection alive.
    /// The ALB idle timeout is 120s; 20s gives 6× margin. Cancelled by disconnect() or
    /// handleUnexpectedDisconnect().
    private func startHeartbeat() {
        heartbeatTask?.cancel()
        heartbeatTask = Task { [weak self] in
            while let self, !Task.isCancelled, self.webSocketTask != nil {
                do {
                    try await Task.sleep(nanoseconds: 20_000_000_000) // 20 seconds
                    guard !Task.isCancelled, let wsTask = self.webSocketTask else { break }
                    // STOMP 1.2 heartbeat frame: a single newline
                    try? await wsTask.send(.string("\n"))
                    logger.debug("WebSocketClient: heartbeat sent")
                } catch {
                    break
                }
            }
        }
    }

    // MARK: - Private: Receive Loop

    private func startReceiveLoop() {
        receiveTask?.cancel()
        receiveTask = Task { [weak self] in
            var buffer = ""
            while let self, !Task.isCancelled, self.webSocketTask != nil {
                do {
                    guard let wsTask = self.webSocketTask else { break }
                    let message = try await wsTask.receive()
                    if case .string(let text) = message {
                        buffer += text
                        // STOMP frames are terminated by NULL (\0)
                        while let nullIdx = buffer.firstIndex(of: "\0") {
                            let frameText = String(buffer[..<nullIdx])
                            buffer = String(buffer[buffer.index(after: nullIdx)...])
                            if let frame = self.parseFrame(frameText) {
                                // Dispatch to MainActor so handleFrame runs in the same isolation
                                // domain where stateUpdatesContinuation/arrivalUpdatesContinuation
                                // are managed — eliminates the data race. (H3 fix)
                                await MainActor.run { [weak self] in
                                    self?.handleFrame(frame)
                                }
                            }
                        }
                    }
                } catch {
                    guard !Task.isCancelled else { return }
                    logger.warning("WebSocketClient receive error: \(error.localizedDescription, privacy: .public)")
                    await self.handleUnexpectedDisconnect()
                    return
                }
            }
        }
    }

    private func handleFrame(_ frame: StompFrame) {
        switch frame.command {
        case "MESSAGE":
            routeMessage(frame)
        case "ERROR":
            logger.error("STOMP ERROR from server: \(frame.body, privacy: .public)")
            Task { await handleUnexpectedDisconnect() }
        case "HEARTBEAT", "RECEIPT":
            break // No-op
        default:
            logger.debug("Unhandled STOMP command: \(frame.command, privacy: .public)")
        }
    }

    private func routeMessage(_ frame: StompFrame) {
        let destination = frame.headers["destination"] ?? ""

        if destination.hasSuffix("/state") {
            handleStateMessage(frame.body)
        } else if destination.hasSuffix("/arrivals") {
            handleArrivalMessage(frame.body)
        }
    }

    // MARK: - Private: Message Decoding

    /// Decode WatchStateServerMessage from JSON body and yield EventStateMessage with full state.
    /// Task 1.4: yield decoded EventStateMessage on each STOMP MESSAGE from state topic.
    private func handleStateMessage(_ body: String) {
        guard let data = body.data(using: .utf8),
              let serverMsg = try? JSONDecoder().decode(WatchStateServerMessage.self, from: data) else {
            logger.warning("WebSocketClient: failed to decode state message")
            return
        }
        let stateUpdate = serverMsg.toWatchStateUpdate()
        let message = EventStateMessage(
            type: serverMsg.messageType,
            sessionSlug: serverMsg.sessionSlug,
            initiatedBy: serverMsg.initiatedBy,
            timestamp: stateUpdate.serverTimestamp,
            stateUpdate: stateUpdate
        )
        stateUpdatesContinuation?.yield(message)
    }

    /// Decode SpeakerArrivalServerMessage from JSON body and yield SpeakerArrivalMessage.
    /// Task 1.5.
    private func handleArrivalMessage(_ body: String) {
        guard let data = body.data(using: .utf8),
              let serverMsg = try? JSONDecoder().decode(SpeakerArrivalServerMessage.self, from: data) else {
            logger.warning("WebSocketClient: failed to decode arrival message")
            return
        }
        let message = SpeakerArrivalMessage(
            speakerUsername: serverMsg.speakerUsername,
            speakerFirstName: serverMsg.speakerFirstName,
            speakerLastName: serverMsg.speakerLastName,
            confirmedBy: serverMsg.confirmedBy,
            arrivedAt: serverMsg.arrivedAtDate,
            arrivedCount: serverMsg.arrivedCount,
            totalCount: serverMsg.totalCount
        )
        arrivalUpdatesContinuation?.yield(message)
    }

    // MARK: - Private: Exponential Backoff Reconnect (Task 1.8)

    @MainActor
    private func handleUnexpectedDisconnect() async {
        isConnected = false
        webSocketTask = nil
        receiveTask = nil
        heartbeatTask?.cancel()
        heartbeatTask = nil

        guard let eventCode = currentEventCode,
              let jwt = currentAccessToken else { return }

        scheduleReconnect(eventCode: eventCode, jwt: jwt)
    }

    private func scheduleReconnect(eventCode: String, jwt: String) {
        let delay = min(pow(2.0, Double(reconnectAttempt)), 60.0)
        reconnectAttempt += 1
        logger.info("WebSocketClient: reconnect attempt \(self.reconnectAttempt, privacy: .public) in \(delay, privacy: .public)s")

        reconnectTask = Task { [weak self] in
            do {
                try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                guard !Task.isCancelled, let self else { return }
                try await self.connect(eventCode: eventCode, accessToken: jwt)
            } catch {
                guard !Task.isCancelled, let self else { return }
                logger.warning("WebSocketClient: reconnect failed: \(error.localizedDescription, privacy: .public)")
                // Fix GH-551: retry indefinitely until disconnect() is called (which cancels this Task).
                self.scheduleReconnect(eventCode: eventCode, jwt: jwt)
            }
        }
    }

    // MARK: - Private: STOMP Frame Utilities

    private func buildFrame(command: String, headers: [String: String], body: String = "") -> String {
        var frame = command + "\n"
        for (key, value) in headers {
            frame += "\(key):\(value)\n"
        }
        frame += "\n"
        frame += body
        frame += "\0"
        return frame
    }

    private struct StompFrame {
        let command: String
        let headers: [String: String]
        let body: String
    }

    private func parseFrame(_ text: String) -> StompFrame? {
        // Remove any leading whitespace or NULL bytes
        var stripped = text
        while stripped.hasPrefix("\n") || stripped.hasPrefix("\r") {
            stripped = String(stripped.dropFirst())
        }
        guard !stripped.isEmpty else { return nil }

        var lines = stripped.components(separatedBy: "\n")
        guard let command = lines.first, !command.isEmpty else { return nil }
        lines.removeFirst()

        var headers: [String: String] = [:]
        var bodyStartIdx = 0

        for (idx, line) in lines.enumerated() {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty {
                bodyStartIdx = idx + 1
                break
            }
            if let colonIdx = trimmed.firstIndex(of: ":") {
                let key = String(trimmed[..<colonIdx])
                let value = String(trimmed[trimmed.index(after: colonIdx)...])
                    .trimmingCharacters(in: .whitespaces)
                headers[key] = value
            }
        }

        let bodyLines = bodyStartIdx < lines.count ? Array(lines[bodyStartIdx...]) : []
        let body = bodyLines.joined(separator: "\n")
            .trimmingCharacters(in: CharacterSet(charactersIn: "\0\r\n"))

        return StompFrame(command: command.trimmingCharacters(in: .whitespaces), headers: headers, body: body)
    }
}

// MARK: - WebSocketClientError

enum WebSocketClientError: Error, LocalizedError {
    case invalidURL
    case handshakeFailed
    case notConnected

    var errorDescription: String? {
        switch self {
        case .invalidURL:        return "Invalid WebSocket URL"
        case .handshakeFailed:   return "STOMP handshake failed"
        case .notConnected:      return "WebSocket not connected"
        }
    }
}

// MARK: - Server Message DTOs (internal, not on protocol)

/// JSON shape of the server's WatchStateUpdateMessage broadcast.
/// Source: story W4.1 Dev Notes — WatchStateUpdateMessage JSON Shape.
/// W4.2 Task 8.2: Added sessionSlug and initiatedBy top-level fields.
/// W4.4: Added eventCompleted (optional for backward compat with pre-W4.4 server versions).
private struct WatchStateServerMessage: Decodable {
    let type: String
    let trigger: String?
    let sessionSlug: String?     // W4.2: slug of session that triggered the update
    let initiatedBy: String?     // W4.2: username of organizer who took the action
    let eventCode: String
    let sessions: [SessionDto]
    let connectedOrganizers: [OrganizerDto]
    let serverTimestamp: String
    let eventCompleted: Bool?    // W4.4: true when all completeable sessions done (nil on old servers)

    struct SessionDto: Decodable {
        let sessionSlug: String
        let status: String
        let actualStartTime: String?
        let actualEndTime: String?
        let overrunMinutes: Int?
        let completedBy: String?
        /// W4.3: Updated scheduled times from extend/delay cascade — nil means no change.
        /// Backend sends current DB values (post-update) in every STATE_UPDATE.
        let newScheduledStartTime: String?
        let newScheduledEndTime: String?
    }

    struct OrganizerDto: Decodable {
        let username: String
        let firstName: String
    }

    var messageType: EventStateMessageType {
        switch trigger {
        case "SESSION_STARTED":  return .sessionStarted
        case "SESSION_ENDED":    return .sessionEnded
        case "SESSION_EXTENDED": return .sessionExtended
        case "SESSION_DELAYED":  return .sessionDelayed
        case "SESSION_SKIPPED":  return .sessionSkipped
        default:                 return .heartbeat
        }
    }

    func toWatchStateUpdate() -> WatchStateUpdate {
        let ts = Self.parseTimestamp(serverTimestamp) ?? Date()
        return WatchStateUpdate(
            sessions: sessions.map { dto in
                SessionStateUpdate(
                    sessionSlug: dto.sessionSlug,
                    status: dto.status,
                    actualStartTime: dto.actualStartTime.flatMap { Self.parseTimestamp($0) },
                    actualEndTime: dto.actualEndTime.flatMap { Self.parseTimestamp($0) },
                    overrunMinutes: dto.overrunMinutes,
                    completedByUsername: dto.completedBy,
                    newScheduledStartTime: dto.newScheduledStartTime.flatMap { Self.parseTimestamp($0) },
                    newScheduledEndTime: dto.newScheduledEndTime.flatMap { Self.parseTimestamp($0) }
                )
            },
            connectedOrganizers: connectedOrganizers.map {
                ConnectedOrganizer(username: $0.username, firstName: $0.firstName)
            },
            serverTimestamp: ts,
            eventCompleted: eventCompleted ?? false
        )
    }

    /// Parse ISO8601 timestamp, handling both with and without fractional seconds.
    /// Instant.now().toString() in Java omits fractional seconds when sub-ms is zero. (M6 fix)
    private static func parseTimestamp(_ string: String) -> Date? {
        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFraction.date(from: string) { return date }
        let withoutFraction = ISO8601DateFormatter()
        withoutFraction.formatOptions = [.withInternetDateTime]
        return withoutFraction.date(from: string)
    }
}

/// JSON shape of the server's speaker arrival broadcast.
private struct SpeakerArrivalServerMessage: Decodable {
    let speakerUsername: String
    let speakerFirstName: String
    let speakerLastName: String
    let confirmedBy: String
    let arrivedAt: String
    let arrivedCount: Int
    let totalCount: Int

    var arrivedAtDate: Date {
        ISO8601DateFormatter().date(from: arrivedAt) ?? Date()
    }
}

/// JSON encoding/decoding of WatchAction for STOMP SEND to action endpoint.
/// Task 1.6 (W4.1). Made Codable (W5.2 Task 2.3/4.3) so OfflineActionQueue can encode
/// for persistence and EventDataController can decode for replay.
struct WatchActionDto: Codable {
    let type: String
    let sessionSlug: String?
    let minutes: Int?
    let speakerUsername: String?

    init(from action: WatchAction) {
        switch action {
        case .startSession(let slug):
            type = "START_SESSION"; sessionSlug = slug; minutes = nil; speakerUsername = nil
        case .endSession(let slug):
            type = "END_SESSION"; sessionSlug = slug; minutes = nil; speakerUsername = nil
        case .skipSession(let slug):
            type = "SKIP_SESSION"; sessionSlug = slug; minutes = nil; speakerUsername = nil
        case .extendSession(let slug, let mins):
            type = "EXTEND_SESSION"; sessionSlug = slug; minutes = mins; speakerUsername = nil
        case .delayToPrevious(let slug, let mins):
            type = "DELAY_TO_PREVIOUS"; sessionSlug = slug; minutes = mins; speakerUsername = nil
        case .speakerArrived(let username):
            type = "SPEAKER_ARRIVED"; sessionSlug = nil; minutes = nil; speakerUsername = username
        }
    }

    /// Reconstruct the WatchAction from a decoded DTO — used by replayPendingActions (W5.2 Task 4.3).
    func toWatchAction() -> WatchAction? {
        switch type {
        case "START_SESSION":
            guard let slug = sessionSlug else { return nil }
            return .startSession(sessionSlug: slug)
        case "END_SESSION":
            guard let slug = sessionSlug else { return nil }
            return .endSession(sessionSlug: slug)
        case "SKIP_SESSION":
            guard let slug = sessionSlug else { return nil }
            return .skipSession(sessionSlug: slug)
        case "EXTEND_SESSION":
            guard let slug = sessionSlug, let mins = minutes else { return nil }
            return .extendSession(sessionSlug: slug, minutes: mins)
        case "DELAY_TO_PREVIOUS":
            guard let slug = sessionSlug, let mins = minutes else { return nil }
            return .delayToPrevious(currentSlug: slug, minutes: mins)
        case "SPEAKER_ARRIVED":
            guard let username = speakerUsername else { return nil }
            return .speakerArrived(speakerUsername: username)
        default:
            return nil
        }
    }
}
