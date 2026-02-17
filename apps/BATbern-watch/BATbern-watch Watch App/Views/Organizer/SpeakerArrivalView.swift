//
//  SpeakerArrivalView.swift
//  BATbern-watch Watch App
//
//  O2: Speaker arrival tracking — placeholder for W2.4.
//  W2.2: Ensures navigation works end-to-end before W2.4 implements full view.
//

import SwiftUI

struct SpeakerArrivalView: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "person.crop.circle.badge.checkmark")
                .font(.system(size: 32))
                .foregroundStyle(.secondary)

            Text("Speaker Arrival")
                .font(.headline)

            Text("Story W2.4")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding()
    }
}

#Preview {
    SpeakerArrivalView()
}
