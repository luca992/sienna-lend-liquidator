package datalayer.functions

import Repository
import io.eqoty.secret.std.contract.msg.Snip20Msgs
import kotlinx.serialization.encodeToString
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
    val response = client.queryContractSmart(
        lendOverseerMarket.underlying.address, query, lendOverseerMarket.underlying.codeHash
    )

    runtimeCache.userBalance[lendOverseerMarket.underlyingAssetId] =
        json.decodeFromString<Snip20Msgs.QueryAnswer>(response).balance!!.amount!!
}