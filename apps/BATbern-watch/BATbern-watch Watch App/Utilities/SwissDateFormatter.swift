//
//  SwissDateFormatter.swift
//  BATbern-watch Watch App
//
//  Swiss German locale date and time formatting utilities.
//  Uses de_CH locale for proper Swiss German formatting.
//

import Foundation

/// Swiss German date and time formatting utilities
enum SwissDateFormatter {

    /// Swiss German locale (German - Switzerland)
    static let swissLocale = Locale(identifier: "de_CH")

    /// Date formatter for event dates (e.g., "15. Februar 2026")
    static let eventDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = swissLocale
        formatter.dateStyle = .long
        formatter.timeStyle = .none
        return formatter
    }()

    /// Time formatter for event times (e.g., "14:00")
    static let eventTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = swissLocale
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    /// Short date formatter (e.g., "15.02.2026")
    static let shortDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = swissLocale
        formatter.dateStyle = .short
        formatter.timeStyle = .none
        return formatter
    }()

    /// Full date and time formatter (e.g., "15. Februar 2026, 14:00")
    static let fullDateTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = swissLocale
        formatter.dateStyle = .long
        formatter.timeStyle = .short
        return formatter
    }()

    /// Format a date for event display
    static func formatEventDate(_ date: Date) -> String {
        eventDateFormatter.string(from: date)
    }

    /// Format a time for event display
    static func formatEventTime(_ date: Date) -> String {
        eventTimeFormatter.string(from: date)
    }

    /// Format a date and time together
    static func formatEventDateTime(_ date: Date) -> String {
        fullDateTimeFormatter.string(from: date)
    }

    /// Parse time string (HH:mm) and return formatted Swiss time
    static func formatTimeString(_ timeString: String) -> String {
        // If already in correct format, return as-is
        if timeString.range(of: #"^\d{2}:\d{2}$"#, options: .regularExpression) != nil {
            return timeString
        }
        // Otherwise, attempt to parse and reformat
        let inputFormatter = DateFormatter()
        inputFormatter.dateFormat = "HH:mm:ss"
        if let date = inputFormatter.date(from: timeString) {
            return eventTimeFormatter.string(from: date)
        }
        return timeString  // Return original if parsing fails
    }
}
