import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import co.touchlab.kermit.Logger
import com.ionspin.kotlin.bignum.serialization.kotlinx.biginteger.bigIntegerhumanReadableSerializerModule
import io.eqoty.secretk.client.SigningCosmWasmClient
import io.eqoty.secretk.wallet.DirectSigningWallet
import io.getenv
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import types.Config

val logger = Logger.withTag("liquidator")
val json = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    serializersModule = bigIntegerhumanReadableSerializerModule
}

@Composable
fun Repository.App() {
    var text by remember { mutableStateOf("Start Liquidator!") }
    var liquidator by remember { mutableStateOf<Liquidator?>(null) }
    val coroutineScope = rememberCoroutineScope()
    MaterialTheme {
        Button(onClick = {
            if (liquidator == null) {
                text = "Liquidator is not initialized!"
                return@Button
            } else {
                text = "Liquidator started!"
                coroutineScope.launch {
                    liquidator!!.runOnce()
                }
            }

        }) {
            Text(text)
        }
        LaunchedEffect(Unit) {
            liquidator = Liquidator.create(this@App)
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
