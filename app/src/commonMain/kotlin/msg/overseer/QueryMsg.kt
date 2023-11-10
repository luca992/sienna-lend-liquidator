package msg.overseer

import kotlinx.serialization.Serializable


@Serializable
data class Pagination(
    val limit: UInt,
    val start: UInt,
)

@Serializable
data class PaginatedResponse<T>(
    /** The total number of entries stored by the contract. */
    val total: UInt,
    /** The entries on this page. */
    val entries: List<T>,
)

@Serializable
data class QueryMsg(
    val config: Config? = null,
    val markets: Markets? = null
) {
    @Serializable
    class Config

    @Serializable
    class Markets(val pagination: Pagination)
}