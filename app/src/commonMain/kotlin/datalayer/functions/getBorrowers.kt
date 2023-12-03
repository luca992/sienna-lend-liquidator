package datalayer.functions

import Repository
import kotlinx.serialization.encodeToString
import msg.market.LendMarketBorrowerAnswer
import msg.market.QueryMsg
import msg.overseer.PaginatedResponse
import msg.overseer.Pagination
import types.LendOverseerMarket
import utils.json

suspend fun Repository.getBorrowers(
    market: LendOverseerMarket, page: Pagination,
): PaginatedResponse<LendMarketBorrowerAnswer>? {
    updateBlockHeight()
    val blockHeight = runtimeCache.blockHeight.value
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