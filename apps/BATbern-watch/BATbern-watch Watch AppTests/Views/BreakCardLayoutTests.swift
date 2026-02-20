//
//  BreakCardLayoutTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for BreakCardLayout (W4.4 Task 0):
//  - breakIcon(for:) static mapping
//  - CachedSession.isBreak computed property
//  - WatchSession.isBreak includes .networking
//  Source: docs/watch-app/ux-design-specification.md#Session-Card-Pages
//

import Testing
@testable import BATbern_watch_Watch_App

// MARK: - BreakCardLayout.breakIcon Tests

@Suite("BreakCardLayout.breakIcon")
struct BreakCardLayoutBreakIconTests {

    @Test("breakTime maps to cup.and.saucer.fill")
    func breakTime_mapsToCupIcon() {
        #expect(BreakCardLayout.breakIcon(for: .breakTime) == "cup.and.saucer.fill")
    }

    @Test("lunch maps to cup.and.saucer.fill")
    func lunch_mapsToCupIcon() {
        #expect(BreakCardLayout.breakIcon(for: .lunch) == "cup.and.saucer.fill")
    }

    @Test("networking maps to person.2.fill")
    func networking_mapsToPersonIcon() {
        #expect(BreakCardLayout.breakIcon(for: .networking) == "person.2.fill")
    }

    @Test("nil maps to questionmark.circle")
    func nil_mapsToFallbackIcon() {
        #expect(BreakCardLayout.breakIcon(for: nil) == "questionmark.circle")
    }

    @Test("presentation maps to questionmark.circle")
    func presentation_mapsToFallbackIcon() {
        #expect(BreakCardLayout.breakIcon(for: .presentation) == "questionmark.circle")
    }

    @Test("keynote maps to questionmark.circle")
    func keynote_mapsToFallbackIcon() {
        #expect(BreakCardLayout.breakIcon(for: .keynote) == "questionmark.circle")
    }

    @Test("moderation maps to questionmark.circle")
    func moderation_mapsToFallbackIcon() {
        #expect(BreakCardLayout.breakIcon(for: .moderation) == "questionmark.circle")
    }
}

// MARK: - CachedSession.isBreak Tests

@Suite("CachedSession.isBreak")
struct CachedSessionIsBreakTests {

    @Test("breakTime session is a break")
    func breakTime_isBreak() {
        let session = CachedSession(
            sessionSlug: "break-1",
            title: "Coffee Break",
            sessionType: .breakTime
        )
        #expect(session.isBreak == true)
    }

    @Test("lunch session is a break")
    func lunch_isBreak() {
        let session = CachedSession(
            sessionSlug: "lunch-1",
            title: "Lunch",
            sessionType: .lunch
        )
        #expect(session.isBreak == true)
    }

    @Test("networking session is a break")
    func networking_isBreak() {
        let session = CachedSession(
            sessionSlug: "networking-1",
            title: "Apéro & Networking",
            sessionType: .networking
        )
        #expect(session.isBreak == true)
    }

    @Test("presentation session is not a break")
    func presentation_isNotBreak() {
        let session = CachedSession(
            sessionSlug: "talk-1",
            title: "Cloud-Native Pitfalls",
            sessionType: .presentation
        )
        #expect(session.isBreak == false)
    }

    @Test("keynote session is not a break")
    func keynote_isNotBreak() {
        let session = CachedSession(
            sessionSlug: "keynote",
            title: "Opening Keynote",
            sessionType: .keynote
        )
        #expect(session.isBreak == false)
    }

    @Test("nil sessionType is not a break")
    func nilType_isNotBreak() {
        let session = CachedSession(
            sessionSlug: "unknown",
            title: "Unknown Session",
            sessionType: nil
        )
        #expect(session.isBreak == false)
    }
}

// MARK: - WatchSession.isBreak Tests

@Suite("WatchSession.isBreak")
struct WatchSessionIsBreakTests {

    @Test("breakTime is a break")
    func breakTime_isBreak() {
        let session = TestData.fixedSession(
            slug: "break-1",
            title: "Coffee Break",
            start: Date(),
            end: Date().addingTimeInterval(20 * 60),
            type: .breakTime,
            speakers: []
        )
        #expect(session.isBreak == true)
    }

    @Test("lunch is a break")
    func lunch_isBreak() {
        let session = TestData.fixedSession(
            slug: "lunch-1",
            title: "Lunch",
            start: Date(),
            end: Date().addingTimeInterval(60 * 60),
            type: .lunch,
            speakers: []
        )
        #expect(session.isBreak == true)
    }

    @Test("networking is a break — W4.4 fix")
    func networking_isBreak() {
        // W4.4: networking sessions must trigger BreakGongView, so isBreak must be true
        let session = TestData.fixedSession(
            slug: "networking-1",
            title: "Apéro & Networking",
            start: Date(),
            end: Date().addingTimeInterval(60 * 60),
            type: .networking,
            speakers: []
        )
        #expect(session.isBreak == true)
    }

    @Test("presentation is not a break")
    func presentation_isNotBreak() {
        let session = TestData.fixedSession(
            slug: "talk-1",
            title: "Cloud-Native Pitfalls",
            start: Date(),
            end: Date().addingTimeInterval(45 * 60)
        )
        #expect(session.isBreak == false)
    }
}
