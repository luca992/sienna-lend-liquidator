import io.eqoty.cosmwasm.std.types.ContractInfo
import io.ktor.util.*
import kotlinx.serialization.encodeToString
import msg.multiquery.MultiQuery
import msg.multiquery.MultiQueryResult
import msg.overseer.*
import types.Config
import types.Market

const val PRICES_UPDATE_INTERVAL = 3 * 60 * 1000
const val UNDERLYING_ASSET_BATCH = 15
val BLACKLISTED_SYMBOLS = listOf("LUNA", "UST")

class Liquidator(val repo: Repository) {

    val client = repo.client
    suspend fun create(config: Config) {
        val msg = QueryMsg(
            config = QueryMsg.Config()
        )
        val overseerConfig: LendOverseerConfig = json.decodeFromString(
            client.queryContractSmart(
                contractAddress = config.overseer.address,
                contractCodeHash = config.overseer.codeHash,
                queryMsg = json.encodeToString(msg)
            )
        )

        val allMarkets = fetchAllPages<LendOverseerMarket>({ pagination ->
            json.decodeFromString(
                client.queryContractSmart(
                    contractAddress = config.overseer.address,
                    contractCodeHash = config.overseer.codeHash,
                    queryMsg = json.encodeToString(QueryMsg(markets = QueryMsg.Markets(pagination))),
                )
            )
        }, 30u, { x -> !BLACKLISTED_SYMBOLS.contains(x.symbol) })

        val storage = Storage.init(repo, allMarkets.map { it.symbol }.toMutableSet())

        val assets = fetchUnderlyingMulticallAssets(allMarkets)

        val markets = mutableListOf<Market>()

        allMarkets.forEachIndexed { i, market ->
            val m = Market(
                repository = repo,
                symbol = market.symbol,
                decimals = market.decimals,
                underlying = assets[i]
            )
            markets.add(m)
        }

    }


    suspend fun <T> fetchAllPages(
        query: suspend (pagination: Pagination) -> PaginatedResponse<T>?,
        limit: UInt,
        filter: ((x: T) -> Boolean)?,
    ): List<T> {
        var start = 0u
        var total = 0u

        val result = mutableListOf<T>()

        do {
            val page = query(Pagination(limit, start))

            if (page == null) {
                start += limit
                continue
            }

            total = page.total
            start += limit

            result.addAll(if (filter != null) page.entries.filter(filter) else page.entries)

        } while (start <= total)

        return result
    }


    private suspend fun fetchUnderlyingMulticallAssets(
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
                            contractAddress = repo.config.multicall.address,
                            contractCodeHash = repo.config.multicall.codeHash,
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
}