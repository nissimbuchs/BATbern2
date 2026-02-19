//
//  CachedSession+WatchSession.swift
//  BATbern-watch Watch App
//
//  Converts SwiftData CachedSession/CachedSpeaker to domain WatchSession/WatchSpeaker.
//  W3.1: LiveCountdownViewModel works with WatchSession (domain) not CachedSession (SwiftData).
//  Source: docs/watch-app/architecture.md#Data-Architecture
//

import Foundation

extension CachedSession {
    /// Convert CachedSession (SwiftData) to WatchSession (domain model) for SessionTimerEngine.
    /// Returns nil if startTime or endTime are missing.
    func toWatchSession() -> WatchSession? {
        guard let start = startTime, let end = endTime else { return nil }
        return WatchSession(
            id: sessionSlug,
            title: title,
            abstract: abstract,
            sessionType: sessionType ?? .presentation,
            startTime: start,
            endTime: end,
            speakers: speakers.map { $0.toWatchSpeaker() },
            state: .active,
            actualStartTime: actualStartTime,
            overrunMinutes: overrunMinutes,
            completedByUsername: completedByUsername
        )
    }
}

extension CachedSpeaker {
    /// Convert CachedSpeaker (SwiftData) to WatchSpeaker (domain model).
    func toWatchSpeaker() -> WatchSpeaker {
        WatchSpeaker(
            id: username,
            firstName: firstName,
            lastName: lastName,
            company: company,
            companyLogoUrl: companyLogoUrl,
            profilePictureUrl: profilePictureUrl,
            bio: bio,
            speakerRole: speakerRole,
            arrived: arrived,
            arrivedConfirmedBy: arrivedConfirmedBy,
            arrivedAt: arrivedAt
        )
    }
}
