import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import org.jetbrains.skiko.wasm.onWasmReady

fun main() {
    onWasmReady {
        Window("Sienna Lend Liquidator") {
            Column(modifier = Modifier.fillMaxSize()) {
                App()
            }
        }
    }
}
