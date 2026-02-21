//
//  BatteryMonitorProtocol.swift
//  BATbern-watch Watch App
//
//  Injectable battery monitor for testable battery-adaptive behavior.
//  Production: `BatteryMonitor()`. Tests: `MockBatteryMonitor(isLowBattery:)`.
//  Story W5.3 Task 3.6.
//

import Foundation

/// Protocol for battery state observation.
/// `WKInterfaceDevice` is not injectable in tests — conforming types use this protocol
/// so tests can inject `MockBatteryMonitor` without touching real hardware.
protocol BatteryMonitorProtocol: AnyObject {
    /// Current battery level in the range 0.0–1.0, or -1.0 if unknown (e.g., Simulator).
    var batteryLevel: Float { get }

    /// True when battery level is known AND below 20%.
    /// Returns false when level is -1.0 (unknown / Simulator) — fail-safe:
    /// do NOT reduce polling when battery state is unknown.
    var isLowBattery: Bool { get }
}
