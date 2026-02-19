//
//  PresenceIndicatorViewTests.swift
//  BATbern-watch Watch AppTests
//
//  Unit tests for PresenceIndicatorView.
//  Story W4.1 Task 4.5.
//
//  Verifies:
//  - Hidden when presenceCount == 0
//  - Shows count text for presenceCount == 1 (single icon)
//  - Shows count text for presenceCount > 1 (group icon)
//  - Teal color when connected, orange when disconnected
//

import Testing
import SwiftUI
@testable import BATbern_watch_Watch_App

@Suite("PresenceIndicatorView")
struct PresenceIndicatorViewTests {

    @Test("hidden when presenceCount is 0")
    func hiddenWhenPresenceCountIsZero() {
        let view = PresenceIndicatorView(presenceCount: 0, isConnected: true)
        #expect(view.isVisible == false, "view must not be visible when presenceCount is 0")
    }

    @Test("visible when presenceCount is 1")
    func visibleWhenPresenceCountIsOne() {
        let view = PresenceIndicatorView(presenceCount: 1, isConnected: true)
        #expect(view.isVisible == true, "view must be visible when presenceCount > 0")
    }

    @Test("shows single-person icon when presenceCount == 1")
    func showsSingleIconForOneOrganizer() {
        let view = PresenceIndicatorView(presenceCount: 1, isConnected: true)
        #expect(view.presenceCount == 1)
        #expect(view.systemImageName == "person.fill")
    }

    @Test("shows group icon when presenceCount > 1")
    func showsGroupIconForMultipleOrganizers() {
        let view = PresenceIndicatorView(presenceCount: 3, isConnected: true)
        #expect(view.presenceCount == 3)
        #expect(view.systemImageName == "person.2.fill")
    }

    @Test("color is teal when connected")
    func colorIsTealWhenConnected() {
        let view = PresenceIndicatorView(presenceCount: 2, isConnected: true)
        #expect(view.indicatorColor == Color.teal)
    }

    @Test("color is orange when disconnected")
    func colorIsOrangeWhenDisconnected() {
        let view = PresenceIndicatorView(presenceCount: 2, isConnected: false)
        #expect(view.indicatorColor == Color.orange)
    }
}
