//
//  EventCompletedViewTests.swift
//  BATbern-watch Watch AppTests
//
//  W4.4 Task 4: Tests for EventCompletedView.
//

import Testing
import SwiftUI
@testable import BATbern_watch_Watch_App

@Suite("EventCompletedView")
struct EventCompletedViewTests {

    @Test("EventCompletedView can be instantiated")
    func viewInstantiates() {
        let view = EventCompletedView(eventTitle: "BATbern Spring 2026")
        // Must not crash — view is a valid SwiftUI View
        _ = view
    }
}
