//
//  PairingInfo.swift
//  BATbern-watch Watch App
//
//  SwiftData model for organizer pairing information (Epic 2).
//  Stub for now — will be populated in Story W2.1.
//

import Foundation
import SwiftData

@Model
class PairingInfo {
    var eventCode: String
    var pairingToken: String
    var organizerUsername: String
    var pairedAt: Date

    init(
        eventCode: String,
        pairingToken: String,
        organizerUsername: String,
        pairedAt: Date = Date()
    ) {
        self.eventCode = eventCode
        self.pairingToken = pairingToken
        self.organizerUsername = organizerUsername
        self.pairedAt = pairedAt
    }
}
