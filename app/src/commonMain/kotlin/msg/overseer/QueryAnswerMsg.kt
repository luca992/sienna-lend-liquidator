package msg.overseer

import io.eqoty.cosmwasm.std.types.ContractInfo
import kotlinx.serialization.Serializable


@Serializable
data class LendOverseerConfig(
    /** The discount on collateral that a liquidator receives.  */
    val premium: String,
    /** The percentage of a liquidatable account's borrow that can be repaid in a single liquidate transaction.
     * If a user has multiple borrowed assets, the close factor applies to any single borrowed asset,
     * not the aggregated value of a user’s outstanding borrowing.  */
    val close_factor: String,
)

@Serializable
data class LendOverseerMarket(
    val contract: ContractInfo,
    /** The symbol of the underlying asset. Note that this is the same as the symbol
     * that the oracle expects, not what the actual token has in its storage. */
    val symbol: String,
    /** The decimals that the market has. Corresponds to the decimals of the underlying token. */
    val decimals: Int,
    /** The percentage rate at which tokens can be borrowed given the size of the collateral. */
    val ltvRatio: String,
)