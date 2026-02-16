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
    @State private var companyLogoUrl: String?

    init(speaker: CachedSpeaker, size: CGFloat = 40) {
        self.speaker = speaker
        self.size = size
    }

    var body: some View {
        VStack(spacing: 4) {
            // Portrait + Company Logo Row
            HStack(spacing: 4) {
                // Circular portrait with AsyncImage from CDN
                portraitImage
                    .frame(width: size, height: size)
                    .clipShape(Circle())

                // Company logo (if available)
                if let logoUrl = companyLogoUrl, let url = URL(string: logoUrl) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(width: size * 0.7, height: size * 0.7)  // Larger logo (70% of portrait size)
                        case .failure, .empty:
                            EmptyView()
                        @unknown default:
                            EmptyView()
                        }
                    }
                }
            }

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
        .frame(minWidth: size + 40)  // Minimum width, allows expansion for single speaker
        .task {
            // Fetch company logo if company name exists
            if let companyName = speaker.company {
                await loadCompanyLogo(companyName: companyName)
            }
        }
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

    // MARK: - Company Logo Loading

    /// Fetch company logo URL from company-user-management API
    /// Replicates web frontend pattern: GET /api/v1/companies/{companyName}?expand=logo
    private func loadCompanyLogo(companyName: String) async {
        // URL encode company name (may contain spaces or special characters)
        guard let encodedCompanyName = companyName.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
              let baseUrl = URL(string: "https://api.staging.batbern.ch") else { return }

        let endpoint = baseUrl.appendingPathComponent("/api/v1/companies/\(encodedCompanyName)")
        var components = URLComponents(url: endpoint, resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "expand", value: "logo")]

        guard let url = components?.url else { return }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let company = try JSONDecoder().decode(CompanyResponse.self, from: data)
            companyLogoUrl = company.logo?.url
        } catch {
            // Silently fail - logo is optional
            print("Failed to load company logo for \(companyName): \(error)")
        }
    }
}

// MARK: - Company API Response Types

/// Minimal company response for logo fetching
private struct CompanyResponse: Codable {
    let logo: CompanyLogo?
}

private struct CompanyLogo: Codable {
    let url: String
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
