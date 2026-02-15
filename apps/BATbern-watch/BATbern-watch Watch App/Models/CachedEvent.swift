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
    var sessions: [CachedSession]
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
        self.sessions = sessions
        self.lastSyncTimestamp = lastSyncTimestamp
    }
}
