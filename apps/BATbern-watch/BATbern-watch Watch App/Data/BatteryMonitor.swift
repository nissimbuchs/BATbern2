//
//  BatteryMonitor.swift
//  BATbern-watch Watch App
//
//  WKInterfaceDevice-backed implementation of BatteryMonitorProtocol.
//  Enables battery monitoring on init and exposes level + low-battery threshold.
//  Fail-safe: `isLowBattery` returns false when level is unknown (-1.0, e.g., Simulator).
//  Story W5.3 Task 3.1-3.5.
//

import Foundation
import WatchKit
import OSLog

private let logger = Logger(subsystem: "ch.batbern.watch", category: "BatteryMonitor")

/// `WKInterfaceDevice`-backed battery monitor.
/// Uses a 60-second polling timer instead of `NotificationCenter` to avoid excessive
/// callback frequency — battery level is checked on each periodic refresh tick anyway.
final class BatteryMonitor: BatteryMonitorProtocol {

    private var checkTimer: Timer?

    init() {
        WKInterfaceDevice.current().isBatteryMonitoringEnabled = true
        // Check every 60s to keep the reading current; avoids excessive WKInterfaceDevice polling.
        checkTimer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            guard let self else { return }
            logger.debug("Battery level: \(Int(self.batteryLevel * 100))%, isLowBattery: \(self.isLowBattery)")
        }
    }

    deinit {
        checkTimer?.invalidate()
    }

    // MARK: - BatteryMonitorProtocol

    var batteryLevel: Float {
        WKInterfaceDevice.current().batteryLevel
    }

    var isLowBattery: Bool {
        let level = batteryLevel
        return level >= 0 && level < 0.20
    }
}
