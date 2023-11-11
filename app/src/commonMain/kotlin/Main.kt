import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.ionspin.kotlin.bignum.serialization.kotlinx.biginteger.bigIntegerhumanReadableSerializerModule
import io.eqoty.secretk.client.SigningCosmWasmClient
import io.eqoty.secretk.wallet.DirectSigningWallet
import io.getenv
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import types.Config
import types.Loan

val logger = Logger.withTag("liquidator")
val json = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    serializersModule = bigIntegerhumanReadableSerializerModule
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Repository.App() {
    var buttonText by remember { mutableStateOf("Liquidator is not initialized!") }
    var liquidator by remember { mutableStateOf<Liquidator?>(null) }
    var userBalanceTokenLabel by remember { mutableStateOf("") }
    val balance by remember { derivedStateOf { liquidator?.storage?.userBalance } }
    LaunchedEffect(Unit) {
        liquidator = Liquidator.create(this@App)
        buttonText = "Query for loans to liquidate!"
        client.getLabelByContractAddr(liquidator!!.storage.repository.config.token.address).let {
            userBalanceTokenLabel = it
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var loans by remember { mutableStateOf<List<Loan>>(listOf()) }
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Liquidator")
                    },
                    actions = {
                        Text("Balance: ${balance} $userBalanceTokenLabel")
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                Button(onClick = {
                    if (liquidator == null) {
                        buttonText = "Liquidator is not initialized!"
                        return@Button
                    } else {
                        buttonText = "Working..."
                        coroutineScope.launch {
                            loans = liquidator!!.runOnce()
                            buttonText = "Query for loans to liquidate!"
                        }
                    }

                }) {
                    Text(buttonText)
                }
                LazyColumn {
                    items(loans) { loan ->
                        LoanCard(loan, client)
                    }
                }
            }
        }
    }
}

@Composable
fun LoanCard(loan: Loan, client: SigningCosmWasmClient) {
    val coroutineScope = rememberCoroutineScope()
    var underlyingCollateralsLabel by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Candidate ID: ${loan.candidate.id}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("LendOverseerMarket: ${loan.candidate.market_info.symbol}", fontWeight = FontWeight.Medium)
                Text("LTV Ratio: ${loan.candidate.market_info.ltvRatio}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Payable: ${loan.candidate.payable.toPlainString()}")
                Text("Seizable USD: ${loan.candidate.seizable_usd.toPlainString()}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Collateral's Market Symbol: ${loan.market.symbol}")
                Text("Collateral's Underlying Contract Label: $underlyingCollateralsLabel")
                coroutineScope.launch {
                    client.getLabelByContractAddr(loan.market.underlying.address).let {
                        underlyingCollateralsLabel = it
                    }
                }
                Text("Collateral's Underlying Contract Address: ${loan.market.underlying.address}")
            }
        }
    }
}


fun getRepositoryWithDirectSigningWallet(): Repository {
    val config: Config = json.decodeFromString(getenv("CONFIG")!!)
    val client = clientWithDirectSigningWallet(config)
    return Repository(
        client, (client.wallet as? DirectSigningWallet)!!.accounts[0].address, config
    )
}

fun clientWithDirectSigningWallet(config: Config): SigningCosmWasmClient {
    val wallet = DirectSigningWallet(config.mnemonic)
    return SigningCosmWasmClient(
        config.apiUrl, wallet, chainId = config.chainId,
    )
}
