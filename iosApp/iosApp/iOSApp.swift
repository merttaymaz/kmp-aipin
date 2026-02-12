import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Initialize Koin
        KoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            DrawerView()
        }
    }
}
