import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import datalayer.response.PriceResults
import io.eqoty.secret.std.contract.msg.Snip20Msgs
import io.eqoty.secret.std.types.Permit
import io.eqoty.secretk.client.Json
import kotlinx.serialization.encodeToString

class Storage private constructor(
    val repository: Repository,
    val price_symbols: MutableSet<String>,
) {
    //    val pool_cache: TimedCache<PoolInfo>
//    val decimals_cache: TimedCache<number>
    val prices = price_symbols.associateWith { 0.0.toBigDecimal() }.toMutableMap()
    var blockHeight: BigInteger = 0.toBigInteger()
    var userBalance = BigInteger.ZERO
    val permits = mutableMapOf<String, Permit>()

    companion object {
        suspend fun init(
            repository: Repository,
            price_symbols: MutableSet<String>,
        ): Storage {
            val instance = Storage(repository, price_symbols)
            instance.updatePrices()
            instance.updateBlockHeight()
            instance.updateUserBalance()
            return instance
        }
    }


    init {
        price_symbols.add(repository.config.token.symbol)
        price_symbols.add("SCRT") // We always need SCRT, in order to check gas costs
//
//
//        this.pool_cache = new TimedCache < PoolInfo >(
//            multicall,
//            POOL_INFO_CACHE_TIME,
//            POOLS_BATCH,
//            'pair_info',
//            (item) => {
//            const info = item . pair_info
//
//                    return {
//                        amount_0: new BigNumber(info.amount_0),
//                        amount_1: new BigNumber(info.amount_1),
//                        pair: info.pair
//                    }
//        }
//        )
//        this.decimals_cache = new TimedCache < number >(
//            multicall,
//            Infinity,
//            DECIMALS_BATCH,
//            { token_info: { } },
//            (item) => item . token_info . decimals
//        )
    }

    fun gas_cost_usd(amount: BigDecimal): BigDecimal {
        return this.usd_value(amount, "SCRT", 6)
    }

    fun usd_value(
        amount: BigDecimal,
        symbol: String,
        decimals: Int
    ): BigDecimal {
        TODO()
//
//        return normalize_denom(
//            prices[symbol]!!.times(amount),
//            decimals
//        )
    }

    suspend fun updatePrices() {
        val symbols = prices.keys.map { "symbols=${it}" }

        val prices: PriceResults = repository.httpClient.getResponse(
            repository.config.bandUrl, "/oracle/v1/request_prices?${symbols.joinToString("&")}"
        )

        prices.priceResults.forEach { x ->
            val price = (x.px.toDouble().toBigDecimal() / x.multiplier.toDouble().toBigDecimal())
            this.prices[x.symbol] = price
        }
    }

    suspend fun updateBlockHeight() {
        this.blockHeight = repository.client.getLatestBlock().block.header.height.toBigInteger()
    }


    suspend fun updateUserBalance() {
        val client = repository.client
        val senderAddress = repository.senderAddress
        val contract = this.repository.config.token

        val query = Json.encodeToString(
            Snip20Msgs.Query(
                balance = Snip20Msgs.Query.Balance(address = senderAddress, key = repository.config.token.underlyingVk)
            )
        )
        val response = client.queryContractSmart(
            contract.address, query, contract.codeHash
        )

        userBalance = Json.decodeFromString<Snip20Msgs.QueryAnswer>(response).balance!!.amount!!
    }
}