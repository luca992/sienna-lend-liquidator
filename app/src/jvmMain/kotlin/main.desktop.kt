import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication

fun main() {
    singleWindowApplication(
        title = "Sienna Lend Liquidator",
        state = WindowState(size = DpSize(500.dp, 800.dp))
    ) {
        App()
    }
}


