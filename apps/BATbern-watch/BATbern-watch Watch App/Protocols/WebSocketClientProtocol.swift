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

    /// Subscribe to speaker arrival updates via `/topic/events/{eventCode}/arrivals`.
    /// Separate from stateUpdates() — lightweight arrival-only broadcasts (W2.4, FR39).
    func arrivalUpdates() -> AsyncStream<SpeakerArrivalMessage>
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
/// W4.1: stateUpdate carries full server state so WebSocketService can call
/// EventDataController.applyServerState() without a separate channel.
struct EventStateMessage: Sendable {
    let type: EventStateMessageType
    let sessionSlug: String?
    let initiatedBy: String?
    let timestamp: Date
    /// Full server state payload — present on every broadcast from the state topic.
    let stateUpdate: WatchStateUpdate?

    init(
        type: EventStateMessageType,
        sessionSlug: String? = nil,
        initiatedBy: String? = nil,
        timestamp: Date,
        stateUpdate: WatchStateUpdate? = nil
    ) {
        self.type = type
        self.sessionSlug = sessionSlug
        self.initiatedBy = initiatedBy
        self.timestamp = timestamp
        self.stateUpdate = stateUpdate
    }
}

/// Session state message types. Speaker arrivals are handled exclusively
/// via the dedicated `arrivalUpdates()` stream — NOT via this enum (W2.4, Task 1.6).
enum EventStateMessageType: String, Sendable {
    case sessionStarted = "SESSION_STARTED"
    case sessionEnded = "SESSION_ENDED"
    case sessionExtended = "SESSION_EXTENDED"
    case sessionSkipped = "SESSION_SKIPPED"
    case heartbeat = "HEARTBEAT"
}

/// Speaker arrival broadcast received via `/topic/events/{eventCode}/arrivals`.
/// Carries both the individual arrival and server-authoritative counts (FR39).
struct SpeakerArrivalMessage: Sendable {
    let speakerUsername: String
    let speakerFirstName: String
    let speakerLastName: String
    let confirmedBy: String
    let arrivedAt: Date
    /// Server-authoritative total arrived count (use for counter display — do NOT recompute locally).
    let arrivedCount: Int
    /// Server-authoritative total speaker count for tonight.
    let totalCount: Int
}

// MARK: - W4.1 Server State Types

/// Full server state broadcast — applied to local cache via EventDataController.applyServerState().
/// Source: story W4.1, Task 2.2 / Dev Notes WatchStateUpdateMessage JSON Shape.
struct WatchStateUpdate: Sendable {
    let sessions: [SessionStateUpdate]
    let connectedOrganizers: [ConnectedOrganizer]
    let serverTimestamp: Date
}

/// Per-session state from the server broadcast.
struct SessionStateUpdate: Sendable {
    let sessionSlug: String
    let status: String
    let actualStartTime: Date?
    let actualEndTime: Date?
    let overrunMinutes: Int?
    let completedByUsername: String?
}

/// Organizer presence entry from the server broadcast.
struct ConnectedOrganizer: Sendable, Equatable {
    let username: String
    let firstName: String
}

/// W4.2 Task 5.3: SESSION_ENDED event consumed by LiveCountdownView to trigger O6.
/// Reset to nil after the view consumes it (avoids persistent stale signal).
struct SessionEndedEvent: Sendable, Equatable {
    let sessionSlug: String
    let completedBy: String
    let timestamp: Date
}
