import androidx.compose.ui.window.Window
import platform.AppKit.NSApp
import platform.AppKit.NSApplication

fun main() {
    NSApplication.sharedApplication()
    Window("Sienna Lend Liquidator") {
        App()
    }
    NSApp?.run()
}
