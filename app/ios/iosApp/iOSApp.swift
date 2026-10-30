import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        IosApplicationBootstrap.shared.launchOnApplicationStart()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()

        }
    }
}
