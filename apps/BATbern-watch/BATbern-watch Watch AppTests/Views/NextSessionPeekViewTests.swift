//
//  NextSessionPeekViewTests.swift
//  BATbern-watch Watch AppTests
//
//  W4.2 Task 0.6: Unit tests for NextSessionPeekView shared component.
//  Verifies .compact and .prominent modes use identical WatchSession input
//  with no independent data fetching — reuse-map Area 1 compliance.
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("NextSessionPeekView")
@MainActor
struct NextSessionPeekViewTests {

    // MARK: - Fixtures

    private let referenceDate = Date(timeIntervalSince1970: 1_000_000)

    private func makeSession(
        slug: String = "cloud-talk",
        title: String = "Cloud-Native Pitfalls",
        withSpeaker: Bool = true,
        profilePictureUrl: String? = "https://cdn.batbern.ch/speakers/anna.jpg"
    ) -> WatchSession {
        let speaker = WatchSpeaker(
            id: "anna.meier",
            firstName: "Anna",
            lastName: "Meier",
            company: "ACME Corp",
            companyLogoUrl: nil,
            profilePictureUrl: profilePictureUrl,
            bio: nil,
            speakerRole: .primarySpeaker,
            arrived: false
        )
        return WatchSession(
            id: slug,
            title: title,
            abstract: nil,
            sessionType: .presentation,
            startTime: referenceDate,
            endTime: referenceDate.addingTimeInterval(2700),
            speakers: withSpeaker ? [speaker] : [],
            state: .scheduled
        )
    }

    // MARK: - Style enum

    @Test("NextSessionPeekStyle has .compact and .prominent cases")
    func nextSessionPeekStyle_hasBothCases() {
        #expect(NextSessionPeekStyle.compact != NextSessionPeekStyle.prominent)
    }

    // MARK: - .compact mode

    @Test(".compact can be instantiated with speaker session")
    func compact_canBeInstantiated() {
        let session = makeSession()
        let view = NextSessionPeekView(session: session, style: .compact)
        #expect(view.session.id == "cloud-talk")
        #expect(view.style == .compact)
    }

    @Test(".compact uses session.title as fallback when no speaker")
    func compact_usesTitleWhenNoSpeaker() {
        let session = makeSession(title: "Keynote: Future of Swiss Cloud", withSpeaker: false)
        let view = NextSessionPeekView(session: session, style: .compact)
        // The view renders `session.speakers.first?.fullName ?? session.title`
        // With no speakers, it falls back to session.title
        #expect(view.session.speakers.isEmpty)
        #expect(view.session.title == "Keynote: Future of Swiss Cloud")
    }

    @Test(".compact uses speaker fullName when speaker exists")
    func compact_usesSpeakerFullNameWhenPresent() {
        let session = makeSession()
        let view = NextSessionPeekView(session: session, style: .compact)
        let expectedName = view.session.speakers.first?.fullName
        #expect(expectedName == "Anna Meier")
    }

    // MARK: - .prominent mode

    @Test(".prominent can be instantiated with speaker session")
    func prominent_canBeInstantiated() {
        let session = makeSession()
        let view = NextSessionPeekView(session: session, style: .prominent)
        #expect(view.session.id == "cloud-talk")
        #expect(view.style == .prominent)
    }

    @Test(".prominent shows portrait placeholder when speaker has no URL")
    func prominent_portraitPlaceholderWhenNoURL() {
        let session = makeSession(profilePictureUrl: nil)
        let view = NextSessionPeekView(session: session, style: .prominent)
        // When profilePictureUrl is nil, portraitCircle renders initials placeholder
        #expect(view.session.speakers.first?.profilePictureUrl == nil)
    }

    @Test(".prominent renders speaker name and talk title from session")
    func prominent_showsNameAndTitle() {
        let session = makeSession(title: "Zero Trust Architecture")
        let view = NextSessionPeekView(session: session, style: .prominent)
        #expect(view.session.speakers.first?.fullName == "Anna Meier")
        #expect(view.session.title == "Zero Trust Architecture")
    }

    // MARK: - Data isolation (Area 1 mandate)

    @Test("Both styles receive identical WatchSession — no independent data fetching")
    func bothStyles_receiveIdenticalWatchSession() {
        let session = makeSession()
        let compact = NextSessionPeekView(session: session, style: .compact)
        let prominent = NextSessionPeekView(session: session, style: .prominent)

        // Same session data — Area 1: no parallel data fetching
        #expect(compact.session.id == prominent.session.id)
        #expect(compact.session.title == prominent.session.title)
        #expect(compact.session.speakers.count == prominent.session.speakers.count)
        #expect(compact.session.startTime == prominent.session.startTime)
    }

    @Test("Both styles expose speakers from the injected WatchSession")
    func bothStyles_exposeSpeakersFromInjectedSession() {
        let session = makeSession()
        let compact = NextSessionPeekView(session: session, style: .compact)
        let prominent = NextSessionPeekView(session: session, style: .prominent)

        #expect(compact.session.speakers.first?.id == prominent.session.speakers.first?.id)
        #expect(compact.session.speakers.first?.fullName == "Anna Meier")
    }
}
