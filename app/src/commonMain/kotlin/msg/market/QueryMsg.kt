package msg.market

import kotlinx.serialization.Serializable
import msg.overseer.Pagination


@Serializable
data class QueryMsg(
    val exchangeRate: ExchangeRate? = null,
    val borrowers: Borrowers? = null,
    val simulateLiquidation: SimulateLiquidation? = null,
    val state: State? = null,
) {
    @Serializable
    data class ExchangeRate(val block: ULong)

    @Serializable
    class State()

    @Serializable
    data class Borrowers(val block: ULong, val pagination: Pagination)

    @Serializable
    data class SimulateLiquidation(
        val block: ULong,
        val borrower: String,
        val collateral: String,
        val amount: String,
    )
}