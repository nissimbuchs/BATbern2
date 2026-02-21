//
//  WatchNotificationService.swift
//  BATbern-watch Watch App
//
//  Schedules local UNUserNotification alerts alongside haptic patterns so
//  organizers see a banner on wrist raise explaining why the watch buzzed.
//
//  Triggered by LiveCountdownViewModel when HapticScheduler fires a threshold alert.
//  Authorization is requested once when the timer starts (onAppear of organizer zone).
//  No sound option requested — WatchHapticService already handles the physical alert.
//

import UserNotifications
import OSLog

private let logger = Logger(subsystem: "ch.batbern.watch", category: "WatchNotificationService")

/// Posts local notifications for haptic threshold events.
///
/// Each notification is identified by a stable string (e.g. "batbern-5min") so that
/// re-entrances (e.g. timer firing twice before the notification clears) replace rather
/// than stack. `cancelAll()` clears pending and delivered notifications when the
/// timer stops so stale alerts do not linger after the session ends.
///
/// Implements `UNUserNotificationCenterDelegate` to opt in to foreground banner delivery.
/// Without this delegate, watchOS (and iOS) silently drop `trigger: nil` notifications
/// while the app is in the foreground — the permission dialog appears but no banners show.
final class WatchNotificationService: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {

    // MARK: - Init

    override init() {
        super.init()
        // Register as delegate before any notification is posted.
        // UNUserNotificationCenter holds a weak reference — this instance is kept alive
        // as a stored property on LiveCountdownViewModel for the duration of the session.
        UNUserNotificationCenter.current().delegate = self
    }

    // MARK: - UNUserNotificationCenterDelegate

    /// Allow banner presentation while the app is in the foreground.
    /// Without this, watchOS drops all `trigger: nil` notifications when the app is active.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner])
    }

    // MARK: - Public API

    /// Request `.alert` authorization once per app install.
    /// No-op if already granted or denied. Safe to call every time the timer starts.
    func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert]) { granted, error in
            if let error {
                logger.error("Notification auth failed: \(error.localizedDescription, privacy: .public)")
            } else {
                logger.info("Notification permission: \(granted ? "granted" : "denied", privacy: .public)")
            }
        }
    }

    /// Post an immediate local notification (trigger: nil = deliver now).
    /// Replaces any previously delivered notification with the same identifier.
    func post(title: String, subtitle: String, identifier: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.subtitle = subtitle
        // No sound: WatchHapticService handles the physical alert.
        // No trigger: nil delivers immediately on the next run-loop cycle.
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request) { error in
            if let error {
                logger.error("Failed to post notification '\(identifier, privacy: .public)': \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    /// Remove all pending and delivered BATbern notifications.
    /// Called when the timer stops so stale alerts do not appear after the event ends.
    func cancelAll() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
    }
}
