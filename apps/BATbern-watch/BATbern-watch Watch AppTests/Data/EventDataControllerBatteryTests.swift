//
//  EventDataControllerBatteryTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for EventDataController's battery-adaptive polling interval.
//  Story W5.3 Task 5.2.
//
//  Tests use `controller.refreshInterval` (internal property) to assert the interval
//  directly without running the full periodic timer — same technique as
//  ConnectivityMonitor using `processConnectivityChange` for unit tests.
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

@Suite("EventDataController — adaptive refresh interval", .serialized)
@MainActor
struct EventDataControllerBatteryTests {

    // MARK: - Fixture

    private func makeController(
        batteryMonitor: MockBatteryMonitor = MockBatteryMonitor()
    ) throws -> EventDataController {
        let schema = Schema([CachedEvent.self, CachedSession.self, CachedSpeaker.self, OfflineAction.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: [config])
        let context = ModelContext(container)

        return EventDataController(
            authManager: MockAuthManager(),
            modelContext: context,
            connectivityMonitor: MockConnectivityMonitor(),
            batteryMonitor: batteryMonitor,
            skipAutoSync: true
        )
    }

    // MARK: - AC3: Adaptive polling interval

    @Test("refreshInterval is 15 min when battery is low (< 20%)")
    func refreshInterval_is15Min_whenBatteryLow() throws {
        let monitor = MockBatteryMonitor(isLowBattery: true)
        let controller = try makeController(batteryMonitor: monitor)

        #expect(controller.refreshInterval == .seconds(15 * 60))
    }

    @Test("refreshInterval is 5 min when battery is normal (>= 20%)")
    func refreshInterval_is5Min_whenBatteryNormal() throws {
        let monitor = MockBatteryMonitor(isLowBattery: false)
        let controller = try makeController(batteryMonitor: monitor)

        #expect(controller.refreshInterval == .seconds(5 * 60))
    }

    @Test("refreshInterval re-evaluates per tick when battery drops below threshold")
    func refreshInterval_reEvaluates_whenBatteryDropsBelowThreshold() throws {
        let monitor = MockBatteryMonitor(isLowBattery: false)
        let controller = try makeController(batteryMonitor: monitor)

        #expect(controller.refreshInterval == .seconds(5 * 60))

        // Battery drops below 20%
        monitor.isLowBattery = true

        // refreshInterval is a computed property — re-evaluates immediately on read
        #expect(controller.refreshInterval == .seconds(15 * 60))
    }

    @Test("refreshInterval re-evaluates per tick when battery recovers above threshold")
    func refreshInterval_reEvaluates_whenBatteryRecovers() throws {
        let monitor = MockBatteryMonitor(isLowBattery: true)
        let controller = try makeController(batteryMonitor: monitor)

        #expect(controller.refreshInterval == .seconds(15 * 60))

        // Battery charges back above 20%
        monitor.isLowBattery = false

        #expect(controller.refreshInterval == .seconds(5 * 60))
    }

    @Test("refreshInterval is 5 min when batteryLevel is unknown (-1.0, fail-safe)")
    func refreshInterval_is5Min_whenBatteryLevelUnknown() throws {
        // batteryLevel = -1.0 → isLowBattery = false (fail-safe: do not reduce polling)
        let monitor = MockBatteryMonitor(batteryLevel: -1.0)
        let controller = try makeController(batteryMonitor: monitor)

        #expect(controller.refreshInterval == .seconds(5 * 60))
    }
}
