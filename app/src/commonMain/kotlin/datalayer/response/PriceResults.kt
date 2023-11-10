package datalayer.response;

import kotlinx.serialization.Serializable


@Serializable
data class PriceResults(
    val priceResults: List<PriceResult>,
)

@Serializable
data class PriceResult(
    val symbol: String,
    val multiplier: UInt,
    val px: ULong,
    val requestId: UInt,
    val resolveTime: UInt
)
