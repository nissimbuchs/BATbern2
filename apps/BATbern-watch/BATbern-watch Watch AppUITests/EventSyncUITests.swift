//
//  EventSyncUITests.swift
//  BATbern-watch Watch AppUITests
//
//  UI tests for event sync flow in the organizer zone.
//  W2.3: AC#2 (loading view), AC#4 (no active event), AC#5 (event preview >1h away),
//        W2.2/W2.3 integration: shows SpeakerArrivalView when <1h before event.
//

import XCTest

final class EventSyncUITests: XCTestCase {

    var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        // Inject test environment flags
        app.launchEnvironment["TESTING_MODE"] = "1"
        app.launchEnvironment["AUTH_STATE"] = "paired"
    }

    override func tearDown() {
        app = nil
        super.tearDown()
    }

    // MARK: - AC#2: Loading view shown during sync

    func test_shouldShowLoadingView_duringEventSync() throws {
        // Pre-condition: paired organizer, slow sync response
        app.launchEnvironment["MOCK_SYNC_RESPONSE"] = "loading"
        app.launch()

        // Navigate to organizer zone (swipeLeft moves content left, revealing right tab)
        app.swipeLeft()

        // Assert: Connecting to event... loading indicator appears
        let connectingLabel = app.staticTexts["Connecting to event..."]
        XCTAssertTrue(connectingLabel.waitForExistence(timeout: 5),
                      "Loading view should appear during event sync (AC#2)")
    }

    // MARK: - AC#4: No active event message

    func test_shouldShowNoActiveEventMessage_whenNoEventsReturned() throws {
        // Pre-condition: paired organizer, backend returns empty event list
        app.launchEnvironment["MOCK_SYNC_RESPONSE"] = "no_events"
        app.launch()

        // Navigate to organizer zone (swipeLeft moves content left, revealing right tab)
        app.swipeLeft()

        // Assert: EventPreviewView with "No active event" message appears
        let noEventLabel = app.staticTexts["No active event"]
        XCTAssertTrue(noEventLabel.waitForExistence(timeout: 5),
                      "No active event message should appear when backend returns empty list (AC#4)")
    }

    // MARK: - AC#5: Event preview shown when event is >1h away

    func test_shouldShowEventPreview_whenEventIsMoreThanOneHourAway() throws {
        // Pre-condition: paired organizer, event 2 hours from now
        app.launchEnvironment["MOCK_SYNC_RESPONSE"] = "event_2h_away"
        app.launch()

        // Navigate to organizer zone (swipeLeft moves content left, revealing right tab)
        app.swipeLeft()

        // Assert: EventPreviewView shows event title
        let eventTitle = app.staticTexts["BATbern 99 - Cloud Native"]
        XCTAssertTrue(eventTitle.waitForExistence(timeout: 5),
                      "Event title should appear in preview when event is >1h away (AC#5)")

        // Assert: Countdown text exists (e.g., "Starts in 2h 0m")
        let startsIn = app.staticTexts.matching(NSPredicate(
            format: "label CONTAINS 'Starts in'")).firstMatch
        XCTAssertTrue(startsIn.waitForExistence(timeout: 3),
                      "Countdown should be visible for event >1h away (AC#5)")
    }

    // MARK: - W2.2 + W2.3 integration: SpeakerArrivalView shown when <1h before event

    func test_shouldShowSpeakerArrivalView_whenEventWithinOneHour() throws {
        // Pre-condition: paired organizer, event 30 minutes from now
        app.launchEnvironment["MOCK_SYNC_RESPONSE"] = "event_30min_away"
        app.launch()

        // Navigate to organizer zone (swipeLeft moves content left, revealing right tab)
        app.swipeLeft()

        // Assert: SpeakerArrivalView appears (organizer zone O2)
        let arrivalLabel = app.staticTexts["Speaker Arrival"]
        XCTAssertTrue(arrivalLabel.waitForExistence(timeout: 5),
                      "SpeakerArrivalView should appear when event is <1h away (O2 state)")
    }
}
