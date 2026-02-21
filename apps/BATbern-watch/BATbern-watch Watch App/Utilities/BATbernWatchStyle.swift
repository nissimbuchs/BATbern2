//
//  BATbernWatchStyle.swift
//  BATbern-watch Watch App
//
//  Design system tokens: colors, typography, and spacing for all Watch views.
//  Single source of truth for visual constants (AC#8).
//  Source: W1.5 - UI Polish & Image Caching
//

import SwiftUI
import WatchKit

// MARK: - Screen Size Enum

/// Bucketed device size derived once from WKInterfaceDevice.screenBounds.width.
/// Covers all Apple Watch models supported by watchOS 11+:
///   small  — SE 40mm            (width < 164pt)
///   medium — SE 44mm, S9 41mm   (164 ≤ width < 190pt)
///   large  — S9 45mm, Ultra 49mm (width ≥ 190pt)
enum WatchScreenSize {
    case small
    case medium
    case large

    static var current: WatchScreenSize {
        let width = WKInterfaceDevice.current().screenBounds.width
        if width < 164 { return .small }
        if width < 190 { return .medium }
        return .large
    }
}

enum BATbernWatchStyle {

    // MARK: - Colors

    enum Colors {
        static let batbernBlue = Color(hex: "#3498DB") ?? .blue       // Primary brand
        static let batbernBlueDark = Color(hex: "#2874A6") ?? .blue   // Pressed states
        static let heroBg = Color.black                                // All screen backgrounds
        static let textPrimary = Color.white                           // Primary text
        static let textSecondary = Color.secondary                      // Secondary text
        static let textTertiary = Color(white: 1.0, opacity: 0.3)     // Tertiary / hints
    }

    // MARK: - Typography

    enum Typography {
        static let heroTitle = Font.system(.title3, design: .rounded, weight: .semibold)
        /// Session title font — scaled down on 40mm SE to prevent long German titles consuming
        /// the full screen height (13pt small / 15pt medium / 16pt large).
        static var sessionTitle: Font {
            switch WatchScreenSize.current {
            case .small:  return .system(size: 11, weight: .medium, design: .rounded)
            case .medium: return .system(size: 14, weight: .medium, design: .rounded)
            case .large:  return .system(size: 16, weight: .medium, design: .rounded)
            }
        }
        static let speakerName = Font.system(size: 11)
        static let companyName = Font.system(size: 9)
        static let caption = Font.caption2
        static let statusBar = Font.caption2
    }

    // MARK: - Spacing

    enum Spacing {
        /// Standard horizontal padding (8 / 10 / 12 pt).
        static var screenPadding: CGFloat {
            switch WatchScreenSize.current {
            case .small:  return 8
            case .medium: return 10
            case .large:  return 12
            }
        }

        /// Vertical inset below the system clock area (24 / 26 / 28 pt).
        static var statusBarTopPadding: CGFloat {
            switch WatchScreenSize.current {
            case .small:  return 24
            case .medium: return 26
            case .large:  return 28
            }
        }

        /// Session card time-slot top offset (24 / 26 / 28 pt).
        static var cardTopPadding: CGFloat {
            switch WatchScreenSize.current {
            case .small:  return 24
            case .medium: return 26
            case .large:  return 28
            }
        }

        /// Single-speaker portrait diameter (38 / 44 / 50 pt).
        static var portraitSize: CGFloat {
            switch WatchScreenSize.current {
            case .small:  return 38
            case .medium: return 44
            case .large:  return 50
            }
        }

        /// Multi-speaker grid portrait diameter (30 / 36 / 40 pt).
        static var portraitSizeSmall: CGFloat {
            switch WatchScreenSize.current {
            case .small:  return 30
            case .medium: return 36
            case .large:  return 40
            }
        }

        /// Live countdown progress-ring diameter (80 / 88 / 96 pt).
        static var countdownRingSize: CGFloat {
            switch WatchScreenSize.current {
            case .small:  return 80
            case .medium: return 88
            case .large:  return 96
            }
        }

        /// Speaker bio screen portrait diameter (60 / 70 / 80 pt).
        static var bioPortraitSize: CGFloat {
            switch WatchScreenSize.current {
            case .small:  return 60
            case .medium: return 70
            case .large:  return 80
            }
        }
    }
}
