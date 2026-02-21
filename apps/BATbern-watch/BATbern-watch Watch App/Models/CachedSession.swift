//
//  CachedSession.swift
//  BATbern-watch Watch App
//
//  SwiftData model for locally cached session data.
//  Source: docs/watch-app/architecture.md#Data-Architecture
//

import Foundation
import SwiftData

@Model
class CachedSession {
    var sessionSlug: String
    var title: String
    var abstract: String?
    var sessionTypeRaw: String?  // Stores SessionType enum raw value
    var startTime: Date?
    var endTime: Date?
    @Relationship(deleteRule: .cascade) var speakers: [CachedSpeaker]
    var actualStartTime: Date?
    var actualEndTime: Date?
    var overrunMinutes: Int?
    var completedByUsername: String?

    /// True for break-type sessions (no speaker area, break icon shown).
    /// W4.4: includes .networking so BreakGongView triggers for networking slots too.
    var isBreak: Bool {
        sessionType == .breakTime || sessionType == .lunch || sessionType == .networking
    }

    /// Computed property for SessionType enum
    var sessionType: SessionType? {
        get {
            guard let raw = sessionTypeRaw else { return nil }
            return SessionType(rawValue: raw)
        }
        set {
            sessionTypeRaw = newValue?.rawValue
        }
    }

    init(
        sessionSlug: String,
        title: String,
        abstract: String? = nil,
        sessionType: SessionType? = nil,
        startTime: Date? = nil,
        endTime: Date? = nil,
        speakers: [CachedSpeaker] = [],
        actualStartTime: Date? = nil,
        actualEndTime: Date? = nil,
        overrunMinutes: Int? = nil,
        completedByUsername: String? = nil
    ) {
        self.sessionSlug = sessionSlug
        self.title = title
        self.abstract = abstract
        self.sessionTypeRaw = sessionType?.rawValue
        self.startTime = startTime
        self.endTime = endTime
        self.speakers = speakers
        self.actualStartTime = actualStartTime
        self.actualEndTime = actualEndTime
        self.overrunMinutes = overrunMinutes
        self.completedByUsername = completedByUsername
    }
}
