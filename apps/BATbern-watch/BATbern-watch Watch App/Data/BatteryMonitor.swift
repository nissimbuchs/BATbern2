//
//  BatteryMonitor.swift
//  BATbern-watch Watch App
//
//  WKInterfaceDevice-backed implementation of BatteryMonitorProtocol.
//  Enables battery monitoring on init and exposes level + low-battery threshold.
//  Fail-safe: `isLowBattery` returns false when level is unknown (-1.0, e.g., Simulator).
//  Story W5.3 Task 3.1-3.5.
//
//  Note: Must be created on the main thread — `WKInterfaceDevice.current()` requires it.
//  In practice, `EventDataController` (@MainActor) always creates the default instance.
//

import Foundation
import WatchKit

/// `WKInterfaceDevice`-backed battery monitor.
/// Battery level is read on-demand by `EventDataController.startPeriodicRefresh()` on each tick —
/// no internal timer is needed here.
final class BatteryMonitor: BatteryMonitorProtocol {

    init() {
        WKInterfaceDevice.current().isBatteryMonitoringEnabled = true
    }

    // MARK: - BatteryMonitorProtocol

    var batteryLevel: Float {
        WKInterfaceDevice.current().batteryLevel
    }

    var isLowBattery: Bool {
        BatteryMonitor.isLowBatteryLevel(batteryLevel)
    }

    // MARK: - Shared Threshold Logic

    /// Shared threshold computation used by both `BatteryMonitor.isLowBattery` and
    /// `MockBatteryMonitor.init` — keeps production and mock logic in sync,
    /// and allows direct unit testing without instantiating `WKInterfaceDevice`.
    ///
    /// Returns `true` when `level` is known (≥ 0.0) and below 20%.
    /// Returns `false` when `level` is -1.0 (unknown / Simulator) — fail-safe:
    /// do NOT reduce polling when battery state is unknown.
    static func isLowBatteryLevel(_ level: Float) -> Bool {
        level >= 0 && level < 0.20
    }
}
