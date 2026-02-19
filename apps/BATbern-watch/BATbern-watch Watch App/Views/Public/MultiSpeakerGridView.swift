//
//  MultiSpeakerGridView.swift
//  BATbern-watch Watch App
//
//  Multi-speaker portrait grid (P5).
//  Displays 2-column grid of speaker portraits with navigation to individual bios.
//  Source: W1.3 AC#3
//

import SwiftUI

struct MultiSpeakerGridView: View {
    let speakers: [CachedSpeaker]

    // 2-column grid layout
    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // Header with speaker count (localized)
                Text(String(format: NSLocalizedString("speakers.count", comment: "Speakers (%d)"), speakers.count))
                    .font(.system(size: 13, weight: .medium))
                    .frame(maxWidth: .infinity, alignment: .center)

                // 2-column portrait grid
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(speakers, id: \.username) { speaker in
                        NavigationLink {
                            // Navigate to SpeakerBioView (P6)
                            SpeakerBioView(speaker: speaker)
                        } label: {
                            // Portrait cell — adapts to device size (30/36/40pt)
                            SpeakerPortraitView(speaker: speaker,
                                                size: BATbernWatchStyle.Spacing.portraitSizeSmall)
                        }
                        .buttonStyle(.plain)  // Remove default button styling
                    }
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
        }
        .navigationTitle("Speakers")  // System back button shows automatically
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Previews

#Preview("2 Speakers") {
    NavigationStack {
        MultiSpeakerGridView(
            speakers: [
                CachedSpeaker(
                    username: "anna-meier",
                    firstName: "Anna",
                    lastName: "Meier",
                    company: "ACME Corp",
                    profilePictureUrl: "https://picsum.photos/80/80?1"
                ),
                CachedSpeaker(
                    username: "tom-mueller",
                    firstName: "Tom",
                    lastName: "Müller",
                    company: "Tech GmbH",
                    profilePictureUrl: "https://picsum.photos/80/80?2"
                )
            ]
        )
    }
}

#Preview("3 Speakers") {
    NavigationStack {
        MultiSpeakerGridView(
            speakers: [
                CachedSpeaker(username: "s1", firstName: "Anna", lastName: "Meier", company: "ACME"),
                CachedSpeaker(username: "s2", firstName: "Tom", lastName: "Müller", company: "Tech GmbH"),
                CachedSpeaker(username: "s3", firstName: "Sara", lastName: "Weber", company: "Startup AG")
            ]
        )
    }
}

#Preview("5 Speakers") {
    NavigationStack {
        MultiSpeakerGridView(
            speakers: [
                CachedSpeaker(username: "s1", firstName: "Anna", lastName: "Meier", company: "ACME"),
                CachedSpeaker(username: "s2", firstName: "Tom", lastName: "Müller", company: "Tech GmbH"),
                CachedSpeaker(username: "s3", firstName: "Sara", lastName: "Weber", company: "Startup AG"),
                CachedSpeaker(username: "s4", firstName: "Max", lastName: "Fischer", company: "Innovation"),
                CachedSpeaker(username: "s5", firstName: "Lea", lastName: "Schmid", company: "Consulting")
            ]
        )
    }
}

#Preview("Many Speakers - Crown Scroll") {
    NavigationStack {
        MultiSpeakerGridView(
            speakers: (1...8).map { index in
                CachedSpeaker(
                    username: "speaker-\(index)",
                    firstName: "Speaker",
                    lastName: "\(index)",
                    company: "Company \(index)"
                )
            }
        )
    }
}

#Preview("Empty Array") {
    NavigationStack {
        MultiSpeakerGridView(speakers: [])
    }
}
