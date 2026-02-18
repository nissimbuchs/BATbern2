//
//  WatchHapticServiceTests.swift
//  BATbern-watch Watch AppTests
//
//  W3.2: Unit tests for WatchHapticService — queue management and protocol conformance.
//  Note: WKInterfaceDevice.play() is a no-op in Simulator; tests assert on queue state only.
//  Haptic distinctiveness (AC7) must be verified manually on physical Apple Watch.
//
//  Mapping:
//    4.3 — Protocol conformance (compile-time)
//    4.4 — cancel() removes correct alert
//    4.5 — cancelAll() clears all
//    4.6 — scheduling same alert twice yields two entries
//    4.8 — Integration path covered by existing HapticSchedulerTests (uses MockHapticService)
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

// MARK: - WatchHapticServiceTests (4.2)

@Suite("WatchHapticService")
@MainActor
struct WatchHapticServiceTests {

    // MARK: - 4.3 Protocol Conformance (compile-time + smoke)

    /// Verifies that WatchHapticService satisfies the HapticServiceProtocol contract.
    ///
    /// The type annotation `let service: HapticServiceProtocol` is the authoritative
    /// compile-time check — this test will not compile if the conformance is broken.
    /// The method calls below smoke-test the full protocol surface without crashing.
    @Test("WatchHapticService conforms to HapticServiceProtocol — compile-time and smoke test")
    func protocolConformance() {
        // Compile-time: fails to compile if WatchHapticService drops HapticServiceProtocol.
        let service: HapticServiceProtocol = WatchHapticService()
        // Smoke: all protocol methods are callable without crashing (WKInterfaceDevice is
        // a no-op in Simulator; startEventSession/stopEventSession guarded by #if simulator).
        service.schedule(.fiveMinuteWarning, at: Date(timeIntervalSinceNow: 60))
        service.cancel(.fiveMinuteWarning)
        service.cancelAll()
        service.startEventSession()
        service.stopEventSession()
    }

    // MARK: - 4.4 cancel() removes correct alert

    @Test("cancel() removes only the specified alert type from the queue")
    func cancelRemovesCorrectAlert() {
        let service = WatchHapticService()
        let futureDate = Date(timeIntervalSinceNow: 60)

        service.schedule(.fiveMinuteWarning, at: futureDate)
        service.schedule(.twoMinuteWarning, at: futureDate)
        service.schedule(.timesUp, at: futureDate)

        service.cancel(.twoMinuteWarning)

        let remaining = service.scheduledQueue.map { $0.alert }
        #expect(remaining.contains(.fiveMinuteWarning))
        #expect(!remaining.contains(.twoMinuteWarning))
        #expect(remaining.contains(.timesUp))
    }

    @Test("cancel() removes all entries of the specified alert type")
    func cancelRemovesAllOfType() {
        let service = WatchHapticService()
        let futureDate = Date(timeIntervalSinceNow: 60)

        service.schedule(.fiveMinuteWarning, at: futureDate)
        service.schedule(.fiveMinuteWarning, at: futureDate.addingTimeInterval(30))
        service.schedule(.timesUp, at: futureDate)

        service.cancel(.fiveMinuteWarning)

        let remaining = service.scheduledQueue.map { $0.alert }
        #expect(!remaining.contains(.fiveMinuteWarning))
        #expect(remaining.contains(.timesUp))
        #expect(service.scheduledQueue.count == 1)
    }

    @Test("cancel() on empty queue does nothing")
    func cancelOnEmptyQueue() {
        let service = WatchHapticService()
        service.cancel(.timesUp)
        #expect(service.scheduledQueue.isEmpty)
    }

    // MARK: - 4.5 cancelAll() clears all

    @Test("cancelAll() removes all scheduled alerts")
    func cancelAllClearsQueue() {
        let service = WatchHapticService()
        let futureDate = Date(timeIntervalSinceNow: 60)

        service.schedule(.fiveMinuteWarning, at: futureDate)
        service.schedule(.twoMinuteWarning, at: futureDate)
        service.schedule(.timesUp, at: futureDate)
        service.schedule(.gongReminder, at: futureDate)

        service.cancelAll()

        #expect(service.scheduledQueue.isEmpty)
    }

    @Test("cancelAll() on empty queue does nothing")
    func cancelAllOnEmptyQueue() {
        let service = WatchHapticService()
        service.cancelAll()
        #expect(service.scheduledQueue.isEmpty)
    }

    // MARK: - 4.6 Duplicate scheduling yields two entries (no dedup in service)

