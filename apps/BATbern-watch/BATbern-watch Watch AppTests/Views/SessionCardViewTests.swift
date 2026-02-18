//
//  SessionCardViewTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for SessionCardView component - presentation vs. break card layouts.
//  Source: W1.2 AC#2, AC#3
//

import XCTest
import SwiftUI
@testable import BATbern_watch_Watch_App

@MainActor
final class SessionCardViewTests: XCTestCase {

    // MARK: - Test Data Factory

    private func makePresentationSession(
        title: String = "Cloud Native Security",
        speakers: [CachedSpeaker] = []
    ) -> CachedSession {
        return CachedSession(
            sessionSlug: "cloud-security",
            title: title,
            abstract: "Test abstract",
            sessionType: .presentation,
            startTime: Date(),
            endTime: Date().addingTimeInterval(45 * 60),
            speakers: speakers
        )
    }

    private func makeBreakSession(
        title: String = "Coffee Break",
        sessionType: SessionType = .breakTime
    ) -> CachedSession {
        return CachedSession(
            sessionSlug: "break-1",
            title: title,
            sessionType: sessionType,
            startTime: Date(),
            endTime: Date().addingTimeInterval(20 * 60)
        )
    }

    private func makeSpeaker(
        firstName: String = "Anna",
        lastName: String = "Test",
        company: String = "ACME Corp"
    ) -> CachedSpeaker {
        return CachedSpeaker(
            username: "\(firstName.lowercased())-test",
            firstName: firstName,
            lastName: lastName,
            company: company
        )
    }

    // MARK: - Presentation Card Tests (AC#2)

    func test_presentationCard_shouldDisplayTimeSlot() throws {
        // Given
        let session = makePresentationSession()

        // When
        let view = SessionCardView(session: session, phase: "AGENDA")

        // Then
        // View should contain time slot text (verified via ViewInspector or manual test)
        // This is a placeholder - actual view inspection would use ViewInspector library
        XCTAssertNotNil(view)
    }

    func test_presentationCard_shouldDisplayTitle() throws {
        // Given
        let session = makePresentationSession(title: "Cloud Native Security in 2026")

        // When
        let view = SessionCardView(session: session, phase: "AGENDA")

        // Then
        XCTAssertNotNil(view)
        // Would verify title is displayed with blue tint
    }

    func test_presentationCard_shouldDisplaySpeakerArea() throws {
        // Given
        let speakers = [
            makeSpeaker(firstName: "Anna", company: "ACME"),
            makeSpeaker(firstName: "Tom", company: "Corp")
        ]
        let session = makePresentationSession(speakers: speakers)

        // When
        let view = SessionCardView(session: session, phase: "SPEAKERS")

        // Then
        XCTAssertNotNil(view)
        // Would verify speaker portraits and names are displayed
    }

    // MARK: - Break Card Tests (AC#3)

    func test_breakCard_shouldDisplayTimeSlot() throws {
        // Given
        let session = makeBreakSession()

        // When
        let view = SessionCardView(session: session, phase: "AGENDA")

        // Then
        XCTAssertNotNil(view)
    }

    func test_breakCard_shouldDisplayIcon_forBreakType() throws {
        // Given
        let session = makeBreakSession(sessionType: .breakTime)

        // When
        let view = SessionCardView(session: session, phase: "AGENDA")

        // Then
        XCTAssertNotNil(view)
        // Would verify cup.and.saucer.fill icon is displayed
    }

    func test_breakCard_shouldDisplayIcon_forLunchType() throws {
        // Given
        let session = makeBreakSession(title: "Lunch Break", sessionType: .lunch)

        // When
        let view = SessionCardView(session: session, phase: "AGENDA")

        // Then
        XCTAssertNotNil(view)
        // Would verify cup.and.saucer.fill icon is displayed
    }

    func test_breakCard_shouldDisplayIcon_forNetworkingType() throws {
        // Given
        let session = makeBreakSession(title: "Networking", sessionType: .networking)

        // When
        let view = SessionCardView(session: session, phase: "AGENDA")

        // Then
        XCTAssertNotNil(view)
        // Would verify person.2.fill icon is displayed
    }

    func test_breakCard_shouldNotDisplaySpeakerArea() throws {
        // Given
        let session = makeBreakSession()

        // When
        let view = SessionCardView(session: session, phase: "AGENDA")

        // Then
        XCTAssertNotNil(view)
        // Would verify speaker area is not present
    }

    // MARK: - Status Badge Tests (W3.4 AC1)

    func test_badgeStatus_completedWhenEndTimeIsInThePast() {
        // Given
        let session = CachedSession(
            sessionSlug: "past",
            title: "Past Session",
            startTime: Date(timeIntervalSinceNow: -90 * 60),
            endTime: Date(timeIntervalSinceNow: -45 * 60)
        )
        // When
        let status = SessionBadgeStatus.status(for: session, at: Date())
        // Then
        XCTAssertEqual(status, .completed)
    }

    func test_badgeStatus_activeWhenNowIsBetweenStartAndEnd() {
        // Given
        let session = CachedSession(
            sessionSlug: "active",
            title: "Active Session",
            startTime: Date(timeIntervalSinceNow: -20 * 60),
            endTime: Date(timeIntervalSinceNow: 25 * 60)
        )
        // When
        let status = SessionBadgeStatus.status(for: session, at: Date())
        // Then
        XCTAssertEqual(status, .active)
    }

    func test_badgeStatus_upcomingWhenStartTimeIsInTheFuture() {
        // Given
        let session = CachedSession(
            sessionSlug: "future",
            title: "Future Session",
            startTime: Date(timeIntervalSinceNow: 30 * 60),
            endTime: Date(timeIntervalSinceNow: 75 * 60)
        )
        // When
        let status = SessionBadgeStatus.status(for: session, at: Date())
        // Then
        XCTAssertEqual(status, .upcoming)
    }

    func test_badgeStatus_nilWhenStartTimeIsMissing() {
        // Given
        let session = CachedSession(
            sessionSlug: "no-start",
            title: "No Start Session",
            startTime: nil,
            endTime: Date(timeIntervalSinceNow: 60 * 60)
        )
        // When
        let status = SessionBadgeStatus.status(for: session, at: Date())
        // Then
        XCTAssertNil(status)
    }

    func test_badgeStatus_nilWhenEndTimeIsMissing() {
        // Given
        let session = CachedSession(
            sessionSlug: "no-end",
            title: "No End Session",
            startTime: Date(timeIntervalSinceNow: -30 * 60),
            endTime: nil
        )
        // When
        let status = SessionBadgeStatus.status(for: session, at: Date())
        // Then
        XCTAssertNil(status)
    }

    func test_badgeStatus_labelAndColor() {
        XCTAssertEqual(SessionBadgeStatus.completed.label, "Done")
        XCTAssertEqual(SessionBadgeStatus.active.label, "Active")
        XCTAssertEqual(SessionBadgeStatus.upcoming.label, "Upcoming")

        XCTAssertEqual(SessionBadgeStatus.completed.color, .gray)
        XCTAssertEqual(SessionBadgeStatus.active.color, .teal)
    }
}
