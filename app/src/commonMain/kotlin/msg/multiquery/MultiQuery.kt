package msg.multiquery

import kotlinx.serialization.Serializable


@Serializable
data class MultiQueryResult(
    val error: String? = null,
    val data: String? = null,
)

@Serializable
data class MultiQuery(
    val contractAddress: String,
    val codeHash: String,
    val query: String
)


@Serializable
data class QueryMsg(
    val batchQuery: BatchQuery? = null,
    val underlyingAsset: UnderlyingAsset? = null,
) {

    @Serializable
    data class BatchQuery(val queries: List<MultiQuery>)

    @Serializable
    class UnderlyingAsset
}