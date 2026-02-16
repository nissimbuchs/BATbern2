//
//  SpeakerBioView.swift
//  BATbern-watch Watch App
//
//  Speaker bio detail screen (P4/P6).
//  Displays large portrait, name, company with logo, and bio text.
//  Source: W1.3 AC#2, AC#4, AC#7
//

import SwiftUI

struct SpeakerBioView: View {
    let speaker: CachedSpeaker

    /// Helper to check if bio is empty or whitespace-only
    private var hasValidBio: Bool {
        guard let bio = speaker.bio,
              !bio.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }
        return true
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Large Portrait (~80pt, circular)
                portraitImage
                    .frame(width: 80, height: 80)
                    .clipShape(Circle())

                // Speaker Name
                Text(speaker.fullName)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)

                // Company Name + Logo (inline)
                if let company = speaker.company {
                    HStack(spacing: 6) {
                        Text(company)
                            .font(.system(size: 13))
                            .foregroundColor(.primary)

                        // Company logo (~20pt height, inline)
                        if let logoUrl = speaker.companyLogoUrl, let url = URL(string: logoUrl) {
                            AsyncImage(url: url) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(height: 20)
                                case .failure, .empty:
                                    EmptyView()  // No logo, just show company name
                                @unknown default:
                                    EmptyView()
                                }
                            }
                        }
                    }
                    .lineLimit(1)
                }

                // Divider
                Divider()

                // Bio Text or Fallback
                if hasValidBio {
                    Text(speaker.bio!)
                        .font(.system(size: 13, weight: .regular))
                        .lineLimit(nil)  // No truncation, Crown-scrollable
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    Text(NSLocalizedString("speaker.no_bio", comment: "No bio available"))
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(.secondary)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
        }
        .navigationTitle("Speaker")  // System back button shows automatically
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Portrait Image

    @ViewBuilder
    private var portraitImage: some View {
        if let profileUrl = speaker.profilePictureUrl, let url = URL(string: profileUrl) {
            // AsyncImage with CDN URL
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
                    .font(.system(size: 48))  // ~60% of 80pt
            )
    }
}

// MARK: - Previews

#Preview("With Bio and Company Logo") {
    NavigationStack {
        SpeakerBioView(
            speaker: CachedSpeaker(
                username: "anna-meier",
                firstName: "Anna",
                lastName: "Meier",
                company: "ACME Corp",
                companyLogoUrl: "https://cdn.batbern.ch/logos/acme.png",
                profilePictureUrl: "https://picsum.photos/160/160",
                bio: "Senior architect specializing in cloud native security and microservices architecture. Over 10 years of experience building distributed systems."
            )
        )
    }
}

#Preview("Long Bio") {
    NavigationStack {
        SpeakerBioView(
            speaker: CachedSpeaker(
                username: "tom-mueller",
                firstName: "Tom",
                lastName: "Müller",
                company: "Tech GmbH",
                profilePictureUrl: "https://picsum.photos/160/160",
                bio: String(repeating: "Expert in cloud architecture and distributed systems. Passionate about DevOps, Kubernetes, and platform engineering. ", count: 10)
            )
        )
    }
}

#Preview("No Bio") {
    NavigationStack {
        SpeakerBioView(
            speaker: CachedSpeaker(
                username: "sara-weber",
                firstName: "Sara",
                lastName: "Weber",
                company: "Startup AG",
                bio: nil
            )
        )
    }
}

#Preview("No Portrait") {
    NavigationStack {
        SpeakerBioView(
            speaker: CachedSpeaker(
                username: "max-fischer",
                firstName: "Max",
                lastName: "Fischer",
                company: "Innovation Labs",
                profilePictureUrl: nil,
                bio: "CTO and co-founder with a passion for innovation and team building."
            )
        )
    }
}

#Preview("No Company") {
    NavigationStack {
        SpeakerBioView(
            speaker: CachedSpeaker(
                username: "lea-schmid",
                firstName: "Lea",
                lastName: "Schmid",
                bio: "Independent consultant focusing on software architecture and team coaching."
            )
        )
    }
}

#Preview("Company Without Logo") {
    NavigationStack {
        SpeakerBioView(
            speaker: CachedSpeaker(
                username: "marco-rossi",
                firstName: "Marco",
                lastName: "Rossi",
                company: "FreelanceHub",
                companyLogoUrl: nil,
                bio: "Freelance software engineer with expertise in full-stack development."
            )
        )
    }
}
