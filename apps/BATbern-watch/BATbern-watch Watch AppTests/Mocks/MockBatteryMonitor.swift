//
//  MockBatteryMonitor.swift
//  BATbern-watch Watch AppTests
//
//  Controllable battery monitor for deterministic tests.
//  When only `batteryLevel` is provided, `isLowBattery` is computed from it
//  (mirrors production logic). When `isLowBattery` is provided explicitly,
//  it overrides the computed value — useful for EventDataController tests that
//  only care about the resulting polling interval.
//  Story W5.3 Task 6.1-6.3.
//

import Foundation
@testable import BATbern_watch_Watch_App

/// Controllable battery monitor that mirrors `BatteryMonitor`'s isLowBattery logic.
/// - `MockBatteryMonitor(batteryLevel: 0.15)` → isLowBattery computed as `true`
/// - `MockBatteryMonitor(isLowBattery: true)` → isLowBattery set directly
final class MockBatteryMonitor: BatteryMonitorProtocol, @unchecked Sendable {

    var batteryLevel: Float
    var isLowBattery: Bool

    /// - Parameters:
    ///   - batteryLevel: Simulated battery level (0.0–1.0, or -1.0 for unknown).
    ///   - isLowBattery: Direct override. When nil, computed from `batteryLevel`
    ///     using the same threshold logic as `BatteryMonitor.isLowBattery`.
    init(batteryLevel: Float = 1.0, isLowBattery: Bool? = nil) {
        self.batteryLevel = batteryLevel
        self.isLowBattery = isLowBattery ?? (batteryLevel >= 0 && batteryLevel < 0.20)
    }
}
