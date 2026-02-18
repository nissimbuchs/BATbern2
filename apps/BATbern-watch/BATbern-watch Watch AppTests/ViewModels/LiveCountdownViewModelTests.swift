//
//  LiveCountdownViewModelTests.swift
//  BATbern-watch Watch AppTests
//
//  W3.1: Unit tests for LiveCountdownViewModel covering AC2-AC6.
//  Uses MockClock and MockHapticService for deterministic, fast tests.
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

// MARK: - Mock EventStateManager

/// Minimal EventStateManagerProtocol conformance for ViewModel tests.
@MainActor
final class MockEventStateManager: EventStateManagerProtocol {
    var currentEvent: CachedEvent?
    var hasActiveEvent: Bool { currentEvent != nil }
    var isPreEvent: Bool = false
    var isLive: Bool = false
    var timeUntilEventStart: TimeInterval? = nil
}

// MARK: - Tests

@Suite("LiveCountdownViewModel")
@MainActor
struct LiveCountdownViewModelTests {

    /// Fixed epoch for all timer tests — avoids flaky relative-to-now dates.
    let referenceDate = Date(timeIntervalSince1970: 1_000_000)

    // MARK: - Test Helpers

    private func makeVM() -> (vm: LiveCountdownViewModel, clock: MockClock, haptics: MockHapticService, state: MockEventStateManager) {
        let clock = MockClock(fixedDate: referenceDate)
        let haptics = MockHapticService()
        let state = MockEventStateManager()
        let vm = LiveCountdownViewModel(clock: clock, hapticService: haptics)
        vm.eventState = state
        return (vm, clock, haptics, state)
    }

    /// Create a CachedSession with the given start/end for timer tests.
    private func makeSession(
        slug: String = "test-session",
        title: String = "Test Talk",
        start: Date,
        end: Date
    ) -> CachedSession {
        let speaker = CachedSpeaker(
            username: "anna.meier",
            firstName: "Anna",
            lastName: "Meier",
            speakerRole: .primarySpeaker
        )
        return CachedSession(
            sessionSlug: slug,
            title: title,
            sessionType: .presentation,
            startTime: start,
            endTime: end,
            speakers: [speaker]
        )
    }

    /// Create a CachedEvent wrapping the given sessions.
    private func makeEvent(sessions: [CachedSession]) -> CachedEvent {
        CachedEvent(
            eventCode: "bat-2026-spring",
            title: "BATbern Spring 2026",
            eventDate: referenceDate,
            venueName: "Kornhausforum Bern",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            sessions: sessions
        )
    }

    // MARK: - Active Session Discovery (3.2)

