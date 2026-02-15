//
//  SessionCardView.swift
//  BATbern-watch Watch App
//
//  Session card (P2) showing session details with progressive publishing support.
//  Source: docs/watch-app/ux-design-specification.md#Session-Card-Pages
//  Story: W1.2 - Session Card Browsing (AC#2, AC#3, AC#6)
//

import SwiftUI

struct SessionCardView: View {
    let session: CachedSession
    let phase: String?  // TOPIC, SPEAKERS, or AGENDA

    // MARK: - Computed Properties

    private var isBreakSession: Bool {
        guard let type = session.sessionType else { return false }
        return type == .breakTime || type == .lunch || type == .networking
    }

    private var showSpeakers: Bool {
        phase == "SPEAKERS" || phase == "AGENDA"
    }

    private var showTimeSlots: Bool {
        phase == "SPEAKERS" || phase == "AGENDA"
    }

    private var titleTapsEnabled: Bool {
        phase == "AGENDA"
    }

    private var speakerTapsEnabled: Bool {
        phase == "SPEAKERS" || phase == "AGENDA"
    }

    private var breakIcon: String {
        guard let type = session.sessionType else { return "questionmark.circle" }
        switch type {
        case .breakTime, .lunch:
            return "cup.and.saucer.fill"
        case .networking:
            return "person.2.fill"
        default:
            return "questionmark.circle"
        }
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            if isBreakSession {
                breakCardLayout
            } else {
                presentationCardLayout
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }

    // MARK: - Presentation Card Layout (AC#2)

    @ViewBuilder
    private var presentationCardLayout: some View {
        VStack(spacing: 12) {
            // Time slot (top, secondary color)
            if showTimeSlots, let startTime = session.startTime, let endTime = session.endTime {
                Text("\(SwissDateFormatter.formatEventTime(startTime)) – \(SwissDateFormatter.formatEventTime(endTime))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .padding(.top, 8)
            }

            Spacer()

            // Title (blue-tinted, tappable area for W1.3)
            titleArea

            Spacer()

            // Speaker area (bottom)
            if showSpeakers && !session.speakers.isEmpty {
                speakerArea
                    .padding(.bottom, 8)
            }
        }
        .padding(.horizontal, 12)
    }

    // MARK: - Break Card Layout (AC#3)

    @ViewBuilder
    private var breakCardLayout: some View {
        VStack(spacing: 8) {
            // Time slot
            if showTimeSlots, let startTime = session.startTime, let endTime = session.endTime {
                Text("\(SwissDateFormatter.formatEventTime(startTime)) – \(SwissDateFormatter.formatEventTime(endTime))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .padding(.top, 8)
            }

            Spacer()

            // SF Symbol icon (AC#3, AC#4)
            Image(systemName: breakIcon)
                .font(.system(size: 36))
                .foregroundStyle(.secondary)

            // Session title
            Text(session.title)
                .font(.system(size: 14, design: .rounded))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .lineLimit(3)

            Spacer()
        }
        .padding(.horizontal, 12)
    }

    // MARK: - Title Area (AC#2, AC#6, W1.3 AC#1, AC#5, AC#6)

    @ViewBuilder
    private var titleArea: some View {
        if titleTapsEnabled {
            // AGENDA phase: Title is NavigationLink to AbstractDetailView (P3)
            NavigationLink {
                AbstractDetailView(session: session)
            } label: {
                titleText
            }
            .buttonStyle(.plain)  // Remove default button styling
        } else {
            // TOPIC/SPEAKERS phase: Title is not tappable
            titleText
        }
    }

    @ViewBuilder
    private var titleText: some View {
        Text(session.title)
            .font(.system(size: 16, weight: .medium, design: .rounded))
            .foregroundStyle(Color(hex: "#2C5F7C") ?? .blue)  // BATbern Blue tint
            .multilineTextAlignment(.center)
            .lineLimit(nil)  // Allow unlimited lines for proper wrapping
            .fixedSize(horizontal: false, vertical: true)  // Enable text wrapping
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())  // Make entire area tappable
    }

    // MARK: - Speaker Area (AC#2, AC#5, W1.3 AC#2, AC#3, AC#5, AC#6)

    @ViewBuilder
    private var speakerArea: some View {
        if speakerTapsEnabled {
            // SPEAKERS/AGENDA phase: Speaker area is tappable
            if session.speakers.count == 1 {
                // Single speaker: NavigationLink to SpeakerBioView (P4)
                NavigationLink {
                    SpeakerBioView(speaker: session.speakers[0])
                } label: {
                    singleSpeakerLayout(session.speakers[0])
                }
                .buttonStyle(.plain)
            } else if session.speakers.count >= 2 {
                // 2+ speakers: NavigationLink to MultiSpeakerGridView (P5)
                NavigationLink {
                    MultiSpeakerGridView(speakers: session.speakers)
                } label: {
                    multiSpeakerLayout
                }
                .buttonStyle(.plain)
            }
        } else {
            // TOPIC phase: Speaker area not tappable
            if session.speakers.count == 1 {
                singleSpeakerLayout(session.speakers[0])
            } else if session.speakers.count >= 2 {
                multiSpeakerLayout
            }
        }
    }

    // MARK: - Multi-Speaker Layout (reusable for tappable and non-tappable states)

    @ViewBuilder
    private var multiSpeakerLayout: some View {
        if session.speakers.count == 2 {
            // Two speakers: side by side
            HStack(spacing: 12) {
                ForEach(Array(session.speakers.prefix(2)), id: \.username) { speaker in
                    speakerPortrait(speaker)
                }
            }
        } else if session.speakers.count >= 3 {
            // 3+ speakers: 2-column grid, max 3 shown + badge
            VStack(spacing: 8) {
                HStack(spacing: 12) {
                    ForEach(Array(session.speakers.prefix(2)), id: \.username) { speaker in
                        speakerPortrait(speaker)
                    }
                }

                if session.speakers.count == 3 {
                    speakerPortrait(session.speakers[2])
                } else {
                    // 4+ speakers: show third + badge
                    HStack(spacing: 12) {
                        speakerPortrait(session.speakers[2])

                        VStack(spacing: 4) {
                            Text("+\(session.speakers.count - 3)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(NSLocalizedString("session.speakers.more", comment: "+N more speakers"))
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        .frame(width: 40)
                    }
                }
            }
        }
    }

    // MARK: - Speaker Portrait Layout

    @ViewBuilder
    private func singleSpeakerLayout(_ speaker: CachedSpeaker) -> some View {
        SpeakerPortraitView(speaker: speaker, size: 50)  // Larger size for single speaker
            .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func speakerPortrait(_ speaker: CachedSpeaker) -> some View {
        SpeakerPortraitView(speaker: speaker, size: 40)
    }
}

// MARK: - Previews

#Preview("Presentation - 1 Speaker") {
    let speaker = CachedSpeaker(
        username: "anna-test",
        firstName: "Anna",
        lastName: "Schmidt",
        company: "ACME Corp"
    )

    let session = CachedSession(
        sessionSlug: "cloud-security",
        title: "Cloud Native Security in 2026",
        abstract: "Test abstract",
        sessionType: .presentation,
        startTime: Date(),
        endTime: Date().addingTimeInterval(45 * 60),
        speakers: [speaker]
    )

    SessionCardView(session: session, phase: "AGENDA")
}

#Preview("Presentation - 2 Speakers") {
    let speakers = [
        CachedSpeaker(
            username: "anna-test",
            firstName: "Anna",
            lastName: "Schmidt",
            company: "ACME Corp"
        ),
        CachedSpeaker(
            username: "tom-test",
            firstName: "Tom",
            lastName: "Müller",
            company: "Tech GmbH"
        )
    ]

    let session = CachedSession(
        sessionSlug: "cloud-security",
        title: "Cloud Native Security",
        abstract: "Test abstract",
        sessionType: .keynote,
        startTime: Date(),
        endTime: Date().addingTimeInterval(45 * 60),
        speakers: speakers
    )

    SessionCardView(session: session, phase: "AGENDA")
}

#Preview("Presentation - 4+ Speakers") {
    let speakers = [
        CachedSpeaker(username: "s1", firstName: "Anna", lastName: "Schmidt", company: "ACME"),
        CachedSpeaker(username: "s2", firstName: "Tom", lastName: "Müller", company: "Tech GmbH"),
        CachedSpeaker(username: "s3", firstName: "Sara", lastName: "Weber", company: "Startup AG"),
        CachedSpeaker(username: "s4", firstName: "Max", lastName: "Fischer", company: "Corp"),
        CachedSpeaker(username: "s5", firstName: "Lisa", lastName: "Klein", company: "Company")
    ]

    let session = CachedSession(
        sessionSlug: "panel",
        title: "Panel Discussion",
        sessionType: .panelDiscussion,
        startTime: Date(),
        endTime: Date().addingTimeInterval(60 * 60),
        speakers: speakers
    )

    SessionCardView(session: session, phase: "SPEAKERS")
}

#Preview("Break - Coffee") {
    let session = CachedSession(
        sessionSlug: "break-1",
        title: "Coffee Break",
        sessionType: .breakTime,
        startTime: Date(),
        endTime: Date().addingTimeInterval(20 * 60)
    )

    SessionCardView(session: session, phase: "AGENDA")
}

#Preview("Break - Lunch") {
    let session = CachedSession(
        sessionSlug: "lunch-1",
        title: "Lunch",
        sessionType: .lunch,
        startTime: Date(),
        endTime: Date().addingTimeInterval(60 * 60)
    )

    SessionCardView(session: session, phase: "AGENDA")
}

#Preview("Break - Networking") {
    let session = CachedSession(
        sessionSlug: "networking-1",
        title: "Networking Session",
        sessionType: .networking,
        startTime: Date(),
        endTime: Date().addingTimeInterval(30 * 60)
    )

    SessionCardView(session: session, phase: "AGENDA")
}

#Preview("Progressive - TOPIC Phase") {
    let speaker = CachedSpeaker(
        username: "anna-test",
        firstName: "Anna",
        lastName: "Schmidt",
        company: "ACME Corp"
    )

    let session = CachedSession(
        sessionSlug: "test",
        title: "Cloud Security",
        sessionType: .presentation,
        startTime: Date(),
        endTime: Date().addingTimeInterval(45 * 60),
        speakers: [speaker]
    )

    SessionCardView(session: session, phase: "TOPIC")
}
