import Foundation

/// Contract for STOMP WebSocket communication.
protocol WebSocketClientProtocol: AnyObject {
    /// Connection state.
    var isConnected: Bool { get }

    /// Connect to event's live channel.
    func connect(eventCode: String, accessToken: String) async throws

    /// Disconnect from current channel.
    func disconnect()

    /// Send a session action (advance, cascade, skip).
    func sendAction(_ action: WatchAction) async throws

    /// Subscribe to state updates. Returns an AsyncStream of state messages.
    func stateUpdates() -> AsyncStream<EventStateMessage>
}

/// Actions sent from Watch to server via STOMP.
enum WatchAction: Sendable, Equatable {
    case startSession(sessionSlug: String)
    case endSession(sessionSlug: String)
    case skipSession(sessionSlug: String)
    case extendSession(sessionSlug: String, minutes: Int)
    case speakerArrived(speakerUsername: String)
}

/// State update received from server broadcast.
struct EventStateMessage: Sendable {
    let type: EventStateMessageType
    let sessionSlug: String?
    let initiatedBy: String?
    let timestamp: Date
}

enum EventStateMessageType: String, Sendable {
    case sessionStarted = "SESSION_STARTED"
    case sessionEnded = "SESSION_ENDED"
    case sessionExtended = "SESSION_EXTENDED"
    case sessionSkipped = "SESSION_SKIPPED"
    case speakerArrived = "SPEAKER_ARRIVED"
    case heartbeat = "HEARTBEAT"
}
