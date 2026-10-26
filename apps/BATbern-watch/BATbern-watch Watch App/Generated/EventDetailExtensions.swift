//
// EventDetailExtensions.swift
// BATbern-watch Watch App
//

import Foundation

// MARK: - Type Mapping Helpers

/// The contract's session types are a closed enum again (#1123), but installed watches can't be
/// updated in step with the backend, so the models are generated with `enumUnknownDefaultCase`
/// (#1126): a value this binary doesn't know decodes as `.unknownDefaultOpenApi` instead of failing
/// the whole event (how 2.3 broke on `aperitif`). Unknown types are kept as a timed talk slot rather
/// than dropped: the organizer countdown needs every slot, and a mislabelled slot is better than a
/// missing one.
private func mapSessionType(_ generated: ApiSessionType) -> SessionType {
    switch generated {
    case .keynote: return .keynote
    case .presentation: return .presentation
    case .workshop: return .workshop
    case .panelDiscussion: return .panelDiscussion
    case .networking: return .networking
    case ._break: return .breakTime
    case .lunch: return .lunch
    case .moderation: return .moderation
    case .unknownDefaultOpenApi: return .presentation
    }
}

private func mapSpeakerRole(_ generated: SessionSpeaker.SpeakerRole) -> SpeakerRole {
    switch generated {
    case .primarySpeaker: return .primarySpeaker
    case .coSpeaker: return .coSpeaker
    case .moderator: return .moderator
    case .panelist: return .panelist
    case .unknownDefaultOpenApi: return .panelist  // same fallback as CachedSpeaker
    }
}

// MARK: - EventDetail → WatchEvent

extension EventDetail {
    func toWatchEvent() -> WatchEvent {
        let watchSessions = sessions?.compactMap { $0.toWatchSession() } ?? []

        // Map generated enum to String (EventDetail.currentPublishedPhase is now generated)
        let phaseString = currentPublishedPhase?.rawValue

        return WatchEvent(
            id: eventCode,
            title: title,
            date: date,
            themeImageUrl: themeImageUrl,
            venueName: venueName,
            sessions: watchSessions,
            currentPublishedPhase: phaseString,
            typicalStartTime: typicalStartTime ?? "18:00",
            typicalEndTime: typicalEndTime ?? "22:00"
        )
    }

    func toCachedEvent() -> CachedEvent {
        let cachedSessions = sessions?.compactMap { $0.toCachedSession() } ?? []

        // Use generated field for cached event too
        let phaseString = currentPublishedPhase?.rawValue

        return CachedEvent(
            eventCode: eventCode,
            title: title,
            eventDate: date,
            themeImageUrl: themeImageUrl,
            venueName: venueName,
            typicalStartTime: typicalStartTime ?? "18:00",
            typicalEndTime: typicalEndTime ?? "22:00",
            currentPublishedPhase: phaseString,
            sessions: cachedSessions,
            lastSyncTimestamp: Date()
        )
    }
}

// MARK: - SessionResponse → WatchSession

extension SessionResponse {
    func toWatchSession() -> WatchSession? {
        guard let start = startTime,
              let end = endTime,
              let generatedType = sessionType else {
            return nil
        }

        let domainType = mapSessionType(generatedType)
        let watchSpeakers = speakers?.compactMap { $0.toWatchSpeaker() } ?? []

        return WatchSession(
            id: sessionSlug,
            title: title,
            abstract: description,
            sessionType: domainType,
            startTime: start,
            endTime: end,
            speakers: watchSpeakers,
            state: .scheduled,
            actualStartTime: nil,
            overrunMinutes: nil
        )
    }

    func toCachedSession() -> CachedSession {
        let cachedSpeakers = speakers?.compactMap { $0.toCachedSpeaker() } ?? []
        let domainType = sessionType.map { mapSessionType($0) }

        return CachedSession(
            sessionSlug: sessionSlug,
            title: title,
            abstract: description,
            sessionType: domainType,
            startTime: startTime,
            endTime: endTime,
            speakers: cachedSpeakers
        )
    }
}

// MARK: - SessionSpeaker → WatchSpeaker

extension SessionSpeaker {
    func toWatchSpeaker() -> WatchSpeaker {
        let domainRole = mapSpeakerRole(speakerRole)

        return WatchSpeaker(
            id: username,
            firstName: firstName,
            lastName: lastName,
            company: company,
            companyLogoUrl: nil,
            profilePictureUrl: profilePictureUrl,
            bio: bio,
            speakerRole: domainRole,
            arrived: false,
            arrivedConfirmedBy: nil,
            arrivedAt: nil
        )
    }

    func toCachedSpeaker() -> CachedSpeaker {
        let domainRole = mapSpeakerRole(speakerRole)

        return CachedSpeaker(
            username: username,
            firstName: firstName,
            lastName: lastName,
            company: company,
            profilePictureUrl: profilePictureUrl,
            bio: bio,
            speakerRole: domainRole
        )
    }
}

// MARK: - WatchEvent → CachedEvent

extension WatchEvent {
    func toCachedEvent() -> CachedEvent {
        let cachedSessions = sessions.map { $0.toCachedSession() }

        return CachedEvent(
            eventCode: id,
            title: title,
            eventDate: date,
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

extension WatchSession {
    func toCachedSession() -> CachedSession {
        let cachedSpeakers = speakers.map { $0.toCachedSpeaker() }

        return CachedSession(
            sessionSlug: id,
            title: title,
            abstract: abstract,
            sessionType: sessionType,
            startTime: startTime,
            endTime: endTime,
            speakers: cachedSpeakers
        )
    }
}

extension WatchSpeaker {
    func toCachedSpeaker() -> CachedSpeaker {
        return CachedSpeaker(
            username: id,
            firstName: firstName,
            lastName: lastName,
            company: company,
            profilePictureUrl: profilePictureUrl,
            bio: bio,
            speakerRole: speakerRole
        )
    }
}
