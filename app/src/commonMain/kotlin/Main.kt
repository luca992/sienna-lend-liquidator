import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import co.touchlab.kermit.Logger
import config.Config
import io.eqoty.secretk.client.SigningCosmWasmClient
import io.eqoty.secretk.wallet.DirectSigningWallet
import io.getenv
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

val logger = Logger.withTag("liquidator")
val json = Json { namingStrategy = JsonNamingStrategy.SnakeCase }

@Composable
fun Repository.App() {
    var text by remember { mutableStateOf("Hello, World!") }
    MaterialTheme {
        Button(onClick = {
            text = "Hello, Liquidator!"

        }) {
            Text(text)
        }
        LaunchedEffect(Unit) {
            Liquidator(this@App).create(config)
        }
    }
}


fun getRepositoryWithDirectSigningWallet(): Repository {
    val config: Config = json.decodeFromString(getenv("CONFIG")!!)
    val client = clientWithDirectSigningWallet(config)
    return Repository(
        client,
        (client.wallet as? DirectSigningWallet)!!.accounts[0].address,
        config
    )
}

fun clientWithDirectSigningWallet(config: Config): SigningCosmWasmClient {
    val wallet = DirectSigningWallet(config.mnemonic)
    return SigningCosmWasmClient(
        config.apiUrl, wallet, chainId = config.chainId,
    )
}
