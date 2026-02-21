//
//  BatteryMonitorTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for BatteryMonitorProtocol's isLowBattery computation logic.
//  Uses MockBatteryMonitor with batteryLevel-only init to exercise the same threshold
//  logic used by BatteryMonitor.isLowBattery — ensures mock and production are consistent.
//  Story W5.3 Task 5.1.
//

import Testing
@testable import BATbern_watch_Watch_App

@Suite("BatteryMonitor — isLowBattery threshold logic")
struct BatteryMonitorTests {

    // MARK: - AC3: Low battery threshold (< 20%)

    @Test("isLowBattery is true when batteryLevel is 0.15 (15%, below threshold)")
    func isLowBattery_trueWhenLevel015() {
        let monitor = MockBatteryMonitor(batteryLevel: 0.15)
        #expect(monitor.isLowBattery == true)
    }

    @Test("isLowBattery is false when batteryLevel is 0.50 (50%, above threshold)")
    func isLowBattery_falseWhenLevel050() {
        let monitor = MockBatteryMonitor(batteryLevel: 0.50)
        #expect(monitor.isLowBattery == false)
    }

    @Test("isLowBattery is false when batteryLevel is exactly 0.20 (boundary: not low)")
    func isLowBattery_falseAtExactThreshold() {
        let monitor = MockBatteryMonitor(batteryLevel: 0.20)
        #expect(monitor.isLowBattery == false)
    }

    @Test("isLowBattery is false when batteryLevel is -1.0 (unknown — fail-safe: do not reduce polling)")
    func isLowBattery_falseWhenUnknown() {
        // Simulator returns -1.0 — we must NOT reduce polling when level is unknown
        let monitor = MockBatteryMonitor(batteryLevel: -1.0)
        #expect(monitor.isLowBattery == false)
    }

    @Test("isLowBattery is true at minimum positive level (1%)")
    func isLowBattery_trueAtOnePercent() {
        let monitor = MockBatteryMonitor(batteryLevel: 0.01)
        #expect(monitor.isLowBattery == true)
    }

    @Test("isLowBattery is false at full charge (1.0)")
    func isLowBattery_falseAtFullCharge() {
        let monitor = MockBatteryMonitor(batteryLevel: 1.0)
        #expect(monitor.isLowBattery == false)
    }
}
