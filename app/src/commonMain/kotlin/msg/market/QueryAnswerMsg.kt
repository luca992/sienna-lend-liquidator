package msg.market

import datalayer.sources.cache.RuntimeCache
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import msg.overseer.LendOverseerMarketQueryAnswer
import types.LendMarketBorrower


@Serializable
data class LendSimulatedLiquidation(
    /** The amount that would be seized by that liquidation minus protocol fees.  */
    @Contextual val seize_amount: BigInteger,
    /** If the liquidation would be unsuccessful this will contain amount by which the seize amount falls flat. Otherwise, it's 0.  */
    @Contextual val shortfall: BigInteger,
)


@Serializable
data class LendMarketBorrowerAnswer(
    val id: String,
    /** Borrow balance at the last interaction of the borrower. */
    @Contextual val principalBalance: BigInteger,
    /** Current borrow balance. */
    @Contextual val actualBalance: BigInteger,
    val liquidity: LendAccountLiquidity,
    var markets: List<LendOverseerMarketQueryAnswer>
) {
    fun toLendMarketBorrower(storage: RuntimeCache): LendMarketBorrower {
        return LendMarketBorrower(id = id,
            principalBalance = principalBalance,
            actualBalance = actualBalance,
            liquidity = liquidity,
            markets = markets.map { market -> storage.lendOverSeerMarketQueryAnswerToLendOverseerMarket[market]!! }.toMutableList()
        )
    }
}

@Serializable
data class LendAccountLiquidity(
    /** The USD value borrowable by the user, before it reaches liquidation. */
    @Contextual val liquidity: BigInteger,
    /** If > 0 the account is currently below the collateral requirement and is subject to liquidation. */
    @Contextual val shortfall: BigInteger
)
