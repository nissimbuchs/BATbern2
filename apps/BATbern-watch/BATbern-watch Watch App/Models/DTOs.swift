//
//  DTOs.swift
//  BATbern-watch Watch App
//
//  Data Transfer Objects for API responses.
//  Maps backend JSON → domain models (WatchEvent) and SwiftData models (CachedEvent).
//  Source: docs/api/events-api.openapi.yml, docs/watch-app/prd-batbern-watch.md#Dev-Notes
//

import Foundation

// MARK: - Event Response

struct EventResponse: Codable {
    let eventCode: String
    let eventNumber: Int?
    let title: String
    let date: String  // ISO 8601 date string
    let themeImageUrl: String?
    let venueName: String
    let venueAddress: String?
    let typicalStartTime: String
    let typicalEndTime: String
    let workflowState: String
    let currentPublishedPhase: String?  // TOPIC, SPEAKERS, AGENDA
    let sessions: [SessionResponse]?
}

// MARK: - Session Response

struct SessionResponse: Codable {
    let sessionSlug: String
    let title: String
    let description: String?  // Abstract
    let sessionType: String?  // keynote, presentation, workshop, panel_discussion, networking, break, lunch
    let startTime: String?  // ISO 8601 datetime
    let endTime: String?    // ISO 8601 datetime
    let speakers: [SessionSpeakerResponse]?
}

// MARK: - Session Speaker Response

struct SessionSpeakerResponse: Codable {
    let username: String
    let firstName: String
    let lastName: String
    let company: String?
    let profilePictureUrl: String?
    let bio: String?
    let speakerRole: String  // PRIMARY_SPEAKER, CO_SPEAKER, MODERATOR, PANELIST
}

// MARK: - DTO Mapping Extensions

extension EventResponse {
    /// Convert API DTO to domain model
    func toWatchEvent() -> WatchEvent {
        let eventDate = ISO8601DateFormatter().date(from: date) ?? Date()
        let watchSessions = sessions?.compactMap { $0.toWatchSession() } ?? []

        return WatchEvent(
            id: eventCode,
            title: title,
            date: eventDate,
            themeImageUrl: themeImageUrl,
            venueName: venueName,
            sessions: watchSessions
        )
    }

    /// Convert API DTO to SwiftData model
    func toCachedEvent() -> CachedEvent {
        let eventDate = ISO8601DateFormatter().date(from: date) ?? Date()
        let cachedSessions = sessions?.compactMap { $0.toCachedSession() } ?? []

        return CachedEvent(
            eventCode: eventCode,
            title: title,
            eventDate: eventDate,
            themeImageUrl: themeImageUrl,
            venueName: venueName,
            typicalStartTime: typicalStartTime,
            typicalEndTime: typicalEndTime,
            currentPublishedPhase: currentPublishedPhase,
            sessions: cachedSessions,
            lastSyncTimestamp: Date()
        )
    }
}

extension SessionResponse {
    /// Convert API DTO to domain model
    func toWatchSession() -> WatchSession? {
        guard let start = startTime.flatMap({ ISO8601DateFormatter().date(from: $0) }),
              let end = endTime.flatMap({ ISO8601DateFormatter().date(from: $0) }),
              let type = sessionType.flatMap({ SessionType(rawValue: $0) }) else {
            return nil  // Skip placeholder sessions with missing data
        }

        let watchSpeakers = speakers?.compactMap { $0.toWatchSpeaker() } ?? []

        return WatchSession(
            id: sessionSlug,
            title: title,
            abstract: description,
            sessionType: type,
            startTime: start,
            endTime: end,
            speakers: watchSpeakers,
            state: .scheduled,
            actualStartTime: nil,
            overrunMinutes: nil
        )
    }

    /// Convert API DTO to SwiftData model
    func toCachedSession() -> CachedSession {
        let start = startTime.flatMap { ISO8601DateFormatter().date(from: $0) }
        let end = endTime.flatMap { ISO8601DateFormatter().date(from: $0) }
        let type = sessionType.flatMap { SessionType(rawValue: $0) }
        let cachedSpeakers = speakers?.compactMap { $0.toCachedSpeaker() } ?? []

        return CachedSession(
            sessionSlug: sessionSlug,
            title: title,
            abstract: description,
            sessionType: type,
            startTime: start,
            endTime: end,
            speakers: cachedSpeakers
        )
    }
}

extension SessionSpeakerResponse {
    /// Convert API DTO to domain model
    func toWatchSpeaker() -> WatchSpeaker {
        // Map backend role to domain role
        let role: SpeakerRole = {
            switch speakerRole {
            case "PRIMARY_SPEAKER": return .keynoteSpeaker
            case "MODERATOR": return .moderator
            default: return .panelist
            }
        }()

        return WatchSpeaker(
            id: username,
            firstName: firstName,
            lastName: lastName,
            company: company,
            companyLogoUrl: nil,  // Not provided in public API
            profilePictureUrl: profilePictureUrl,
            bio: bio,
            speakerRole: role,
            arrived: false,
            arrivedConfirmedBy: nil,
            arrivedAt: nil
        )
    }

    /// Convert API DTO to SwiftData model
    func toCachedSpeaker() -> CachedSpeaker {
        let role: SpeakerRole = {
            switch speakerRole {
            case "PRIMARY_SPEAKER": return .keynoteSpeaker
            case "MODERATOR": return .moderator
            default: return .panelist
            }
        }()

        return CachedSpeaker(
            username: username,
            firstName: firstName,
            lastName: lastName,
            company: company,
            profilePictureUrl: profilePictureUrl,
            bio: bio,
            speakerRole: role
        )
    }
}
