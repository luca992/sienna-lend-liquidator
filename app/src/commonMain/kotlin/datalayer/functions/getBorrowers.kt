package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.integer.BigInteger
import json
import kotlinx.serialization.encodeToString
import msg.market.LendMarketBorrowerAnswer
import msg.market.QueryMsg
import msg.overseer.PaginatedResponse
import msg.overseer.Pagination
import types.LendOverseerMarketAndUnderlyingAsset

suspend fun Repository.getBorrowers(
    market: LendOverseerMarketAndUnderlyingAsset, page: Pagination, blockHeight: BigInteger
): PaginatedResponse<LendMarketBorrowerAnswer>? {
    return try {
        json.decodeFromString(
            client.queryContractSmart(
                contractAddress = market.contract.address,
                contractCodeHash = market.contract.codeHash,
                queryMsg = json.encodeToString(QueryMsg(borrowers = QueryMsg.Borrowers(blockHeight.ulongValue(), page)))
            )
        )
    } catch (t: Throwable) {
        if (t.message?.contains("Not entered in any markets.") == true) {
            return null
        } else {
            throw t
        }
    }
}