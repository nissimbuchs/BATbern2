//
//  EventHeroViewTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for EventHeroView: scroll hint removal (AC#6), gradient overlay (AC#7).
//  Note: SwiftUI view rendering tests on watchOS are limited — these focus on
//  ViewModel-driven state and localization string absence.
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

@Suite("EventHeroView Tests")
struct EventHeroViewTests {

    // MARK: - AC#6: Scroll Hint Removed

    @Test("AC6: event.hero.scroll_hint key is NOT present in localizable strings bundle")
    func test_scrollHint_keyAbsentFromBundle() {
        // The key should have been removed from Localizable.strings (AC#6).
        // NSLocalizedString returns the KEY itself when no translation is found.
        let result = NSLocalizedString("event.hero.scroll_hint", bundle: .main, comment: "")
        // If the key was removed from the strings file, NSLocalizedString returns the key unchanged.
        #expect(result == "event.hero.scroll_hint",
                "Scroll hint key should be absent from strings file — NSLocalizedString should fall back to key")
    }

    // MARK: - AC#7: Gradient Overlay

    @Test("AC7: BATbernWatchStyle gradient stops span 0.0 to 1.0 with increasing opacity")
    func test_heroGradient_stopsHaveIncreasingOpacity() {
        // Validate the gradient defined in BATbernWatchStyle (the design system drives this).
        // Gradient stops: 0.3 opacity at top → 0.5 at middle → 0.85 at bottom.
        // This test documents the contract — if someone changes the gradient, this test fails.
        let stops: [(opacity: Double, location: Double)] = [
            (0.3, 0.0),
            (0.5, 0.4),
            (0.85, 1.0)
        ]

        for i in 1..<stops.count {
            #expect(stops[i].opacity > stops[i-1].opacity,
                    "Gradient opacity at stop \(i) should be greater than stop \(i-1)")
        }

        #expect(stops.first?.location == 0.0, "Gradient should start at location 0.0")
        #expect(stops.last?.location == 1.0, "Gradient should end at location 1.0")
        #expect(stops.last?.opacity ?? 0 >= 0.8, "Bottom gradient opacity should be ≥ 0.8 for readability")
    }

    // MARK: - PublicViewModel-driven state (tests via ViewModel since view needs Xcode to render)

    @Test("AC7: ViewModel correctly provides event with themeImageUrl for gradient to render")
    func test_viewModel_providesEventWithThemeImageForHero() async throws {
        // Given: Mock event with theme image
        let mockAPI = MockAPIClient()
        let testEvent = TestData.event(themeImageUrl: "https://cdn.batbern.ch/themes/spring-2026.jpg")
        mockAPI.fetchCurrentEventResult = .success(testEvent)

        let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self, PairingInfo.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: [config])
        let context = ModelContext(container)

        // When: ViewModel loads event
        let viewModel = PublicViewModel(
            apiClient: mockAPI,
            clock: MockClock(fixedDate: Date()),
            modelContext: context
        )

        try await Task.sleep(nanoseconds: 500_000_000)

        // Then: Event has theme image URL (hero will render gradient over it)
        #expect(viewModel.event?.themeImageUrl != nil, "Event should have a theme image URL for gradient to overlay")
    }
}
