package datalayer.functions

import Repository
import io.eqoty.cosmwasm.std.types.ContractInfo
import io.ktor.util.*
import json
import kotlinx.serialization.encodeToString
import msg.multiquery.MultiQuery
import msg.multiquery.MultiQueryResult
import msg.overseer.LendOverseerMarket

const val UNDERLYING_ASSET_BATCH = 15


suspend fun Repository.fetchUnderlyingMulticallAssets(
    markets: List<LendOverseerMarket>,
): List<ContractInfo> {
    val assetResults = mutableListOf<List<MultiQueryResult>>()
    val buffer = mutableListOf<LendOverseerMarket>()

    markets.forEachIndexed { i, market ->
        buffer.add(market)

        if (buffer.size == UNDERLYING_ASSET_BATCH || (buffer.size > 0 && i == markets.size - 1)) {
            val queries: List<MultiQuery> = buffer.map { x ->
                MultiQuery(
                    contractAddress = x.contract.address,
                    codeHash = x.contract.codeHash,
                    query = json.encodeToString(
                        msg.multiquery.QueryMsg(underlyingAsset = msg.multiquery.QueryMsg.UnderlyingAsset())
                    ).encodeBase64()
                )
            }
            assetResults.add(
                json.decodeFromString(
                    client.queryContractSmart(
                        contractAddress = config.multicall.address,
                        contractCodeHash = config.multicall.codeHash,
                        queryMsg = json.encodeToString(
                            msg.multiquery.QueryMsg(batchQuery = msg.multiquery.QueryMsg.BatchQuery(queries))
                        )
                    )
                )
            )
            buffer.clear()
        }
    }

    val assets = mutableListOf<ContractInfo>()

    assetResults.forEach { resp ->
        resp.forEach { result ->
            if (result.error != null) {
                throw Error(result.error.decodeBase64String())
            }

            assets.add(json.decodeFromString(result.data!!.decodeBase64String()))
        }
    }

    return assets
}