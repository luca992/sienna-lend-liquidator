package types;

import Repository
import io.eqoty.cosmwasm.std.types.ContractInfo

data class Market(
    val repository: Repository,
    val symbol: String,
    val decimals: UInt,
    val underlying: ContractInfo,
)