    @Test("scheduling same alert twice adds two entries — dedup is HapticScheduler's responsibility")
    func scheduleSameAlertTwiceYieldsTwoEntries() {
        let service = WatchHapticService()
        let date1 = Date(timeIntervalSinceNow: 60)
        let date2 = Date(timeIntervalSinceNow: 120)

        service.schedule(.fiveMinuteWarning, at: date1)
        service.schedule(.fiveMinuteWarning, at: date2)

        #expect(service.scheduledQueue.count == 2)
        #expect(service.scheduledQueue[0].alert == .fiveMinuteWarning)
        #expect(service.scheduledQueue[1].alert == .fiveMinuteWarning)
    }

    @Test("schedule() preserves insertion order")
    func schedulePreservesOrder() {
        let service = WatchHapticService()
        let base = Date(timeIntervalSinceNow: 60)

        service.schedule(.fiveMinuteWarning, at: base)
        service.schedule(.twoMinuteWarning, at: base.addingTimeInterval(180))
        service.schedule(.timesUp, at: base.addingTimeInterval(300))

        #expect(service.scheduledQueue.count == 3)
        #expect(service.scheduledQueue[0].alert == .fiveMinuteWarning)
        #expect(service.scheduledQueue[1].alert == .twoMinuteWarning)
        #expect(service.scheduledQueue[2].alert == .timesUp)
    }

    // MARK: - 4.8 Integration path note
    //
    // Full pipeline integration (HapticScheduler → WatchHapticService.play()) cannot be
    // automated in unit tests because WKInterfaceDevice requires physical device.
    // The equivalent integration path (HapticScheduler → MockHapticService.play()) is
    // comprehensively covered in HapticSchedulerTests.swift (11 test cases).
    // See: BATbern-watch Watch AppTests/Domain/HapticSchedulerTests.swift

    // MARK: - D1 Scheduled Firing

    @Test("schedule() removes the entry from the queue after the delay elapses")
    func scheduledAlertRemovesFromQueueAfterDelay() async {
        let service = WatchHapticService()

        service.schedule(.fiveMinuteWarning, at: Date(timeIntervalSinceNow: 0.05))
        #expect(service.scheduledQueue.count == 1)  // Queued immediately

        try? await Task.sleep(nanoseconds: 150_000_000)  // Wait 0.15s — past the 0.05s fire time

        #expect(service.scheduledQueue.isEmpty)  // Task fired, entry removed
    }

    @Test("schedule() with a past date fires immediately on next runloop turn")
    func schedulePastDateFiresImmediately() async {
        let service = WatchHapticService()

        service.schedule(.timesUp, at: Date(timeIntervalSinceNow: -5))  // 5s ago

        try? await Task.sleep(nanoseconds: 100_000_000)  // Small yield

        #expect(service.scheduledQueue.isEmpty)
    }

    @Test("cancelAll() before scheduled time prevents the entry from re-appearing after delay")
    func cancelAllBeforeFireTimePreventsPlay() async {
        let service = WatchHapticService()

        service.schedule(.fiveMinuteWarning, at: Date(timeIntervalSinceNow: 0.1))
        service.cancelAll()

        #expect(service.scheduledQueue.isEmpty)  // Cleared immediately

        try? await Task.sleep(nanoseconds: 200_000_000)  // Wait past fire time

        #expect(service.scheduledQueue.isEmpty)  // Did not re-add on wakeup
    }

    @Test("cancel() before fire time suppresses that alert via the queue guard")
    func cancelBeforeFireTimeSuppressesViaGuard() async {
        let service = WatchHapticService()
        let fireAt = Date(timeIntervalSinceNow: 0.1)

        service.schedule(.fiveMinuteWarning, at: fireAt)
        service.schedule(.twoMinuteWarning, at: fireAt)

        service.cancel(.fiveMinuteWarning)

        #expect(service.scheduledQueue.count == 1)
        #expect(service.scheduledQueue[0].alert == .twoMinuteWarning)

        try? await Task.sleep(nanoseconds: 200_000_000)  // Wait past fire time

        // twoMinuteWarning fired and was removed; fiveMinuteWarning guard prevented play
        #expect(service.scheduledQueue.isEmpty)
    }

    @Test("stopEventSession() before fire time cancels the scheduled task")
    func stopEventSessionCancelsScheduledTask() async {
        let service = WatchHapticService()

        service.schedule(.gongReminder, at: Date(timeIntervalSinceNow: 0.1))
        service.stopEventSession()

        #expect(service.scheduledQueue.isEmpty)  // cancelAll() inside stop clears queue

        try? await Task.sleep(nanoseconds: 200_000_000)

        #expect(service.scheduledQueue.isEmpty)  // Task was cancelled — nothing re-added
    }
}
