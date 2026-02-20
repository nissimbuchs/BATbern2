//
//  CachedEvent.swift
//  BATbern-watch Watch App
//
//  SwiftData model for locally cached event data from public API.
//  Source: docs/watch-app/architecture.md#Data-Architecture
//

import Foundation
import SwiftData

@Model
class CachedEvent {
    var eventCode: String
    var title: String
    var eventDate: Date
    var themeImageUrl: String?
    var venueName: String
    var typicalStartTime: String
    var typicalEndTime: String
    var currentPublishedPhase: String?  // TOPIC, SPEAKERS, AGENDA
    /// W4.4: Server-driven event lifecycle state (e.g. "EVENT_COMPLETED").
    /// Nil until the server broadcasts EVENT_COMPLETED. Optional preserves backward compat.
    var workflowState: String?
    @Relationship(deleteRule: .cascade) var sessions: [CachedSession]
    var lastSyncTimestamp: Date

    init(
        eventCode: String,
        title: String,
        eventDate: Date,
        themeImageUrl: String? = nil,
        venueName: String,
        typicalStartTime: String,
        typicalEndTime: String,
        currentPublishedPhase: String? = nil,
        workflowState: String? = nil,
        sessions: [CachedSession] = [],
        lastSyncTimestamp: Date = Date()
    ) {
        self.eventCode = eventCode
        self.title = title
        self.eventDate = eventDate
        self.themeImageUrl = themeImageUrl
        self.venueName = venueName
        self.typicalStartTime = typicalStartTime
        self.typicalEndTime = typicalEndTime
        self.currentPublishedPhase = currentPublishedPhase
        self.workflowState = workflowState
        self.sessions = sessions
        self.lastSyncTimestamp = lastSyncTimestamp
    }
}
