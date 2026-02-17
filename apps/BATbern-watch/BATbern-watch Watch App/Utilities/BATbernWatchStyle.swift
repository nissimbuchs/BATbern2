//
//  BATbernWatchStyle.swift
//  BATbern-watch Watch App
//
//  Design system tokens: colors, typography, and spacing for all Watch views.
//  Single source of truth for visual constants (AC#8).
//  Source: W1.5 - UI Polish & Image Caching
//

import SwiftUI

enum BATbernWatchStyle {

    // MARK: - Colors

    enum Colors {
        static let batbernBlue = Color(hex: "#2C5F7C") ?? .blue       // Primary brand
        static let batbernBlueDark = Color(hex: "#1E4A61") ?? .blue   // Pressed states
        static let heroBg = Color.black                                // All screen backgrounds
        static let textPrimary = Color.white                           // Primary text
        static let textSecondary = Color(.secondaryLabel)              // Secondary text
        static let textTertiary = Color(.tertiaryLabel)                // Tertiary / hints
    }

    // MARK: - Typography

    enum Typography {
        static let heroTitle = Font.system(.title3, design: .rounded, weight: .semibold)
        static let sessionTitle = Font.system(size: 16, weight: .medium, design: .rounded)
        static let speakerName = Font.system(size: 11)
        static let companyName = Font.system(size: 9)
        static let caption = Font.caption2
        static let statusBar = Font.caption2
    }

    // MARK: - Spacing

    enum Spacing {
        static let screenPadding: CGFloat = 12       // Standard horizontal padding
        static let statusBarTopPadding: CGFloat = 28  // Below system clock area (~24pt + 4pt buffer)
        static let cardTopPadding: CGFloat = 28       // Session card time slot starts here
        static let portraitSize: CGFloat = 50         // Single speaker portrait
        static let portraitSizeSmall: CGFloat = 40    // Multi-speaker portrait
    }
}
