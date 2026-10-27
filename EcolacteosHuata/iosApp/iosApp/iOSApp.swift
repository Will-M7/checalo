import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Koin se inicia una sola vez, antes de crear la pantalla Compose (KoinIos.kt).
        KoinIosKt.doInitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
