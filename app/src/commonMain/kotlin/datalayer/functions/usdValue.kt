package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import types.UnderlyingAssetId
import types.sscrtAssetId
import utils.normalizeDenom

fun Repository.gasCostUsd(amount: BigDecimal): BigDecimal {
    return usdValue(amount, sscrtAssetId, 6u)
}

fun Repository.usdValue(
    amount: BigDecimal, underlyingAssetId: UnderlyingAssetId, decimals: UInt
): BigDecimal {
    return normalizeDenom(
        runtimeCache.underlyingAssetToPrice[underlyingAssetId]!!.times(amount), decimals
    )
}
