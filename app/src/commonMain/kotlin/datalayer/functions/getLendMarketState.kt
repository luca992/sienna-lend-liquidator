package datalayer.functions

import Repository
import kotlinx.serialization.encodeToString
import logger
import msg.market.LendMarketState
import msg.market.QueryMsg
import types.LendOverseerMarket
import utils.json

suspend fun Repository.getLendMarketState(
    market: LendOverseerMarket
): LendMarketState? {
    return try {
        json.decodeFromString(
            client.queryContractSmart(
                contractAddress = market.contract.address,
                contractCodeHash = market.contract.codeHash,
                queryMsg = json.encodeToString(QueryMsg(state = QueryMsg.State()))
            )
        )
    } catch (e: Exception) {
        logger.e("Failed to get lend market state", e)
        null
    }
}