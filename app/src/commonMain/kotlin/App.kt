import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.eqoty.secretk.client.SigningCosmWasmClient
import io.eqoty.secretk.wallet.DirectSigningWallet
import io.getenv
import kotlinx.coroutines.launch
import types.Config
import types.Loan
import utils.json

val logger = Logger.withTag("liquidator")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: AppViewModel) {
    var buttonText by remember { mutableStateOf("Query for loans to liquidate!") }
    val userUnderlyingAssetBalance by remember {
        derivedStateOf {
            viewModel.userUnderlyingAssetBalance[viewModel.selectedLendMarket.value.underlyingAssetId]
                ?: (-1).toBigInteger()
        }
    }
    val userMarketBalance by remember {
        derivedStateOf {
            viewModel.userMarketBalance[viewModel.selectedLendMarket.value.underlyingAssetId] ?: (-1).toBigInteger()
        }
    }
    val loans by remember {
        derivedStateOf {
            viewModel.marketToLoans[viewModel.selectedLendMarket.value] ?: emptyList()
        }
    }
    LaunchedEffect(Unit) {
        // trigger the wallet balance to update by
        viewModel.init()
    }
    var selectLendMarketDropdownExpanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    MaterialTheme {
        Scaffold(topBar = {
            TopAppBar(title = {
                Text("Liquidator")
            }, actions = {
                OutlinedButton(onClick = {
                    selectLendMarketDropdownExpanded = !selectLendMarketDropdownExpanded
                }) {
                    Text("Change Lend Market")
                    DropdownMenu(
                        expanded = selectLendMarketDropdownExpanded,
                        onDismissRequest = {
                            selectLendMarketDropdownExpanded = false
                        },
                    ) {
                        viewModel.allLendMarkets.forEach { lendMarket ->
                            DropdownMenuItem({
                                Text(lendMarket.underlyingAssetId.snip20Symbol)
                            }, onClick = {
                                coroutineScope.launch {
                                    viewModel.setSelectedLendMarket(lendMarket)
                                }
                                selectLendMarketDropdownExpanded = false
                            })
                        }
                    }
                }
            })
        }) { innerPadding ->

            LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                item {
                    SelectionContainer {
                        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Lend Market: ${viewModel.selectedLendMarket.value.underlyingAssetId.snip20Symbol}")
                                    Row {
                                        Column {
                                            Text(
                                                "Clamp to user balance:", style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                "$userUnderlyingAssetBalance ${viewModel.selectedLendMarket.value.underlyingAssetId.snip20Symbol}",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                        }
                                        Switch(checked = viewModel.clampToWalletBalance.value, onCheckedChange = {
                                            viewModel.clampToWalletBalance.value = it
                                        })
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {

                                        Text(
                                            "User market balance:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "$userMarketBalance ${viewModel.selectedLendMarket.value.underlyingAssetId.snip20Symbol}",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            "User market balance to sl asset conversion:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${viewModel.userMarketBalanceSlConversion.value} sl-${viewModel.selectedLendMarket.value.underlyingAssetId.snip20Symbol}",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        DisableSelection {
                                            Button(onClick = {
                                                coroutineScope.launch {
                                                    viewModel.withdrawMaxUserBalance()
                                                }
                                            }) {
                                                Text("Withdraw Max User Balance")
                                            }
                                        }
                                        Text(
                                            "Underlying asset balance:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${viewModel.marketUnderlyingBalance.value} ${viewModel.selectedLendMarket.value.underlyingAssetId.snip20Symbol}" +
                                                    " (${viewModel.marketUnderlyingBalanceSlConversion.value} sl-${viewModel.selectedLendMarket.value.underlyingAssetId.snip20Symbol})",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                    DisableSelection {
                                        Button(onClick = {
                                            buttonText = "Working..."
                                            coroutineScope.launch {
                                                viewModel.getLoans()
                                                buttonText = "Query for loans to liquidate!"
                                            }

                                        }) {
                                            Text(buttonText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                items(loans) { loan ->
                    LoanCard(loan) {
                        viewModel.liquidate(loan)
                    }
                }
            }
        }
    }
}

@Composable
fun LoanCard(loan: Loan, onClickLiquidate: suspend () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Lend Market Symbol: ${loan.market.symbol}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Lend Market's underlying asset contract:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    loan.market.underlying.address,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Lend Market's underlying asset contract label:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    loan.market.underlyingAssetId.snip20Symbol,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text("Loan Info:", fontWeight = FontWeight.Medium)
                Text(
                    text = "Candidate ID: ${loan.candidate.id}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val loanMarketUnderlyingAssetSnip20Symbol = loan.market.underlyingAssetId.snip20Symbol
                val loanCollateralSnip20Symbol = loan.candidate.marketInfo.underlyingAssetId.snip20Symbol
                Text("Total Amount Owed (Payable): ${loan.candidate.totalPayable} $loanMarketUnderlyingAssetSnip20Symbol")
                val clampedPrefix = if (loan.candidate.clampedToWalletBalance) "Clamped " else ""
                Text(clampedPrefix + "Amount Owed (Payable): ${loan.candidate.payable.toPlainString()} $loanMarketUnderlyingAssetSnip20Symbol")
                Text(clampedPrefix + "USD Value Of Payable: ${loan.candidate.payableUsd.toPlainString()}")
                Spacer(modifier = Modifier.height(8.dp))

                Text("Amount of Seizable Collateral: ${loan.candidate.seizable} $loanCollateralSnip20Symbol")
                Text("Seizable Collateral's USD Value: ${loan.candidate.seizableUsd.toPlainString()}")
                Text(
                    "Seizable Collateral's Symbol: ${loan.candidate.marketInfo.symbol}", fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                DisableSelection {
                    Button(onClick = {
                        coroutineScope.launch {
                            onClickLiquidate()
                        }
                    }) {
                        Text("Liquidate")
                    }
                }
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
    val wallet = DirectSigningWallet(config.mnemonic.ifBlank { null })
    return SigningCosmWasmClient(
        config.apiUrl, wallet, chainId = config.chainId,
    )
}
