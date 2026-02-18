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
    let portraitCache: PortraitCache

    @State private var portraitData: Data?
    @State private var logoData: Data?

    init(speaker: CachedSpeaker, portraitCache: PortraitCache = .shared) {
        self.speaker = speaker
        self.portraitCache = portraitCache
    }

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
                // Large Portrait (circular) — PortraitCache backed (AC#4). Size adapts to
                // device: 60pt (40mm SE) / 70pt (44mm SE, 41mm S9) / 80pt (45mm S9, Ultra).
                portraitImage
                    .frame(width: BATbernWatchStyle.Spacing.bioPortraitSize,
                           height: BATbernWatchStyle.Spacing.bioPortraitSize)
                    .clipShape(Circle())

                // Speaker Name
                Text(speaker.fullName)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)

                // Company: logo if available, else company name as alt
                if let company = speaker.company {
                    if let data = logoData, let uiImage = UIImage(data: data) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(height: 20)
                            .accessibilityLabel(company)
                    } else {
                        Text(company)
                            .font(.system(size: 13))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                    }
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
        .task {
            await loadPortrait()
            if let company = speaker.company {
                await loadLogo(companyName: company)
            }
        }
    }

    // MARK: - Portrait Image (AC#4 — PortraitCache backed, no duplicate network request)

    @ViewBuilder
    private var portraitImage: some View {
        if let data = portraitData, let uiImage = UIImage(data: data) {
            Image(uiImage: uiImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else {
            placeholderPortrait
        }
    }

    private func loadPortrait() async {
        guard let urlString = speaker.profilePictureUrl, let url = URL(string: urlString) else { return }
        if let cached = portraitCache.getCachedPortrait(url: url) {
            portraitData = cached
            return
        }
        if let downloaded = try? await portraitCache.downloadAndCache(url: url) {
            portraitData = downloaded
        }
    }

    private func loadLogo(companyName: String) async {
        if let cached = portraitCache.getLogoForCompany(companyName) {
            logoData = cached
            return
        }
        do {
            try await portraitCache.downloadAndCacheLogo(companyName: companyName)
            logoData = portraitCache.getLogoForCompany(companyName)
        } catch {
            // Silently fail — logo is optional
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
