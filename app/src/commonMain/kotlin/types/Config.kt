package types

import io.eqoty.cosmwasm.std.types.ContractInfo
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val coinGeckoUrl: String,
    val coinGeckoDemoApiKey: String,
    val bandUrl: String,
    val apiUrl: String,
    val chainId: String,
    val mnemonic: String,
    val interval: Int,
    val maxPriceImpact: Double,
    val overseer: ContractInfo,
    val multicall: ContractInfo,
    val router: ContractInfo,
    val factory: ContractInfo,
    val viewingKeys: List<ViewingKey>,
    val gasCosts: GasCosts
)

@Serializable
data class ViewingKey(
    val address: String, val viewingKey: String
)

@Serializable
data class GasCosts(
    val swap: Int, val liquidate: Int, val withdraw: Int
)