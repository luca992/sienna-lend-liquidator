package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.eqoty.secret.std.contract.msg.Snip20Msgs
import kotlinx.serialization.encodeToString
import logger
import types.LendOverseerMarket
import utils.json

suspend fun Repository.updateUserBalance(underlyingAssetIds: List<LendOverseerMarket>) {
    underlyingAssetIds.forEach { underlyingAssetId ->
        updateUserBalance(underlyingAssetId)
    }
}

suspend fun Repository.updateUserBalance(lendOverseerMarket: LendOverseerMarket) {
    val query = json.encodeToString(
        Snip20Msgs.Query(
            withPermit = Snip20Msgs.Query.WithPermit(
                permit = getPermit(senderAddress, lendOverseerMarket.underlying.address),
                query = Snip20Msgs.QueryWithPermit(balance = Snip20Msgs.QueryWithPermit.Balance())
            )
        )
    )
    val response = try {
        json.decodeFromString<Snip20Msgs.QueryAnswer>(
            client.queryContractSmart(
                lendOverseerMarket.underlying.address, query, lendOverseerMarket.underlying.codeHash
            )
        ).balance!!.amount!!
    } catch (t: Throwable) {
        logger.e("Failed to query balance for ${lendOverseerMarket.underlyingAssetId.snip20Symbol} with permit")
        (-1).toBigInteger()
    }

    runtimeCache.userBalance[lendOverseerMarket.underlyingAssetId] = response
}