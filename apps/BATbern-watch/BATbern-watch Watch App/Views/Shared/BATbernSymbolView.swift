//
//  BATbernSymbolView.swift
//  BATbern-watch Watch App
//
//  Reusable BATbern symbol mark component.
//  Displays the official BATbern arrows logo (extracted from web-frontend/public/BATbern_arrows_only.svg)
//  Source: docs/watch-app/ux-design-specification.md#Brand-Assets-for-Watch
//

import SwiftUI

struct BATbernSymbolView: View {
    let size: CGFloat
    let color: Color

    init(size: CGFloat = 20, color: Color = Color(hex: "#2C5F7C") ?? .blue) {
        self.size = size
        self.color = color
    }

    var body: some View {
        // Official BATbern logo (arrows + text) from Assets.xcassets/BATbernLogo.imageset
        Image("BATbernLogo")
            .resizable()
            .renderingMode(.template)  // Apply color tint
            .aspectRatio(contentMode: .fit)
            .frame(width: size, height: size)  // Square aspect ratio: 200x200
            .foregroundStyle(color)
    }
}

// MARK: - Color Extension for Hex Support

extension Color {
    init?(hex: String) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")

        var rgb: UInt64 = 0

        guard Scanner(string: hexSanitized).scanHexInt64(&rgb) else {
            return nil
        }

        let r = Double((rgb & 0xFF0000) >> 16) / 255.0
        let g = Double((rgb & 0x00FF00) >> 8) / 255.0
        let b = Double(rgb & 0x0000FF) / 255.0

        self.init(red: r, green: g, blue: b)
    }
}

#Preview {
    VStack(spacing: 20) {
        BATbernSymbolView(size: 20)
        BATbernSymbolView(size: 32)
        BATbernSymbolView(size: 48, color: .white)
    }
    .padding()
    .background(Color.black)
}
