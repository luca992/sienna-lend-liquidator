package msg.market

import kotlinx.serialization.Serializable
import msg.overseer.Pagination


@Serializable
data class QueryMsg(
    val exchangeRate: ExchangeRate? = null,
    val borrowers: Borrowers? = null,
    val simulateLiquidation: SimulateLiquidation? = null,
) {
    @Serializable
    class ExchangeRate(val block: ULong)

    @Serializable
    class Borrowers(val block: ULong, val pagination: Pagination)

    @Serializable
    class SimulateLiquidation(
        val block: ULong,
        val borrower: String,
        val collateral: String,
        val amount: String,
    )
}