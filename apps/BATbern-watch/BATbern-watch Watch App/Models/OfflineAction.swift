//
//  OfflineAction.swift
//  BATbern-watch Watch App
//
//  SwiftData model for persisting queued offline actions.
//  Story W5.2 Task 1 — AC#2 (survives app restart, NFR11).
//
//  Actions enqueued while offline (WebSocket disconnected) are stored here.
//  Replayed in enqueuedAt order when connectivity is restored.
//

import Foundation
import SwiftData

/// Persistent record of a WatchAction queued while the WebSocket was offline.
/// Stored via SwiftData — survives app kill/restart (NFR11).
@Model
final class OfflineAction {

    /// Unique identifier. @Attribute(.unique) prevents duplicate inserts.
    @Attribute(.unique) var id: UUID

    /// Serialised action type string (e.g. "END_SESSION", "EXTEND_SESSION").
    /// Mirrors WatchActionDto.type — used for logging and filtering.
    var actionType: String

    /// JSON-encoded WatchActionDto — same wire format as WebSocketClient STOMP SEND body.
    var payload: Data

    /// Timestamp of when the action was first enqueued (used for ordered replay).
    var enqueuedAt: Date

    /// Number of failed replay attempts. Dropped silently after 3 failures.
    var attemptCount: Int

    init(
        id: UUID = UUID(),
        actionType: String,
        payload: Data,
        enqueuedAt: Date = Date(),
        attemptCount: Int = 0
    ) {
        self.id = id
        self.actionType = actionType
        self.payload = payload
        self.enqueuedAt = enqueuedAt
        self.attemptCount = attemptCount
    }
}
