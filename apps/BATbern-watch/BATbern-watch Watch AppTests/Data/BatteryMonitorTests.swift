//
//  BatteryMonitorTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for BatteryMonitor.isLowBatteryLevel(_:) — the shared production threshold
//  function used by both BatteryMonitor.isLowBattery and MockBatteryMonitor.init.
//  Testing the static function directly validates the production threshold without
//  requiring WKInterfaceDevice instantiation.
//  Story W5.3 Task 5.1.
//

import Testing
@testable import BATbern_watch_Watch_App

@Suite("BatteryMonitor — isLowBatteryLevel threshold logic")
struct BatteryMonitorTests {

    // MARK: - AC3: Low battery threshold (< 20%)

    @Test("isLowBatteryLevel is true when level is 0.15 (15%, below threshold)")
    func isLowBatteryLevel_trueWhenLevel015() {
        #expect(BatteryMonitor.isLowBatteryLevel(0.15) == true)
    }

    @Test("isLowBatteryLevel is false when level is 0.50 (50%, above threshold)")
    func isLowBatteryLevel_falseWhenLevel050() {
        #expect(BatteryMonitor.isLowBatteryLevel(0.50) == false)
    }

    @Test("isLowBatteryLevel is false when level is exactly 0.20 (boundary: not low)")
    func isLowBatteryLevel_falseAtExactThreshold() {
        #expect(BatteryMonitor.isLowBatteryLevel(0.20) == false)
    }

    @Test("isLowBatteryLevel is false when level is -1.0 (unknown — fail-safe: do not reduce polling)")
    func isLowBatteryLevel_falseWhenUnknown() {
        // Simulator returns -1.0 — must NOT reduce polling when level is unknown
        #expect(BatteryMonitor.isLowBatteryLevel(-1.0) == false)
    }

    @Test("isLowBatteryLevel is true at minimum positive level (1%)")
    func isLowBatteryLevel_trueAtOnePercent() {
        #expect(BatteryMonitor.isLowBatteryLevel(0.01) == true)
    }

    @Test("isLowBatteryLevel is true at 0.0 (completely dead battery — still known, still low)")
    func isLowBatteryLevel_trueAtZero() {
        // 0.0 is a valid reading (not -1.0), so isLowBattery must be true
        #expect(BatteryMonitor.isLowBatteryLevel(0.0) == true)
    }

    @Test("isLowBatteryLevel is false at full charge (1.0)")
    func isLowBatteryLevel_falseAtFullCharge() {
        #expect(BatteryMonitor.isLowBatteryLevel(1.0) == false)
    }

    // MARK: - Mock consistency

    @Test("MockBatteryMonitor uses the same threshold as BatteryMonitor.isLowBatteryLevel")
    func mockBatteryMonitor_usesProductionThreshold() {
        // Verify mock delegates to production function (no duplicated logic)
        let belowThreshold = MockBatteryMonitor(batteryLevel: 0.15)
        let atThreshold    = MockBatteryMonitor(batteryLevel: 0.20)
        let aboveThreshold = MockBatteryMonitor(batteryLevel: 0.50)
        let unknown        = MockBatteryMonitor(batteryLevel: -1.0)

        #expect(belowThreshold.isLowBattery == BatteryMonitor.isLowBatteryLevel(0.15))
        #expect(atThreshold.isLowBattery    == BatteryMonitor.isLowBatteryLevel(0.20))
        #expect(aboveThreshold.isLowBattery == BatteryMonitor.isLowBatteryLevel(0.50))
        #expect(unknown.isLowBattery        == BatteryMonitor.isLowBatteryLevel(-1.0))
    }
}
