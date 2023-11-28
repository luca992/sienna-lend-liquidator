package msg.market

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable


@Serializable
data class ExecuteMsg(
    val liquidate: Liquidate? = null,
    val redeemToken: RedeemToken? = null,
) {
    @Serializable
    class Liquidate(
        val borrower: String,
        val collateral: String,
    )

    @Serializable
    class RedeemToken(
        @Contextual val burnAmount: BigInteger,
    )
}