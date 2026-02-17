//
//  CachedSpeaker.swift
//  BATbern-watch Watch App
//
//  SwiftData model for locally cached speaker data.
//  Source: docs/watch-app/architecture.md#Data-Architecture
//

import Foundation
import SwiftData

@Model
class CachedSpeaker: @unchecked Sendable {
    var username: String
    var firstName: String
    var lastName: String
    var company: String?
    var companyLogoUrl: String?
    var profilePictureUrl: String?
    var bio: String?
    var speakerRoleRaw: String  // Stores SpeakerRole enum raw value

    // Arrival tracking fields (Epic 2)
    var arrived: Bool
    var arrivedConfirmedBy: String?
    var arrivedAt: Date?

    /// Computed property for SpeakerRole enum
    var speakerRole: SpeakerRole {
        get {
            SpeakerRole(rawValue: speakerRoleRaw) ?? .panelist
        }
        set {
            speakerRoleRaw = newValue.rawValue
        }
    }

    var fullName: String {
        "\(firstName) \(lastName)"
    }

    init(
        username: String,
        firstName: String,
        lastName: String,
        company: String? = nil,
        companyLogoUrl: String? = nil,
        profilePictureUrl: String? = nil,
        bio: String? = nil,
        speakerRole: SpeakerRole = .panelist,
        arrived: Bool = false,
        arrivedConfirmedBy: String? = nil,
        arrivedAt: Date? = nil
    ) {
        self.username = username
        self.firstName = firstName
        self.lastName = lastName
        self.company = company
        self.companyLogoUrl = companyLogoUrl
        self.profilePictureUrl = profilePictureUrl
        self.bio = bio
        self.speakerRoleRaw = speakerRole.rawValue
        self.arrived = arrived
        self.arrivedConfirmedBy = arrivedConfirmedBy
        self.arrivedAt = arrivedAt
    }
}
