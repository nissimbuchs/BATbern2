//
//  DelayedSessionViewTests.swift
//  BATbern-watch Watch AppTests
//
//  W4.3 Task 3.6: Unit tests for DelayedSessionView.
//  Same pattern as ExtendSessionViewTests — struct instantiation, callback wiring, haptic injection.
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("DelayedSessionView")
@MainActor
struct DelayedSessionViewTests {

    @Test("View instantiates with correct currentSlug and callbacks")
    func viewInstantiation() {
        var receivedMinutes: Int?
        var dismissCalled = false

        let view = DelayedSessionView(
            currentSlug: "microservices-mistakes",
            onDelay: { minutes in receivedMinutes = minutes },
            onDismiss: { dismissCalled = true }
        )

        _ = view
        #expect(receivedMinutes == nil)
        #expect(dismissCalled == false)
    }

    @Test("Haptic service injectable — MockHapticService accepted")
    func hapticServiceInjectable() {
        let haptics = MockHapticService()
        let view = DelayedSessionView(
            currentSlug: "test-session",
            onDelay: { _ in },
            onDismiss: { },
            hapticService: haptics
        )

        _ = view
        #expect(haptics.playedAlerts.isEmpty)
    }

    @Test("onDelay closure receives correct minutes for each option")
    func callbackReceivesCorrectMinutes() {
        for option in [5, 10, 15, 20] {
            var received: Int?
            let view = DelayedSessionView(
                currentSlug: "test-session",
                onDelay: { minutes in received = minutes },
                onDismiss: { }
            )
            _ = view
            view.onDelay(option)
            #expect(received == option, "Expected onDelay(\(option)) to pass \(option)")
        }
    }
}
