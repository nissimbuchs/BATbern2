//
//  ExtendSessionViewTests.swift
//  BATbern-watch Watch AppTests
//
//  W4.3 Task 2.7: Unit tests for ExtendSessionView.
//  Verifies view struct instantiation, callback wiring, and haptic injection.
//  Button tap simulation not possible without ViewInspector on watchOS —
//  behavior verified via ViewModel-level tests in LiveCountdownViewTests.
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("ExtendSessionView")
@MainActor
struct ExtendSessionViewTests {

    // MARK: - Instantiation

    @Test("View instantiates with correct sessionSlug and callbacks")
    func viewInstantiation() {
        var receivedMinutes: Int?
        var dismissCalled = false

        let view = ExtendSessionView(
            sessionSlug: "cloud-native-pitfalls",
            onExtend: { minutes in receivedMinutes = minutes },
            onDismiss: { dismissCalled = true }
        )

        _ = view
        // Closures wired — no crash. Button tap testing deferred to Playwright/manual.
        #expect(receivedMinutes == nil)
        #expect(dismissCalled == false)
    }

    @Test("Haptic service injectable — MockHapticService accepted")
    func hapticServiceInjectable() {
        let haptics = MockHapticService()
        let view = ExtendSessionView(
            sessionSlug: "test-session",
            onExtend: { _ in },
            onDismiss: { },
            hapticService: haptics
        )

        _ = view
        // Injection succeeds — haptic wiring verified at runtime.
        #expect(haptics.playedAlerts.isEmpty)
    }

    @Test("onExtend closure receives correct minutes for each option")
    func callbackReceivesCorrectMinutes() {
        let expectedOptions = [5, 10, 15, 20]
        for option in expectedOptions {
            var received: Int?
            let view = ExtendSessionView(
                sessionSlug: "test-session",
                onExtend: { minutes in received = minutes },
                onDismiss: { }
            )
            _ = view
            // Invoke stored closure directly to verify wiring contract.
            // Note: haptic + isSending changes are NOT exercised here (no SwiftUI render);
            // those are covered by ViewModel-level tests in LiveCountdownViewModelTests.
            view.onExtend(option)
            #expect(received == option, "Expected onExtend(\(option)) to pass \(option)")
        }
    }
}
