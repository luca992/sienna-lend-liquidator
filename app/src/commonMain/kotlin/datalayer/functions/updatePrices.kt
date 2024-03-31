package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import datalayer.response.PriceResults
import kotlinx.serialization.Serializable
import types.symbolToAssetId

@Serializable
private data class DenomPriceValue(
    val usd: Double
)

suspend fun Repository.updatePrices() {
    // try getting by contract address with coin gecko, only works for scrt and staked scrt variants at the moment
    runtimeCache.lendOverseerMarkets.forEach {
        runtimeCache.underlyingAssetToPrice.getOrPut(it.underlyingAssetId) { 0.0.toBigDecimal() }
    }
    val allContractAddrs = runtimeCache.underlyingAssetToPrice.keys.map { it.underlyingAssetAddress }
    val contractAddrsBatches = allContractAddrs.chunked(10)
    contractAddrsBatches.forEach { contractAddrs ->
        val contractAddrToUsdPrice: Map<String, DenomPriceValue> = httpClient.getResponse(
            config.coinGeckoUrl,
            "v3/simple/token_price/secret?vs_currencies=usd&contract_addresses=${contractAddrs.joinToString(",")}&x_cg_demo_api_key=${config.coinGeckoDemoApiKey}"
        )

        runtimeCache.underlyingAssetToPrice.keys.forEach { assetToPriceKey ->
            val fetchedUsdPrice =
                contractAddrToUsdPrice[assetToPriceKey.underlyingAssetAddress]?.usd?.toBigDecimal()
            fetchedUsdPrice?.run { runtimeCache.underlyingAssetToPrice[assetToPriceKey] = this }
        }
    }

    // try getting the rest by symbol with band protocol
    // The Band oracle REST endpoint.
    // The latest one can be found here: https://docs.bandchain.org/technical-specifications/band-endpoints.html
    val symbolsToFetch =
        runtimeCache.underlyingAssetToPrice.filter { it.value == 0.0.toBigDecimal() }.keys.map { it.lendMarketSymbol }
    require(symbolsToFetch.none { it.contains("SCRT") }) { "SCRT variants should be fetched by contract address" }
    val symbolsToFetchStr = symbolsToFetch.map { "symbols=${it}" }.joinToString("&")
    val prices: PriceResults = httpClient.getResponse(
        config.bandUrl, "oracle/v1/request_prices?$symbolsToFetchStr&x_cg_demo_api_key=${config.coinGeckoDemoApiKey}"
    )
    prices.priceResults.forEach { x ->
        val price = (x.px.toDouble().toBigDecimal() / x.multiplier.toDouble().toBigDecimal())
        require(runtimeCache.underlyingAssetToPrice.contains(symbolToAssetId(x.symbol))) {
            "symbolToContractAddr has an outdated contract address. Updated ${x.symbol}'s address"
        }
        runtimeCache.underlyingAssetToPrice[symbolToAssetId(x.symbol)] = price
    }
    println(runtimeCache.underlyingAssetToPrice.toMap())
}