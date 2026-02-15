import Foundation
@testable import BATbern_watch_Watch_App

/// Factories for creating test data with sensible defaults and builder-style overrides.
/// All defaults use realistic BATbern data (Bern architecture community event).
enum TestData {

    // MARK: - Events

    static func event(
        code: String = "bat-2026-spring",
        title: String = "BATbern Spring 2026",
        date: Date = Date(),
        themeImageUrl: String? = "https://cdn.batbern.ch/themes/spring-2026.jpg",
        venueName: String = "Kornhausforum Bern",
        sessions: [WatchSession]? = nil
    ) -> WatchEvent {
        WatchEvent(
            id: code,
            title: title,
            date: date,
            themeImageUrl: themeImageUrl,
            venueName: venueName,
            sessions: sessions ?? defaultSessions()
        )
    }

    /// Event with a full realistic evening schedule.
    static func eveningEvent(baseTime: Date = Date()) -> WatchEvent {
        event(sessions: [
            session(slug: "keynote", title: "Opening Keynote", startTime: baseTime, durationMinutes: 45),
            session(slug: "talk-1", title: "Cloud-Native Pitfalls", startTime: baseTime.addingTimeInterval(50 * 60), durationMinutes: 45),
            session(slug: "break-1", title: "Coffee Break", type: .breakTime, startTime: baseTime.addingTimeInterval(100 * 60), durationMinutes: 20, speakers: []),
            session(slug: "talk-2", title: "Zero Trust Architecture", startTime: baseTime.addingTimeInterval(125 * 60), durationMinutes: 45),
            session(slug: "networking", title: "Apéro & Networking", type: .networking, startTime: baseTime.addingTimeInterval(175 * 60), durationMinutes: 60, speakers: []),
        ])
    }

    // MARK: - Sessions

    static func session(
        slug: String = "talk-1",
        title: String = "Cloud-Native Pitfalls",
        abstract: String? = "A deep dive into microservices anti-patterns and how to avoid them.",
        type: SessionType = .presentation,
        startTime: Date? = nil,
        endTime: Date? = nil,
        durationMinutes: Double = 45,
        speakers: [WatchSpeaker]? = nil,
        state: SessionState = .scheduled,
        overrunMinutes: Int? = nil
    ) -> WatchSession {
        let start = startTime ?? Date()
        let end = endTime ?? start.addingTimeInterval(durationMinutes * 60)

        return WatchSession(
            id: slug,
            title: title,
            abstract: abstract,
            sessionType: type,
            startTime: start,
            endTime: end,
            speakers: speakers ?? [speaker()],
            state: state,
            actualStartTime: state == .active ? start : nil,
            overrunMinutes: overrunMinutes
        )
    }

    /// Session with exact start/end dates (not relative to now). Ideal for timer tests.
    static func fixedSession(
        slug: String = "talk-1",
        title: String = "Cloud-Native Pitfalls",
        start: Date,
        end: Date,
        type: SessionType = .presentation,
        speakers: [WatchSpeaker]? = nil,
        state: SessionState = .scheduled
    ) -> WatchSession {
        WatchSession(
            id: slug,
            title: title,
            abstract: "Test abstract",
            sessionType: type,
            startTime: start,
            endTime: end,
            speakers: speakers ?? [speaker()],
            state: state,
            actualStartTime: state == .active ? start : nil,
            overrunMinutes: nil
        )
    }

    // MARK: - Speakers

    static func speaker(
        username: String = "anna.meier",
        firstName: String = "Anna",
        lastName: String = "Meier",
        company: String? = "ACME Corp",
        companyLogoUrl: String? = "https://cdn.batbern.ch/logos/acme.png",
        profilePictureUrl: String? = "https://cdn.batbern.ch/speakers/anna-meier.jpg",
        bio: String? = "Cloud architecture expert with 15 years experience.",
        role: SpeakerRole = .keynoteSpeaker,
        arrived: Bool = false
    ) -> WatchSpeaker {
        WatchSpeaker(
            id: username,
            firstName: firstName,
            lastName: lastName,
            company: company,
            companyLogoUrl: companyLogoUrl,
            profilePictureUrl: profilePictureUrl,
            bio: bio,
            speakerRole: role,
            arrived: arrived,
            arrivedConfirmedBy: arrived ? "nissim" : nil,
            arrivedAt: arrived ? Date() : nil
        )
    }

    /// Generate multiple distinct speakers.
    static func speakers(count: Int = 3) -> [WatchSpeaker] {
        let names: [(String, String, String)] = [
            ("Anna", "Meier", "ACME Corp"),
            ("Thomas", "Keller", "SwissCloud AG"),
            ("Sarah", "Brunner", "TechBern GmbH"),
            ("Marco", "Weber", "ArchStudio"),
            ("Lisa", "Mueller", "DevOps Hub"),
        ]

        return (0..<min(count, names.count)).map { i in
            let (first, last, company) = names[i]
            return speaker(
                username: "\(first.lowercased()).\(last.lowercased())",
                firstName: first,
                lastName: last,
                company: company,
                arrived: false
            )
        }
    }

    // MARK: - State Messages

    static func stateMessage(
        type: EventStateMessageType = .sessionEnded,
        sessionSlug: String? = "talk-1",
        initiatedBy: String? = "marco.weber",
        timestamp: Date = Date()
    ) -> EventStateMessage {
        EventStateMessage(
            type: type,
            sessionSlug: sessionSlug,
            initiatedBy: initiatedBy,
            timestamp: timestamp
        )
    }

    // MARK: - Auth

    static func pairingResult(
        token: String = "pairing-token-abc123",
        username: String = "nissim",
        firstName: String = "Nissim"
    ) -> PairingResult {
        PairingResult(
            pairingToken: token,
            organizerUsername: username,
            organizerFirstName: firstName
        )
    }

    static func authResult(
        accessToken: String = "jwt-access-token-xyz",
        expiresIn: TimeInterval = 3600
    ) -> AuthResult {
        AuthResult(accessToken: accessToken, expiresIn: expiresIn)
    }

    // MARK: - Private

    private static func defaultSessions() -> [WatchSession] {
        let base = Date()
        return [
            session(slug: "keynote", title: "Opening Keynote", startTime: base, durationMinutes: 45),
            session(slug: "talk-1", title: "Cloud-Native Pitfalls", startTime: base.addingTimeInterval(50 * 60), durationMinutes: 45),
            session(slug: "break-1", title: "Coffee Break", type: .breakTime, startTime: base.addingTimeInterval(100 * 60), durationMinutes: 20, speakers: []),
            session(slug: "talk-2", title: "Zero Trust Architecture", startTime: base.addingTimeInterval(125 * 60), durationMinutes: 45),
        ]
    }
}
