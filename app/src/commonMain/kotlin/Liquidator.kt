import config.Config
import kotlinx.serialization.encodeToString
import msg.overseer.*

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

        val allMarkets = fetchAllPages<LendOverseerMarket>(
            { pagination ->
                json.decodeFromString(
                    client.queryContractSmart(
                        contractAddress = config.overseer.address,
                        contractCodeHash = config.overseer.codeHash,
                        queryMsg = json.encodeToString(QueryMsg(markets = QueryMsg.Markets(pagination))),
                    )
                )
            },
            30u,
            { x -> !BLACKLISTED_SYMBOLS.contains(x.symbol) }
        )
        logger.i("allMarkets: $allMarkets")

        val storage = Storage.init(repo, allMarkets.map { it.symbol }.toMutableSet())

        println(storage.prices.toString())

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
}