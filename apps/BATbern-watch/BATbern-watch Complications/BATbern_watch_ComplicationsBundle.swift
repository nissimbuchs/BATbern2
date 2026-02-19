//
//  BATbern_watch_ComplicationsBundle.swift
//  BATbern-watch Complications
//
//  Widget extension entry point. Registers all three complication types.
//  W3.3: AC1 — C1 circular, C2 rectangular, C3 corner.
//
//  Source: docs/watch-app/architecture.md#Frontend-Architecture
//

import WidgetKit
import SwiftUI

@main
struct BATbern_watch_ComplicationsBundle: WidgetBundle {
    var body: some Widget {
        CircularComplication()
        RectangularComplication()
        CornerComplication()
    }
}
