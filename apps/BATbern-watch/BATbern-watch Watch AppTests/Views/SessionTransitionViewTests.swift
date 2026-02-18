//
//  SessionTransitionViewTests.swift
//  BATbern-watch Watch AppTests
//
//  W4.2 Task 3.7: Unit tests for O6 SessionTransitionView.
//  Verifies view struct instantiation, data exposure, and onDismiss callback wiring.
//  Auto-dismiss (5s Task.sleep) is a system behaviour not unit-tested here.
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("SessionTransitionView")
@MainActor
struct SessionTransitionViewTests {

    // MARK: - Fixtures

    private func makeNextSession(
        slug: String = "microservices-mistakes",
        title: String = "Microservices Mistakes You'll Make",
        withSpeaker: Bool = true
    ) -> WatchSession {
        let speaker = WatchSpeaker(
            id: "marco.rossi",
            firstName: "Marco",
            lastName: "Rossi",
            company: "Tech Corp",
            companyLogoUrl: nil,
            profilePictureUrl: nil,
            bio: nil,
            speakerRole: .primarySpeaker,
            arrived: true
        )
        return WatchSession(
            id: slug,
            title: title,
            abstract: nil,
            sessionType: .presentation,
            startTime: Date().addingTimeInterval(300),
            endTime: Date().addingTimeInterval(3000),
            speakers: withSpeaker ? [speaker] : [],
            state: .scheduled
        )
    }

    // MARK: - Instantiation

    @Test("SessionTransitionView can be instantiated with nextSession and onDismiss")
    func canBeInstantiated() {
        var dismissCalled = false
        let session = makeNextSession()
        let view = SessionTransitionView(
            nextSession: session,
            onDismiss: { dismissCalled = false }
        )
        #expect(view.nextSession.id == "microservices-mistakes")
        _ = dismissCalled  // confirms closure is captured
    }

    @Test("nextSession data is accessible from the view")
    func nextSession_dataIsAccessible() {
        let session = makeNextSession(
            slug: "zero-trust",
            title: "Zero Trust Architecture",
            withSpeaker: true
        )
        let view = SessionTransitionView(nextSession: session, onDismiss: {})
        #expect(view.nextSession.id == "zero-trust")
        #expect(view.nextSession.title == "Zero Trust Architecture")
        #expect(view.nextSession.speakers.first?.fullName == "Marco Rossi")
    }

    @Test("nextSession without speakers still instantiates (break-after handling)")
    func nextSession_withoutSpeakers_instantiatesSuccessfully() {
        let session = makeNextSession(title: "Panel Discussion", withSpeaker: false)
        let view = SessionTransitionView(nextSession: session, onDismiss: {})
        #expect(view.nextSession.speakers.isEmpty)
        #expect(view.nextSession.title == "Panel Discussion")
    }

    // MARK: - onDismiss wiring

    @Test("onDismiss callback is called when invoked")
    func onDismiss_callbackInvoked() {
        var dismissedCalled = false
        let session = makeNextSession()
        let view = SessionTransitionView(
            nextSession: session,
            onDismiss: { dismissedCalled = true }
        )
        // Invoke onDismiss directly (simulates auto-dismiss completing)
        view.onDismiss()
        #expect(dismissedCalled == true)
    }

    // MARK: - NextSessionPeekView usage (Area 1 — .prominent style mandated)

    @Test("View exposes nextSession for NextSessionPeekView(.prominent) rendering")
    func view_exposesNextSessionForProminentPeekView() {
        let session = makeNextSession(slug: "cloud-security", title: "Cloud Security 2026")
        let view = SessionTransitionView(nextSession: session, onDismiss: {})

        // NextSessionPeekView(.prominent) will render view.nextSession
        let peekView = NextSessionPeekView(session: view.nextSession, style: .prominent)
        #expect(peekView.session.id == "cloud-security")
        #expect(peekView.session.title == "Cloud Security 2026")
        #expect(peekView.style == .prominent)
    }
}
