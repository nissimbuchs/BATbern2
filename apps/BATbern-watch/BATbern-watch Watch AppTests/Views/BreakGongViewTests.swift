//
//  BreakGongViewTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for BreakGongView (W4.4 Task 2):
//  - View can be constructed with a ViewModel
//  - gongOverlayVisible drives the "break ending soon" banner
//
//  Source: docs/watch-app/ux-design-specification.md#Session-Card-Pages
//

import Testing
import SwiftUI
@testable import BATbern_watch_Watch_App

@Suite("BreakGongView")
@MainActor
struct BreakGongViewTests {

    private let referenceDate = Date(timeIntervalSince1970: 1_000_000)

    private func makeVM(
        breakSession: CachedSession? = nil,
        advanceClockBy: TimeInterval = 0
    ) -> (vm: LiveCountdownViewModel, clock: MockClock) {
        let clock = MockClock(fixedDate: referenceDate.addingTimeInterval(advanceClockBy))
        let haptics = MockHapticService()
        let vm = LiveCountdownViewModel(clock: clock, hapticService: haptics)
        let state = MockEventStateManager()
        if let session = breakSession {
            state.currentEvent = CachedEvent(
                eventCode: "bat-2026-spring",
                title: "BATbern Spring 2026",
                eventDate: referenceDate,
                venueName: "Kornhausforum Bern",
                typicalStartTime: "18:00",
                typicalEndTime: "22:00",
                sessions: [session]
            )
        }
        vm.eventState = state
        if breakSession != nil {
            vm.refreshState()
        }
        return (vm, clock)
    }

    @Test("BreakGongView renders without crashing when no active session")
    func rendersWithoutActiveSession() {
        let (vm, _) = makeVM()
        let view = BreakGongView(viewModel: vm)
        // View construction must not crash
        #expect(view.viewModel === vm)
    }

    @Test("BreakGongView renders with active break session")
    func rendersWithBreakSession() {
        let breakSession = CachedSession(
            sessionSlug: "coffee-break",
            title: "Coffee Break",
            sessionType: .breakTime,
            startTime: referenceDate.addingTimeInterval(-600),
            endTime: referenceDate.addingTimeInterval(600),
            speakers: []
        )
        let (vm, _) = makeVM(breakSession: breakSession)
        let view = BreakGongView(viewModel: vm)

        #expect(vm.activeSession?.id == "coffee-break")
        #expect(view.viewModel.activeSession?.id == "coffee-break")
    }

    @Test("gongOverlayVisible is false when break has more than 60s remaining")
    func gongOverlay_hiddenWhenBreakHasTimeLeft() {
        let breakSession = CachedSession(
            sessionSlug: "coffee-break",
            title: "Coffee Break",
            sessionType: .breakTime,
            startTime: referenceDate.addingTimeInterval(-600),
            endTime: referenceDate.addingTimeInterval(120),  // 120s remaining
            speakers: []
        )
        let (vm, _) = makeVM(breakSession: breakSession)

        #expect(vm.gongOverlayVisible == false)
    }

    @Test("gongOverlayVisible is true when break has ≤60s remaining")
    func gongOverlay_visibleWhenBreakEndingSoon() {
        let breakSession = CachedSession(
            sessionSlug: "coffee-break",
            title: "Coffee Break",
            sessionType: .breakTime,
            startTime: referenceDate.addingTimeInterval(-1770),
            endTime: referenceDate.addingTimeInterval(30),  // 30s remaining
            speakers: []
        )
        let (vm, _) = makeVM(breakSession: breakSession)

        #expect(vm.gongOverlayVisible == true)
        let view = BreakGongView(viewModel: vm)
        #expect(view.viewModel.gongOverlayVisible == true)
    }

    @Test("BreakGongView uses same ViewModel instance as caller")
    func sharedViewModelInstance() {
        let (vm, _) = makeVM()
        let view = BreakGongView(viewModel: vm)
        // Must be same object — no new VM allocation
        #expect(view.viewModel === vm)
    }

    @Test("NextSessionPeekView rendered when viewModel.nextSession is not nil")
    func nextSessionPeek_renderedWhenNextSessionExists() {
        let breakSession = CachedSession(
            sessionSlug: "coffee-break",
            title: "Coffee Break",
            sessionType: .breakTime,
            startTime: referenceDate.addingTimeInterval(-300),
            endTime: referenceDate.addingTimeInterval(600),
            speakers: []
        )
        let talkSession = CachedSession(
            sessionSlug: "next-talk",
            title: "Architecture Patterns",
            sessionType: .presentation,
            startTime: referenceDate.addingTimeInterval(600),
            endTime: referenceDate.addingTimeInterval(3000),
            speakers: []
        )
        let clock = MockClock(fixedDate: referenceDate)
        let vm = LiveCountdownViewModel(clock: clock, hapticService: MockHapticService())
        let state = MockEventStateManager()
        state.currentEvent = CachedEvent(
            eventCode: "bat-2026-spring",
            title: "BATbern Spring 2026",
            eventDate: referenceDate,
            venueName: "Kornhausforum Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            sessions: [breakSession, talkSession]
        )
        vm.eventState = state
        vm.refreshState()

        #expect(vm.nextSession != nil)
        let view = BreakGongView(viewModel: vm)
        #expect(view.viewModel.nextSession != nil)
    }

    @Test("NextSessionPeekView NOT rendered when viewModel.nextSession is nil")
    func nextSessionPeek_notRenderedWhenNoNextSession() {
        // Single break session — no subsequent talk exists (final break before event end)
        let breakSession = CachedSession(
            sessionSlug: "final-break",
            title: "Final Break",
            sessionType: .breakTime,
            startTime: referenceDate.addingTimeInterval(-300),
            endTime: referenceDate.addingTimeInterval(600),
            speakers: []
        )
        let (vm, _) = makeVM(breakSession: breakSession)

        #expect(vm.nextSession == nil)
        let view = BreakGongView(viewModel: vm)
        #expect(view.viewModel.nextSession == nil)
    }

    @Test("Countdown text in BreakGongView matches viewModel.formattedTime")
    func countdownText_matchesFormattedTime() {
        // 600 seconds remaining → formattedTime should be "10:00"
        let breakSession = CachedSession(
            sessionSlug: "coffee-break",
            title: "Coffee Break",
            sessionType: .breakTime,
            startTime: referenceDate.addingTimeInterval(-300),
            endTime: referenceDate.addingTimeInterval(600),
            speakers: []
        )
        let (vm, _) = makeVM(breakSession: breakSession)

        #expect(vm.formattedTime == "10:00")
        let view = BreakGongView(viewModel: vm)
        #expect(view.viewModel.formattedTime == "10:00")
    }
}
