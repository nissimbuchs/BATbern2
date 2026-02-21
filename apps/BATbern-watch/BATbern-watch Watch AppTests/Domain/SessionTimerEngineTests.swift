import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("SessionTimerEngine")
struct SessionTimerEngineTests {

    let clock: MockClock
    let engine: SessionTimerEngine

    init() {
        clock = MockClock(fixedDate: Date(timeIntervalSince1970: 1_000_000))
        engine = SessionTimerEngine(clock: clock)
    }

    // MARK: - Countdown Calculation

    @Test("Calculates correct remaining time from wall clock")
    func remainingTimeCalculation() {
        let session = TestData.fixedSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(2700) // 45 minutes
        )

        engine.setActiveSession(session)

        #expect(engine.remainingSeconds == 2700)
        #expect(engine.isOvertime == false)
        #expect(engine.formattedTime == "45:00")
    }

    @Test("Recalculates correctly after time advances")
    func recalculateAfterTimeAdvance() {
        let session = TestData.fixedSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(2700)
        )

        engine.setActiveSession(session)
        clock.advance(by: 300) // 5 minutes pass
        engine.recalculate()

        #expect(engine.remainingSeconds == 2400) // 40 minutes left
        #expect(engine.formattedTime == "40:00")
    }

    @Test("Survives watchOS suspension — recalculates from wall clock, not counter")
    func survivesAppSuspension() {
        let session = TestData.fixedSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(2700)
        )

        engine.setActiveSession(session)

        // Simulate app suspension: 20 minutes pass without any tick
        clock.advance(by: 1200)
        engine.recalculate()

        #expect(engine.remainingSeconds == 1500) // 25 min left
        #expect(engine.formattedTime == "25:00")
        #expect(engine.isOvertime == false)
    }

    // MARK: - Overtime Detection

    @Test("Detects overtime when session runs past end time")
    func detectsOvertime() {
        let session = TestData.fixedSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(2700)
        )

        engine.setActiveSession(session)
        clock.advance(by: 2820) // 47 minutes (2 min over)
        engine.recalculate()

        #expect(engine.isOvertime == true)
        #expect(engine.overtimeSeconds == 120)
        #expect(engine.remainingSeconds == 0)
        #expect(engine.formattedTime == "+02:00")
    }

    @Test("Overtime at exact boundary")
    func overtimeExactBoundary() {
        let session = TestData.fixedSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(2700)
        )

        engine.setActiveSession(session)
        clock.advance(by: 2700) // exactly at end
        engine.recalculate()

        // At exact boundary: remaining is 0, overtime starts
        #expect(engine.remainingSeconds == 0)
        #expect(engine.isOvertime == true)
        #expect(engine.overtimeSeconds == 0)
    }

    // MARK: - Urgency Levels

    @Test("Urgency level transitions at correct thresholds",
          arguments: [
            (2400.0, UrgencyLevel.caution),   // 5 min left
            (2580.0, UrgencyLevel.warning),   // 2 min left
            (2640.0, UrgencyLevel.critical),  // 1 min left
            (2760.0, UrgencyLevel.overtime),  // 1 min over
            (0.0, UrgencyLevel.normal),       // 45 min left
          ])
    func urgencyLevelTransitions(advance: Double, expected: UrgencyLevel) {
        let session = TestData.fixedSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(2700)
        )

        engine.setActiveSession(session)
        clock.advance(by: advance)
        engine.recalculate()

        #expect(engine.urgencyLevel == expected)
    }

    // MARK: - Edge Cases

    @Test("Clearing active session resets all state")
    func clearResets() {
        let session = TestData.fixedSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(2700)
        )

        engine.setActiveSession(session)
        #expect(engine.remainingSeconds > 0)

        engine.clearActiveSession()

        #expect(engine.remainingSeconds == 0)
        #expect(engine.isOvertime == false)
        #expect(engine.overtimeSeconds == 0)
        #expect(engine.activeSession == nil)
    }

    @Test("Recalculate with no active session is safe")
    func recalculateWithNoSession() {
        engine.recalculate()

        #expect(engine.remainingSeconds == 0)
        #expect(engine.isOvertime == false)
    }

    @Test("Formatted time shows correct zero-padding")
    func formattedTimePadding() {
        let session = TestData.fixedSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(65) // 1:05
        )

        engine.setActiveSession(session)

        #expect(engine.formattedTime == "01:05")
    }

    @Test("Switching sessions recalculates immediately")
    func switchingSessions() {
        let session1 = TestData.fixedSession(
            slug: "talk-1",
            start: clock.now,
            end: clock.now.addingTimeInterval(2700)
        )
        let session2 = TestData.fixedSession(
            slug: "talk-2",
            start: clock.now,
            end: clock.now.addingTimeInterval(1800) // 30 min
        )

        engine.setActiveSession(session1)
        #expect(engine.remainingSeconds == 2700)

        engine.setActiveSession(session2)
        #expect(engine.remainingSeconds == 1800)
        #expect(engine.activeSession?.id == "talk-2")
    }
}
