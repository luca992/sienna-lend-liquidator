import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import io.eqoty.secretk.utils.ensureLibsodiumInitialized

suspend fun main() {
    ensureLibsodiumInitialized()
    val repository = getRepositoryWithDirectSigningWallet()
    singleWindowApplication(
        title = "Sienna Lend Liquidator", state = WindowState(size = DpSize(500.dp, 800.dp))
    ) {
        repository.App()
    }
}


