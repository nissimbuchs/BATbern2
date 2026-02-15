//
//  SpeakerPortraitView.swift
//  BATbern-watch Watch App
//
//  Reusable circular portrait thumbnail for speakers.
//  Source: W1.2 - Session Card Browsing (AC#5)
//

import SwiftUI

struct SpeakerPortraitView: View {
    let speaker: CachedSpeaker
    let size: CGFloat

    init(speaker: CachedSpeaker, size: CGFloat = 40) {
        self.speaker = speaker
        self.size = size
    }

    var body: some View {
        VStack(spacing: 4) {
            // Circular portrait (Task 7 will add AsyncImage from CDN)
            portraitImage
                .frame(width: size, height: size)
                .clipShape(Circle())

            // Speaker name
            Text(speaker.fullName)
                .font(.system(size: 11))
                .foregroundStyle(.white)
                .lineLimit(1)

            // Company name
            if let company = speaker.company {
                Text(company)
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .frame(width: size + 20)  // Extra width for text
    }

    // MARK: - Portrait Image (AC#5)

    @ViewBuilder
    private var portraitImage: some View {
        if let profileUrl = speaker.profilePictureUrl, let url = URL(string: profileUrl) {
            // AsyncImage with CDN URL (will use cache in Task 7)
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                case .failure, .empty:
                    placeholderPortrait
                @unknown default:
                    placeholderPortrait
                }
            }
        } else {
            placeholderPortrait
        }
    }

    // MARK: - Placeholder Portrait

    @ViewBuilder
    private var placeholderPortrait: some View {
        Circle()
            .fill(.secondary.opacity(0.3))
            .overlay(
                Image(systemName: "person.crop.circle.fill")
                    .foregroundStyle(.secondary)
                    .font(.system(size: size * 0.6))
            )
    }
}

// MARK: - Previews

#Preview("With Portrait URL") {
    let speaker = CachedSpeaker(
        username: "anna-test",
        firstName: "Anna",
        lastName: "Schmidt",
        company: "ACME Corp",
        profilePictureUrl: "https://picsum.photos/80/80"  // Sample image
    )

    SpeakerPortraitView(speaker: speaker, size: 40)
        .padding()
        .background(Color.black)
}

#Preview("Without Portrait") {
    let speaker = CachedSpeaker(
        username: "tom-test",
        firstName: "Tom",
        lastName: "Müller",
        company: "Tech GmbH"
    )

    SpeakerPortraitView(speaker: speaker, size: 40)
        .padding()
        .background(Color.black)
}

#Preview("No Company") {
    let speaker = CachedSpeaker(
        username: "sara-test",
        firstName: "Sara",
        lastName: "Weber"
    )

    SpeakerPortraitView(speaker: speaker, size: 40)
        .padding()
        .background(Color.black)
}

#Preview("Large Size") {
    let speaker = CachedSpeaker(
        username: "max-test",
        firstName: "Max",
        lastName: "Fischer",
        company: "Startup AG"
    )

    SpeakerPortraitView(speaker: speaker, size: 60)
        .padding()
        .background(Color.black)
}

#Preview("Grid Layout") {
    let speakers = [
        CachedSpeaker(username: "s1", firstName: "Anna", lastName: "Schmidt", company: "ACME"),
        CachedSpeaker(username: "s2", firstName: "Tom", lastName: "Müller", company: "Tech GmbH"),
        CachedSpeaker(username: "s3", firstName: "Sara", lastName: "Weber"),
        CachedSpeaker(username: "s4", firstName: "Max", lastName: "Fischer", company: "Startup")
    ]

    HStack(spacing: 8) {
        ForEach(speakers.prefix(2), id: \.username) { speaker in
            SpeakerPortraitView(speaker: speaker, size: 40)
        }
    }
    .padding()
    .background(Color.black)
}
