package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.eqoty.secret.std.contract.msg.Snip20Msgs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonPrimitive
import logger
import types.LendOverseerMarket
import types.stkdScrtAssetId
import utils.json

enum class BalanceType {
    Market, UnderlyingAsset
}

suspend fun Repository.updateUserBalance(underlyingAssetIds: List<LendOverseerMarket>, balanceType: BalanceType) {
    underlyingAssetIds.forEach { underlyingAssetId ->
        updateUserBalance(underlyingAssetId, balanceType)
    }
}

suspend fun Repository.updateUserBalance(lendOverseerMarket: LendOverseerMarket, balanceType: BalanceType) {
    val contract = when (balanceType) {
        BalanceType.Market -> lendOverseerMarket.contract
        BalanceType.UnderlyingAsset -> lendOverseerMarket.underlying
    }
    val viewingKey = config.viewingKeys.firstOrNull { it.address == contract.address }

    val (query, queryType) = when (viewingKey) {
        null -> {
            val query = json.encodeToString(
                Snip20Msgs.Query(
                    withPermit = Snip20Msgs.Query.WithPermit(
                        permit = getPermit(senderAddress, contract.address),
                        query = Snip20Msgs.QueryWithPermit(balance = Snip20Msgs.QueryWithPermit.Balance())
                    )
                )
            )
            query to "permit"
        }

        else -> {
            val query = json.encodeToString(
                Snip20Msgs.Query(
                    balance = Snip20Msgs.Query.Balance(
                        address = senderAddress, key = viewingKey.viewingKey
                    )
                )
            )
            query to "viewing key"
        }
    }

    val balance = try {
        val response = client.queryContractSmart(
            contract.address, query, contract.codeHash
        )
        when (balanceType) {
            BalanceType.Market -> (json.parseToJsonElement(response).jsonPrimitive.content.toBigDecimal() * getExchangeRate(
                lendOverseerMarket
            )).toBigInteger()

            BalanceType.UnderlyingAsset -> json.decodeFromString<Snip20Msgs.QueryAnswer>(response).balance!!.amount!!
        }
    } catch (t: Throwable) {
        logger.e(
            "Failed to query $balanceType balance for ${lendOverseerMarket.underlyingAssetId.snip20Symbol} with $queryType",
            t
        )
        (-1).toBigInteger()
    }

    when (balanceType) {
        BalanceType.Market -> runtimeCache.userMarketBalance[lendOverseerMarket.underlyingAssetId] = balance
        BalanceType.UnderlyingAsset -> runtimeCache.userBalance[lendOverseerMarket.underlyingAssetId] = balance
    }
}