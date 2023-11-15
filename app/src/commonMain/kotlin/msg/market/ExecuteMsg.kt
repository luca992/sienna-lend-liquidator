package msg.market

import kotlinx.serialization.Serializable


@Serializable
data class ExecuteMsg(
    val liquidate: Liquidate? = null,
) {
    @Serializable
    class Liquidate(
        val borrower: String,
        val collateral: String,
    )
}