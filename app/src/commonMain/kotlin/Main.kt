import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import co.touchlab.kermit.Logger
import io.eqoty.secretk.client.SigningCosmWasmClient
import io.eqoty.secretk.wallet.DirectSigningWallet
import io.getenv

val logger = Logger.withTag("liquidator")

@Composable
fun Repository.App() {
    var text by remember { mutableStateOf("Hello, World!") }
    MaterialTheme {
        Button(onClick = {
            text = "Hello, Liquidator!"
        }) {
            Text(text)
        }
    }
}

fun getRepositoryWithDirectSigningWallet(chain: Chain): Repository {
    val client = clientWithDirectSigningWallet(chain)
    return Repository(
        client,
        (client.wallet as? DirectSigningWallet)!!.accounts[0].address
    )
}

fun clientWithDirectSigningWallet(chain: Chain): SigningCosmWasmClient {
    val mnemonic = getenv("MNEMONIC")
    val wallet = DirectSigningWallet(mnemonic)
    return SigningCosmWasmClient(
        chain.grpcGatewayEndpoint, wallet
    )
}
