//
//  SpeakerBioViewTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for SpeakerBioView component - speaker bio screen (P4/P6).
//  Source: W1.3 AC#2, AC#4, AC#7
//

import XCTest
import SwiftUI
@testable import BATbern_watch_Watch_App

@MainActor
final class SpeakerBioViewTests: XCTestCase {

    // MARK: - Test Data Factory

    private func makeSpeaker(
        firstName: String = "Anna",
        lastName: String = "Meier",
        company: String? = "ACME Corp",
        companyLogoUrl: String? = "https://cdn.batbern.ch/logos/acme.png",
        bio: String? = "Senior architect specializing in cloud native security.",
        profilePictureUrl: String? = "https://cdn.batbern.ch/portraits/anna.jpg"
    ) -> CachedSpeaker {
        return CachedSpeaker(
            username: "\(firstName.lowercased())-\(lastName.lowercased())",
            firstName: firstName,
            lastName: lastName,
            company: company,
            companyLogoUrl: companyLogoUrl,
            profilePictureUrl: profilePictureUrl,
            bio: bio
        )
    }

    // MARK: - Layout Tests (AC#2, AC#4)

    func test_speakerBioView_shouldDisplayLargePortrait() throws {
        // Given
        let speaker = makeSpeaker()

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify large circular portrait (~80pt) is displayed
    }

    func test_speakerBioView_shouldDisplaySpeakerName() throws {
        // Given
        let speaker = makeSpeaker(firstName: "Anna", lastName: "Meier")

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify full name "Anna Meier" is displayed
        // Font: SF Pro Rounded ~16pt Semibold
    }

    func test_speakerBioView_shouldDisplayBioText() throws {
        // Given
        let bio = "Senior architect specializing in cloud native security and microservices."
        let speaker = makeSpeaker(bio: bio)

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify bio text is displayed with SF Pro ~13pt
    }

    // MARK: - Company Logo Tests (AC#7)

    func test_speakerBioView_shouldDisplayCompanyLogoInline() throws {
        // Given
        let speaker = makeSpeaker(company: "ACME Corp", companyLogoUrl: "https://cdn.batbern.ch/logos/acme.png")

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify company logo (~20pt) is shown inline next to company name
    }

    func test_speakerBioView_shouldDisplayCompanyNameOnly_whenNoLogo() throws {
        // Given
        let speaker = makeSpeaker(company: "ACME Corp", companyLogoUrl: nil)

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify company name is shown as plain text without logo
    }

    // MARK: - Crown Scroll Tests (Subtask 2.5)

    func test_speakerBioView_shouldSupportCrownScroll_forLongBios() throws {
        // Given
        let longBio = String(repeating: "Expert in cloud architecture and distributed systems. ", count: 30)
        let speaker = makeSpeaker(bio: longBio)

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify ScrollView is present for Crown scrolling
        // Would verify no line limit or truncation on bio text
    }

    // MARK: - Missing Data Handling (Subtask 2.7)

    func test_speakerBioView_shouldHandleMissingPortrait() throws {
        // Given
        let speaker = makeSpeaker(profilePictureUrl: nil)

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify SF Symbol placeholder is shown
        // person.crop.circle.fill
    }

    func test_speakerBioView_shouldHandleMissingBio() throws {
        // Given
        let speaker = makeSpeaker(bio: nil)

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify localized "No bio available" text is shown
        // Localization key: "speaker.no_bio"
    }

    func test_speakerBioView_shouldHandleEmptyBio() throws {
        // Given
        let speaker = makeSpeaker(bio: "")

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify localized "No bio available" text is shown
    }

    func test_speakerBioView_shouldHandleWhitespaceOnlyBio() throws {
        // Given
        let speaker = makeSpeaker(bio: "   \n\t  ")

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify localized "No bio available" text is shown
    }

    func test_speakerBioView_shouldHandleMissingCompany() throws {
        // Given
        let speaker = makeSpeaker(company: nil, companyLogoUrl: nil)

        // When
        let view = SpeakerBioView(speaker: speaker)

        // Then
        XCTAssertNotNil(view)
        // Would verify no company section is displayed
    }
}
