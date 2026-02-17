//
//  SpeakerPortraitView.swift
//  BATbern-watch Watch App
//
//  Reusable circular portrait thumbnail for speakers.
//  Uses PortraitCache exclusively — no AsyncImage (AC#4, AC#5).
//  Source: W1.2 - Session Card Browsing (AC#5); W1.5 - UI Polish (AC#4, AC#5)
//

import SwiftUI

struct SpeakerPortraitView: View {
    let speaker: CachedSpeaker
    let size: CGFloat
    let portraitCache: PortraitCache

    @State private var portraitData: Data?
    @State private var logoData: Data?

    init(speaker: CachedSpeaker, size: CGFloat = 40, portraitCache: PortraitCache = .shared) {
        self.speaker = speaker
        self.size = size
        self.portraitCache = portraitCache
    }

    var body: some View {
        VStack(spacing: 4) {
            // Portrait + Company Logo Row
            HStack(spacing: 4) {
                // Circular portrait — PortraitCache backed (AC#4)
                portraitImage
                    .frame(width: size, height: size)
                    .clipShape(Circle())

                // Company logo — PortraitCache backed (AC#5)
                if let data = logoData, let uiImage = UIImage(data: data) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: size * 0.7, height: size * 0.7)
                }
            }

            // Speaker name
            Text(speaker.fullName)
                .font(BATbernWatchStyle.Typography.speakerName)
                .foregroundStyle(BATbernWatchStyle.Colors.textPrimary)
                .lineLimit(1)

            // Company name
            if let company = speaker.company {
                Text(company)
                    .font(BATbernWatchStyle.Typography.companyName)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .frame(minWidth: size + 40)
        .task {
            await loadPortrait()
            if let companyName = speaker.company {
                await loadLogo(companyName: companyName)
            }
        }
    }

    // MARK: - Portrait Image (AC#4)

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

    // MARK: - Cache-First Portrait Loading (AC#4)

    private func loadPortrait() async {
        guard let urlString = speaker.profilePictureUrl, let url = URL(string: urlString) else { return }

        // Cache-first: try local file first
        if let cached = portraitCache.getCachedPortrait(url: url) {
            portraitData = cached
            return
        }

        // Fallback: download and cache
        if let downloaded = try? await portraitCache.downloadAndCache(url: url) {
            portraitData = downloaded
        }
    }

    // MARK: - Cache-First Logo Loading (AC#5)

    private func loadLogo(companyName: String) async {
        // Cache-first: try local file first
        if let cached = portraitCache.getLogoForCompany(companyName) {
            logoData = cached
            return
        }

        // Fallback: fetch from company API and cache
        guard let encodedName = companyName.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
              var components = URLComponents(string: "https://api.staging.batbern.ch/api/v1/companies/\(encodedName)") else {
            return
        }
        components.queryItems = [URLQueryItem(name: "expand", value: "logo")]
        guard let url = components.url else { return }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let company = try JSONDecoder().decode(CompanyResponse.self, from: data)
            if let logoUrlString = company.logo?.url,
               let logoUrl = URL(string: logoUrlString) {
                let (rawLogoData, _) = try await URLSession.shared.data(from: logoUrl)
                portraitCache.saveLogo(companyName: companyName, data: rawLogoData)
                logoData = rawLogoData
            }
        } catch {
            // Silently fail — logo is optional
            print("SpeakerPortraitView: Logo load failed for \(companyName): \(error.localizedDescription)")
        }
    }
}

// MARK: - Company API Response Types

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
        profilePictureUrl: "https://picsum.photos/80/80"
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
