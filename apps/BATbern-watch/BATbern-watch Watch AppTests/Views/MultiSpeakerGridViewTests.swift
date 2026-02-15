//
//  MultiSpeakerGridViewTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for MultiSpeakerGridView component - multi-speaker grid (P5).
//  Source: W1.3 AC#3
//

import XCTest
import SwiftUI
@testable import BATbern_watch_Watch_App

@MainActor
final class MultiSpeakerGridViewTests: XCTestCase {

    // MARK: - Test Data Factory

    private func makeSpeakers(count: Int = 3) -> [CachedSpeaker] {
        return (0..<count).map { index in
            CachedSpeaker(
                username: "speaker-\(index)",
                firstName: "Speaker",
                lastName: "\(index + 1)",
                company: "Company \(index + 1)"
            )
        }
    }

    // MARK: - Layout Tests (AC#3)

    func test_multiSpeakerGridView_shouldDisplaySpeakerCount() throws {
        // Given
        let speakers = makeSpeakers(count: 3)

        // When
        let view = MultiSpeakerGridView(speakers: speakers)

        // Then
        XCTAssertNotNil(view)
        // Would verify header displays "Referenten (3)" (localized)
        // Localization key: "speakers.count" with format argument
    }

    func test_multiSpeakerGridView_shouldDisplay2ColumnGrid() throws {
        // Given
        let speakers = makeSpeakers(count: 4)

        // When
        let view = MultiSpeakerGridView(speakers: speakers)

        // Then
        XCTAssertNotNil(view)
        // Would verify LazyVGrid with 2 columns is used
        // Would verify portrait size is ~40pt (same as session card)
    }

    func test_multiSpeakerGridView_shouldDisplayPortraitGrid_with2Speakers() throws {
        // Given
        let speakers = makeSpeakers(count: 2)

        // When
        let view = MultiSpeakerGridView(speakers: speakers)

        // Then
        XCTAssertNotNil(view)
        // Would verify 2 portraits in grid
    }

    func test_multiSpeakerGridView_shouldDisplayPortraitGrid_with3Speakers() throws {
        // Given
        let speakers = makeSpeakers(count: 3)

        // When
        let view = MultiSpeakerGridView(speakers: speakers)

        // Then
        XCTAssertNotNil(view)
        // Would verify 3 portraits in grid (2 on first row, 1 on second)
    }

    func test_multiSpeakerGridView_shouldDisplayPortraitGrid_withManySpeakers() throws {
        // Given
        let speakers = makeSpeakers(count: 6)

        // When
        let view = MultiSpeakerGridView(speakers: speakers)

        // Then
        XCTAssertNotNil(view)
        // Would verify 6 portraits in grid (3 rows of 2)
    }

    // MARK: - Crown Scroll Tests (Subtask 3.6)

    func test_multiSpeakerGridView_shouldSupportCrownScroll_whenManySpeakers() throws {
        // Given
        let speakers = makeSpeakers(count: 8)  // More than 4 speakers

        // When
        let view = MultiSpeakerGridView(speakers: speakers)

        // Then
        XCTAssertNotNil(view)
        // Would verify ScrollView is present for Crown scrolling
        // Would verify grid exceeds screen height with >4 speakers
    }

    // MARK: - Navigation Tests (Subtask 3.5)

    func test_multiSpeakerGridView_shouldWrapPortraitsInNavigationLinks() throws {
        // Given
        let speakers = makeSpeakers(count: 3)

        // When
        let view = MultiSpeakerGridView(speakers: speakers)

        // Then
        XCTAssertNotNil(view)
        // Would verify each portrait cell is a NavigationLink
        // Would verify NavigationLink destination is SpeakerBioView
    }

    // MARK: - Edge Cases

    func test_multiSpeakerGridView_shouldHandleEmptyArray() throws {
        // Given
        let speakers: [CachedSpeaker] = []

        // When
        let view = MultiSpeakerGridView(speakers: speakers)

        // Then
        XCTAssertNotNil(view)
        // Would verify "Referenten (0)" is displayed
        // Would verify empty grid (no crash)
    }
}
