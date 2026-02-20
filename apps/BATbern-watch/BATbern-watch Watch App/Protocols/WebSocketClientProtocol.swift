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
    /// W4.3: Re-activates the previous session with extra time; resets current to SCHEDULED.
    case delayToPrevious(currentSlug: String, minutes: Int)
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
    /// W4.3: Set for SESSION_DELAYED — slug of the session being re-activated (previous session).
    let previousSessionSlug: String?

    init(
        type: EventStateMessageType,
        sessionSlug: String? = nil,
        initiatedBy: String? = nil,
        timestamp: Date,
        stateUpdate: WatchStateUpdate? = nil,
        previousSessionSlug: String? = nil
    ) {
        self.type = type
        self.sessionSlug = sessionSlug
        self.initiatedBy = initiatedBy
        self.timestamp = timestamp
        self.stateUpdate = stateUpdate
        self.previousSessionSlug = previousSessionSlug
    }
}

/// Session state message types. Speaker arrivals are handled exclusively
/// via the dedicated `arrivalUpdates()` stream — NOT via this enum (W2.4, Task 1.6).
enum EventStateMessageType: String, Sendable {
    case sessionStarted = "SESSION_STARTED"
    case sessionEnded = "SESSION_ENDED"
    case sessionExtended = "SESSION_EXTENDED"
    case sessionSkipped = "SESSION_SKIPPED"
    /// W4.3: Previous session re-activated; current session demoted to SCHEDULED.
    case sessionDelayed = "SESSION_DELAYED"
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
    /// W4.4: True when the server has transitioned the event to EVENT_COMPLETED.
    /// Default false preserves backward compatibility with pre-W4.4 broadcasts.
    let eventCompleted: Bool

    init(
        sessions: [SessionStateUpdate],
        connectedOrganizers: [ConnectedOrganizer],
        serverTimestamp: Date,
        eventCompleted: Bool = false
    ) {
        self.sessions = sessions
        self.connectedOrganizers = connectedOrganizers
        self.serverTimestamp = serverTimestamp
        self.eventCompleted = eventCompleted
    }
}

/// Per-session state from the server broadcast.
struct SessionStateUpdate: Sendable {
    let sessionSlug: String
    let status: String
    let actualStartTime: Date?
    let actualEndTime: Date?
    let overrunMinutes: Int?
    let completedByUsername: String?
    /// W4.3: Updated scheduled start time — present when server shifts schedule (extend/delay).
    /// nil means no change. Watch applies to CachedSession.startTime.
    let newScheduledStartTime: Date?
    /// W4.3: Updated scheduled end time — present when server shifts schedule (extend/delay).
    /// nil means no change. Watch applies to CachedSession.endTime.
    let newScheduledEndTime: Date?

    init(
        sessionSlug: String,
        status: String,
        actualStartTime: Date? = nil,
        actualEndTime: Date? = nil,
        overrunMinutes: Int? = nil,
        completedByUsername: String? = nil,
        newScheduledStartTime: Date? = nil,
        newScheduledEndTime: Date? = nil
    ) {
        self.sessionSlug = sessionSlug
        self.status = status
        self.actualStartTime = actualStartTime
        self.actualEndTime = actualEndTime
        self.overrunMinutes = overrunMinutes
        self.completedByUsername = completedByUsername
        self.newScheduledStartTime = newScheduledStartTime
        self.newScheduledEndTime = newScheduledEndTime
    }
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

/// W4.3 Task 5.4: SESSION_DELAYED event — signals that the previous session was re-activated.
/// Consumed via consumeSessionDelayedEvent() (same pattern as SessionEndedEvent).
struct SessionDelayedEvent: Sendable, Equatable {
    let previousSessionSlug: String
    let currentSessionSlug: String
    let timestamp: Date
}
