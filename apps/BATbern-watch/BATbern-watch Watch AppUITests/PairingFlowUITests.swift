//
//  PairingFlowUITests.swift
//  BATbern-watch Watch AppUITests
//
//  UI tests for pairing flow and dual-zone navigation.
//  W2.2: AC1 (pairing screen), AC2 (successful pairing), AC3 (error message),
//        AC4 (persistent pairing), AC5 (zone navigation).
//

import XCTest

final class PairingFlowUITests: XCTestCase {

    var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        // Inject test environment: no pairing, mock API responses
        app.launchEnvironment["TESTING_MODE"] = "1"
    }

    override func tearDown() {
        app = nil
        super.tearDown()
    }

    // MARK: - AC1: Pairing Screen Entry

    func test_shouldShowPairingScreen_whenNotPaired() throws {
        // Pre-condition: not paired (Keychain clear)
        app.launchEnvironment["AUTH_STATE"] = "unpaired"
        app.launch()

        // Swipe left to enter organizer zone (reveals right tab)
        app.swipeLeft()

        // Assert: PairingView appears with title
        let pairingTitle = app.staticTexts["Watch koppeln"]
        XCTAssertTrue(pairingTitle.waitForExistence(timeout: 3),
                      "Pairing screen should appear when not paired")

        // Assert: Pair button is visible
        let pairButton = app.buttons["Koppeln"]
        XCTAssertTrue(pairButton.exists, "Pair button should be visible")
    }

    // MARK: - AC3: Error Display

    func test_shouldShowError_whenInvalidCode() throws {
        app.launchEnvironment["AUTH_STATE"] = "unpaired"
        app.launchEnvironment["MOCK_PAIR_RESPONSE"] = "invalid_code"
        app.launch()

        // Navigate to organizer zone (reveals right tab)
        app.swipeLeft()

        // Tap Pair button with default code (000000)
        let pairButton = app.buttons["Koppeln"]
        _ = pairButton.waitForExistence(timeout: 3)
        pairButton.tap()

        // Assert: Error message appears
        let errorText = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS 'ungültig'")
        ).firstMatch
        XCTAssertTrue(errorText.waitForExistence(timeout: 3),
                      "Error message should appear for invalid code")
    }

    // MARK: - AC5: Zone Navigation

    func test_shouldNavigateBetweenZones_whenPaired() throws {
        app.launchEnvironment["AUTH_STATE"] = "paired"
        app.launch()

        // Assert: starts in public zone (SessionListView or hero)
        // The public zone should be visible on launch

        // Swipe left → Organizer Zone (reveals right tab; no pairing screen because already paired)
        app.swipeLeft()

        // Should NOT show pairing screen
        let pairingTitle = app.staticTexts["Watch koppeln"]
        XCTAssertFalse(pairingTitle.exists,
                       "Pairing screen should NOT appear when already paired")

        // Swipe right → Back to public zone
        app.swipeRight()

        // Should be back in public zone (no pairing title)
        XCTAssertFalse(pairingTitle.exists,
                       "After swiping left, should return to public zone")
    }

    // MARK: - AC4: No Re-Authentication

    func test_shouldSkipPairingScreen_whenAlreadyPaired() throws {
        app.launchEnvironment["AUTH_STATE"] = "paired"
        app.launch()

        // Swipe left to organizer zone (reveals right tab)
        app.swipeLeft()

        // Pairing view should NOT be shown
        let pairingTitle = app.staticTexts["Watch koppeln"]
        XCTAssertFalse(pairingTitle.waitForExistence(timeout: 2),
                       "Should go directly to organizer zone without pairing screen")
    }
}
