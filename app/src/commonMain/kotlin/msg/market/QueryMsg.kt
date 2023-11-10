package msg.market

import kotlinx.serialization.Serializable
import msg.overseer.Pagination


@Serializable
data class QueryMsg(
    val exchangeRate: ExchangeRate? = null,
    val borrowers: Borrowers? = null,
) {
    @Serializable
    class ExchangeRate(val block: ULong)

    @Serializable
    class Borrowers(val block: ULong, val pagination: Pagination)
}