//
//  AbstractDetailView.swift
//  BATbern-watch Watch App
//
//  Session abstract detail screen (P3).
//  Displays session title, full description text, and time slot.
//  Source: W1.3 AC#1
//

import SwiftUI

struct AbstractDetailView: View {
    let session: CachedSession

    /// Helper to check if abstract is empty or whitespace-only
    private var hasValidAbstract: Bool {
        guard let abstract = session.abstract,
              !abstract.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }
        return true
    }

    /// Formatted time slot string (e.g., "18:00 – 18:45")
    private var timeSlotString: String {
        guard let startTime = session.startTime,
              let endTime = session.endTime else {
            return ""
        }

        let startStr = SwissDateFormatter.formatEventTime(startTime)
        let endStr = SwissDateFormatter.formatEventTime(endTime)
        return "\(startStr) – \(endStr)"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // Session Title
                Text(session.title)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .lineLimit(nil)  // No truncation
                    .fixedSize(horizontal: false, vertical: true)

                // Divider
                Divider()

                // Abstract Text or Fallback
                if hasValidAbstract {
                    Text(session.abstract!)
                        .font(.system(size: 13, weight: .regular))
                        .lineLimit(nil)  // No truncation, Crown-scrollable
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    Text(NSLocalizedString("session.no_description", comment: "No description available"))
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(.secondary)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 20)

                // Time Slot (bottom)
                if !timeSlotString.isEmpty {
                    Text(timeSlotString)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
        }
        .navigationTitle("Abstract")  // System back button shows automatically
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview("With Abstract") {
    NavigationStack {
        AbstractDetailView(
            session: CachedSession(
                sessionSlug: "cloud-security",
                title: "Cloud Native Security in 2026",
                abstract: "Microservices gone wrong: lessons from 3 years of production failures and how we fixed them. This talk covers real-world security incidents, architectural mistakes, and the patterns we discovered for building resilient distributed systems.",
                sessionType: .presentation,
                startTime: Date(),
                endTime: Date().addingTimeInterval(45 * 60)
            )
        )
    }
}

#Preview("Long Abstract") {
    NavigationStack {
        AbstractDetailView(
            session: CachedSession(
                sessionSlug: "long-talk",
                title: "The Evolution of Microservices Architecture",
                abstract: String(repeating: "This is a long abstract that demonstrates Crown scrolling functionality on watchOS. ", count: 20),
                sessionType: .presentation,
                startTime: Date(),
                endTime: Date().addingTimeInterval(45 * 60)
            )
        )
    }
}

#Preview("No Abstract") {
    NavigationStack {
        AbstractDetailView(
            session: CachedSession(
                sessionSlug: "no-desc",
                title: "Session Without Description",
                abstract: nil,
                sessionType: .presentation,
                startTime: Date(),
                endTime: Date().addingTimeInterval(30 * 60)
            )
        )
    }
}

#Preview("Empty Abstract") {
    NavigationStack {
        AbstractDetailView(
            session: CachedSession(
                sessionSlug: "empty",
                title: "Session With Empty String",
                abstract: "   \n\t  ",
                sessionType: .presentation,
                startTime: Date(),
                endTime: Date().addingTimeInterval(30 * 60)
            )
        )
    }
}
