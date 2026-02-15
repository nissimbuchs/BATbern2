import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("HapticScheduler")
struct HapticSchedulerTests {

    let clock: MockClock
    let hapticService: MockHapticService
    let scheduler: HapticScheduler

    init() {
        clock = MockClock(fixedDate: Date(timeIntervalSince1970: 1_000_000))
        hapticService = MockHapticService()
        scheduler = HapticScheduler(clock: clock, hapticService: hapticService)
    }

    // MARK: - Threshold Alerts

    @Test("Fires 5-minute warning at correct threshold")
    func fiveMinuteWarning() {
        let session = TestData.fixedSession(
            start: clock.now.addingTimeInterval(-2400),
            end: clock.now.addingTimeInterval(300) // 5 min left
        )

        let fired = scheduler.evaluate(session: session)

        #expect(fired == [.fiveMinuteWarning])
        #expect(hapticService.playedAlerts == [.fiveMinuteWarning])
    }

    @Test("Fires 2-minute warning at correct threshold")
    func twoMinuteWarning() {
        let session = TestData.fixedSession(
            start: clock.now.addingTimeInterval(-2580),
            end: clock.now.addingTimeInterval(120) // 2 min left
        )

        let fired = scheduler.evaluate(session: session)

        #expect(fired == [.twoMinuteWarning])
        #expect(hapticService.playedAlerts == [.twoMinuteWarning])
    }

    @Test("Fires time's up when session time reaches zero")
    func timesUp() {
        let session = TestData.fixedSession(
            start: clock.now.addingTimeInterval(-2700),
            end: clock.now // exactly now
        )

        let fired = scheduler.evaluate(session: session)

        #expect(fired == [.timesUp])
    }

    @Test("Does NOT fire any alert when plenty of time remains")
    func noAlertWhenPlenty() {
        let session = TestData.fixedSession(
            start: clock.now,
            end: clock.now.addingTimeInterval(2700) // 45 min left
        )

        let fired = scheduler.evaluate(session: session)

        #expect(fired.isEmpty)
        #expect(hapticService.playedAlerts.isEmpty)
    }

    // MARK: - Deduplication

    @Test("Never fires same alert twice (deduplication)")
    func deduplication() {
        let session = TestData.fixedSession(
            start: clock.now.addingTimeInterval(-2400),
            end: clock.now.addingTimeInterval(180) // 3 min left → fires 5-min
        )

        scheduler.evaluate(session: session)
        #expect(hapticService.playedAlerts.count == 1)

        // Second evaluation at same threshold — no duplicate
        scheduler.evaluate(session: session)
        #expect(hapticService.playedAlerts.count == 1)
    }

    @Test("Overtime pulse fires repeatedly (exception to deduplication)")
    func overtimePulseRepeats() {
        let session = TestData.fixedSession(
            start: clock.now.addingTimeInterval(-2700),
            end: clock.now.addingTimeInterval(-60) // 1 min overtime
        )

        scheduler.evaluate(session: session)
        let firstCount = hapticService.playedAlerts.filter { $0 == .overtimePulse }.count
        #expect(firstCount == 1)

        // Overtime pulse resets and fires again
        scheduler.evaluate(session: session)
        let secondCount = hapticService.playedAlerts.filter { $0 == .overtimePulse }.count
        #expect(secondCount == 2)
    }

    // MARK: - Break Gong

    @Test("Fires gong reminder before break ends")
    func gongReminder() {
        let breakSession = TestData.fixedSession(
            slug: "break-1",
            title: "Coffee Break",
            start: clock.now.addingTimeInterval(-900),
            end: clock.now.addingTimeInterval(45), // 45 sec left
            type: .breakTime
        )

        let fired = scheduler.evaluateBreakGong(breakSession: breakSession)

        #expect(fired == [.gongReminder])
    }

    @Test("Does NOT fire gong when break has plenty of time")
    func noGongWhenPlenty() {
        let breakSession = TestData.fixedSession(
            slug: "break-1",
            title: "Coffee Break",
            start: clock.now,
            end: clock.now.addingTimeInterval(1200), // 20 min left
            type: .breakTime
        )

        let fired = scheduler.evaluateBreakGong(breakSession: breakSession)

        #expect(fired.isEmpty)
    }

    @Test("Does NOT fire gong after break is over")
    func noGongAfterBreak() {
        let breakSession = TestData.fixedSession(
            slug: "break-1",
            title: "Coffee Break",
            start: clock.now.addingTimeInterval(-1200),
            end: clock.now.addingTimeInterval(-60), // already ended
            type: .breakTime
        )

        let fired = scheduler.evaluateBreakGong(breakSession: breakSession)

        #expect(fired.isEmpty)
    }

    // MARK: - Reset

    @Test("Reset clears all fired alerts, allowing re-firing")
    func resetClears() {
        let session = TestData.fixedSession(
            start: clock.now.addingTimeInterval(-2400),
            end: clock.now.addingTimeInterval(180)
        )

        scheduler.evaluate(session: session)
        #expect(!scheduler.firedAlerts.isEmpty)

        scheduler.reset()
        #expect(scheduler.firedAlerts.isEmpty)
    }

    // MARK: - Full Lifecycle

    @Test("Walk through full session fires alerts in correct order")
    func fullSessionLifecycle() {
        let sessionStart = clock.now
        let sessionEnd = sessionStart.addingTimeInterval(2700)

        let session = TestData.fixedSession(start: sessionStart, end: sessionEnd)

        // 30 min remaining — nothing fires
        clock.advance(by: 900)
        scheduler.evaluate(session: session)
        #expect(hapticService.playedAlerts.isEmpty)

        // 4:50 remaining — 5-min warning
        clock.set(to: sessionEnd.addingTimeInterval(-290))
        scheduler.evaluate(session: session)
        #expect(hapticService.playedAlerts.last == .fiveMinuteWarning)

        // 1:50 remaining — 2-min warning
        clock.set(to: sessionEnd.addingTimeInterval(-110))
        scheduler.evaluate(session: session)
        #expect(hapticService.playedAlerts.last == .twoMinuteWarning)

        // Time's up
        clock.set(to: sessionEnd)
        scheduler.evaluate(session: session)
        #expect(hapticService.playedAlerts.last == .timesUp)

        // 1 min overtime — overtime pulse
        clock.set(to: sessionEnd.addingTimeInterval(60))
        scheduler.evaluate(session: session)
        #expect(hapticService.playedAlerts.last == .overtimePulse)

        // 4 distinct alert types fired
        let uniqueAlerts = Set(hapticService.playedAlerts)
        #expect(uniqueAlerts.count == 4)
    }

    // MARK: - Custom Thresholds

    @Test("Respects custom thresholds")
    func customThresholds() {
        let customScheduler = HapticScheduler(
            clock: clock,
            hapticService: hapticService,
            thresholds: HapticScheduler.Thresholds(
                fiveMinute: 600, // 10 minutes
                twoMinute: 180,  // 3 minutes
                timesUp: 0,
                overtimeInterval: 60,
                gongLeadTime: 120
            )
        )

        let session = TestData.fixedSession(
            start: clock.now.addingTimeInterval(-2100),
            end: clock.now.addingTimeInterval(480) // 8 min left
        )

        // With custom 10-min threshold, 8 min remaining triggers 5-min warning
        let fired = customScheduler.evaluate(session: session)
        #expect(fired == [.fiveMinuteWarning])
    }
}