    @Test("Active session discovery — session with now between start/end is found")
    func activeSessionDiscovery() {
        let (vm, clock, _, state) = makeVM()

        let session = makeSession(
            start: clock.now.addingTimeInterval(-300),   // started 5 min ago
            end: clock.now.addingTimeInterval(2400)      // 40 min remaining
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()

        #expect(vm.activeSession != nil)
        #expect(vm.activeSession?.id == "test-session")
    }

    // MARK: - Urgency Level Transitions (3.3, 3.4, 3.5, 3.6)

    @Test("urgencyLevel is .normal when remaining > 300s")
    func urgencyNormal() {
        let (vm, clock, _, state) = makeVM()
        let session = makeSession(
            start: clock.now.addingTimeInterval(-60),
            end: clock.now.addingTimeInterval(3600)  // 60 min remaining — well above 5 min
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()

        #expect(vm.urgencyLevel == .normal)
    }

    @Test("urgencyLevel is .caution at exactly 300s remaining")
    func urgencyCautionAt300s() {
        let (vm, clock, _, state) = makeVM()
        // Session ends in exactly 300s from now
        let session = makeSession(
            start: clock.now.addingTimeInterval(-2100),
            end: clock.now.addingTimeInterval(300)
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()

        #expect(vm.urgencyLevel == .caution)
    }

    @Test("urgencyLevel is .warning at exactly 120s remaining")
    func urgencyWarningAt120s() {
        let (vm, clock, _, state) = makeVM()
        let session = makeSession(
            start: clock.now.addingTimeInterval(-2280),
            end: clock.now.addingTimeInterval(120)
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()

        #expect(vm.urgencyLevel == .warning)
    }

    @Test("urgencyLevel is .overtime when past end time")
    func urgencyOvertime() {
        let (vm, clock, _, state) = makeVM()
        let session = makeSession(
            start: clock.now.addingTimeInterval(-3750),
            end: clock.now.addingTimeInterval(-150)   // 150s past end
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()

        #expect(vm.urgencyLevel == .overtime)
    }

    // MARK: - Formatted Time (3.7, 3.8)

    @Test("formattedTime is 22:45 for 1365 seconds remaining")
    func formattedTimeNormal() {
        let (vm, clock, _, state) = makeVM()
        let session = makeSession(
            start: clock.now.addingTimeInterval(-300),
            end: clock.now.addingTimeInterval(1365)
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()

        #expect(vm.formattedTime == "22:45")
    }

    @Test("formattedTime is +02:30 when 150s overtime")
    func formattedTimeOvertime() {
        let (vm, clock, _, state) = makeVM()
        let session = makeSession(
            start: clock.now.addingTimeInterval(-3750),
            end: clock.now.addingTimeInterval(-150)   // 150s overtime
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()

        #expect(vm.formattedTime == "+02:30")
    }

    // MARK: - Progress (3.9)

    @Test("Progress advances from 0 to 1 as time elapses")
    func progressAdvances() {
        let (vm, clock, _, state) = makeVM()
        let session = makeSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(3600)  // 60-min session
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()
        let initialProgress = vm.progress

        clock.advance(by: 900)  // 15 min
        vm.refreshState()
        let laterProgress = vm.progress

        #expect(initialProgress >= 0)
        #expect(laterProgress > initialProgress)
        #expect(laterProgress <= 1.0)
        // After 15/60 min, expect ~0.25
        #expect(abs(laterProgress - 0.25) < 0.01)
    }

    // MARK: - Critical Urgency (H2 — AC4 full coverage)

    @Test("urgencyLevel is .critical at exactly 60s remaining")
    func urgencyCriticalAt60s() {
        let (vm, clock, _, state) = makeVM()
        let session = makeSession(
            start: clock.now.addingTimeInterval(-2340),
            end: clock.now.addingTimeInterval(60)
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()

        #expect(vm.urgencyLevel == .critical)
    }

    // MARK: - Next Session Discovery (M1)

    @Test("nextSession skips break and returns first non-break session after active")
    func nextSessionSkipsBreak() {
        let (vm, clock, _, state) = makeVM()

        let active = makeSession(
            slug: "talk-1",
            title: "First Talk",
            start: clock.now.addingTimeInterval(-300),
            end: clock.now.addingTimeInterval(2400)
        )
        let breakSession = CachedSession(
            sessionSlug: "coffee-break",
            title: "Coffee Break",
            sessionType: .breakTime,
            startTime: clock.now.addingTimeInterval(2401),
            endTime: clock.now.addingTimeInterval(3001),
            speakers: []
        )
        let next = makeSession(
            slug: "talk-2",
            title: "Second Talk",
            start: clock.now.addingTimeInterval(3001),
            end: clock.now.addingTimeInterval(5701)
        )
        state.currentEvent = makeEvent(sessions: [active, breakSession, next])

        vm.refreshState()

        #expect(vm.activeSession?.id == "talk-1")
        #expect(vm.nextSession?.id == "talk-2")
    }

    @Test("nextSession is nil when active session is the last one")
    func nextSessionNilWhenLast() {
        let (vm, clock, _, state) = makeVM()

        let session = makeSession(
            start: clock.now.addingTimeInterval(-300),
            end: clock.now.addingTimeInterval(2400)
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()

        #expect(vm.activeSession != nil)
        #expect(vm.nextSession == nil)
    }

    // MARK: - Break Session Haptic Routing (M2)

    @Test("Break session routes to gong reminder, not threshold alerts")
    func breakSessionFiresGongNotThresholdAlerts() {
        let (vm, _, haptics, state) = makeVM()

        // Break ending in 30s — within gong lead time (60s threshold)
        let breakSession = CachedSession(
            sessionSlug: "coffee-break",
            title: "Coffee Break",
            sessionType: .breakTime,
            startTime: referenceDate.addingTimeInterval(-1770),
            endTime: referenceDate.addingTimeInterval(30),
            speakers: []
        )
        state.currentEvent = makeEvent(sessions: [breakSession])

        vm.refreshState()

        #expect(haptics.playedAlerts.contains(.gongReminder))
        #expect(!haptics.playedAlerts.contains(.fiveMinuteWarning))
        #expect(!haptics.playedAlerts.contains(.twoMinuteWarning))
    }

    // MARK: - Fallback Session Discovery (M4)

    @Test("Fallback to upcoming session when no session is currently active")
    func fallbackToUpcomingSession() {
        let (vm, clock, _, state) = makeVM()

        // Session starts 1 hour in the future
        let upcoming = makeSession(
            slug: "upcoming-talk",
            title: "Upcoming Talk",
            start: clock.now.addingTimeInterval(3600),
            end: clock.now.addingTimeInterval(7200)
        )
        state.currentEvent = makeEvent(sessions: [upcoming])

        vm.refreshState()

        #expect(vm.activeSession?.id == "upcoming-talk")
    }

    // MARK: - Extended Runtime Session Lifecycle (AC6 wiring)

    @Test("startTimer calls startEventSession — AC6 background haptic delivery wired")
    func startTimer_callsStartEventSession() {
        let (vm, _, haptics, _) = makeVM()
        #expect(haptics.startEventSessionCallCount == 0)
        vm.startTimer()
        #expect(haptics.startEventSessionCallCount == 1)
        vm.stopTimer()
    }

    @Test("stopTimer calls stopEventSession — Extended Runtime session released on dismiss")
    func stopTimer_callsStopEventSession() {
        let (vm, _, haptics, _) = makeVM()
        vm.startTimer()
        #expect(haptics.stopEventSessionCallCount == 0)
        vm.stopTimer()
        #expect(haptics.stopEventSessionCallCount == 1)
    }

    // MARK: - Wall-Clock Accuracy (3.10, AC6)

    @Test("Wall-clock recalculation survives simulated suspension")
    func wallClockAccuracyAfterSuspension() {
        let (vm, clock, _, state) = makeVM()
        // Session started 5 min ago, ends 40 min from now
        let session = makeSession(
            start: clock.now.addingTimeInterval(-300),
            end: clock.now.addingTimeInterval(2400)
        )
        state.currentEvent = makeEvent(sessions: [session])

        vm.refreshState()
        #expect(vm.formattedTime == "40:00")

        // Simulate watchOS app suspension: 15 minutes pass with NO timer ticks
        clock.advance(by: 900)

        // Single recalculate after resuming — wall-clock gives correct value
        vm.refreshState()

        // Should show 25:00 remaining (not still 40:00 as a decrementing counter would)
        #expect(vm.formattedTime == "25:00")
        #expect(vm.urgencyLevel == .normal)
    }
}
