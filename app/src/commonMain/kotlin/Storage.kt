import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import datalayer.response.PriceResults
import io.eqoty.secret.std.contract.msg.Snip20Msgs
import io.eqoty.secret.std.types.Permit
import io.eqoty.secretk.client.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import msg.overseer.LendOverseerMarket
import types.LendOverseerMarketAndUnderlyingAsset
import types.UnderlyingAssetId
import types.symbolToContractAddr
import utils.normalizeDenom

val sscrtAssetId = UnderlyingAssetId("secret1k0jntykt7e4g3y88ltc60czgjuqdy4c9e8fzek")

class Storage private constructor(
    val repository: Repository,
    val marketToMarketAndUnderlyingAsset: Map<LendOverseerMarket, LendOverseerMarketAndUnderlyingAsset>,
) {
    //    val pool_cache: TimedCache<PoolInfo>
//    val decimals_cache: TimedCache<number>
    val markets = marketToMarketAndUnderlyingAsset.values.toList()
    private val underlyingMarketAssetIds =
        markets.map { UnderlyingAssetId(it.underlying.address, it.symbol) }.toMutableSet()
    val underlyingAssetToPrice = underlyingMarketAssetIds.associateWith { 0.0.toBigDecimal() }.toMutableMap()
    var blockHeight: BigInteger = 0.toBigInteger()
    var userBalance = BigInteger.ZERO
    val permits = mutableMapOf<String, Permit>()

    companion object {
        suspend fun init(
            repository: Repository,
            marketToMarketAndUnderlyingAsset: Map<LendOverseerMarket, LendOverseerMarketAndUnderlyingAsset>
        ): Storage {
            val instance = Storage(repository, marketToMarketAndUnderlyingAsset)
            instance.updatePrices()
            instance.updateBlockHeight()
            instance.updateUserBalance()
            return instance
        }
    }


    init {
        underlyingMarketAssetIds.add(UnderlyingAssetId(repository.config.token.address))
        // We always need SCRT, in order to check gas costs, this is the sscrt contract
        underlyingMarketAssetIds.add(sscrtAssetId)
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

    fun gasCostUsd(amount: BigDecimal): BigDecimal {
        return this.usdValue(amount, sscrtAssetId, 6u)
    }

    fun usdValue(
        amount: BigDecimal,
        underlyingAssetId: UnderlyingAssetId,
        decimals: UInt
    ): BigDecimal {
        return normalizeDenom(
            underlyingAssetToPrice[underlyingAssetId]!!.times(amount),
            decimals
        )
    }


    @Serializable
    private data class DenomPriceValue(
        val usd: Double
    )

    suspend fun updatePrices() {
        // try getting by contract address with coin gecko, only works for scrt and staked scrt variants at the moment
        val contractAddrs = underlyingAssetToPrice.keys.map { it.underlyingAssetAddress }

        val contractAddrToUsdPrice: Map<String, DenomPriceValue> = repository.httpClient.getResponse(
            repository.config.coinGeckoUrl,
            "v3/simple/token_price/secret?vs_currencies=usd&contract_addresses=${contractAddrs.joinToString(",")}"
        )

        underlyingAssetToPrice.keys.forEach { assetToPriceKey ->
            val fetchedUsdPrice = contractAddrToUsdPrice[assetToPriceKey.underlyingAssetAddress]?.usd?.toBigDecimal()
            fetchedUsdPrice?.run { underlyingAssetToPrice[assetToPriceKey] = this }
        }

        // try getting the rest by symbol with band protocol
        // The Band oracle REST endpoint.
        // The latest one can be found here: https://docs.bandchain.org/technical-specifications/band-endpoints.html
        val symbolsToFetch = underlyingAssetToPrice.filter { it.value == 0.0.toBigDecimal() }.keys.map { it.symbol }
        val symbolsToFetchStr = symbolsToFetch.map { "symbols=${it}" }.joinToString("&")
        require(symbolsToFetch.none { it.contains("SCRT") }) { "SCRT variants should be fetched by contract address" }
        val prices: PriceResults = repository.httpClient.getResponse(
            repository.config.bandUrl, "oracle/v1/request_prices?$symbolsToFetchStr"
        )
        prices.priceResults.forEach { x ->
            val price = (x.px.toDouble().toBigDecimal() / x.multiplier.toDouble().toBigDecimal())
            require(underlyingAssetToPrice.contains(symbolToContractAddr(x.symbol))) {
                "symbolToContractAddr has an outdated contract address. Updated ${x.symbol}'s address"
            }
            underlyingAssetToPrice[symbolToContractAddr(x.symbol)] = price
        }
        println(underlyingAssetToPrice)
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