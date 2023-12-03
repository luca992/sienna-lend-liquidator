import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.encodeToString
import msg.market.QueryMsg
import types.LendOverseerMarket
import utils.json

val exchangeRateCache = mutableMapOf<Pair<LendOverseerMarket, BigInteger>, BigDecimal>()
suspend fun Repository.getExchangeRate(
    market: LendOverseerMarket
): BigDecimal {
    val blockHeight = runtimeCache.blockHeight.value
    return exchangeRateCache.getOrPut(market to blockHeight) {
        json.decodeFromString<String>(
            client.queryContractSmart(
                contractAddress = market.contract.address,
                contractCodeHash = market.contract.codeHash,
                queryMsg = json.encodeToString(QueryMsg(exchangeRate = QueryMsg.ExchangeRate(blockHeight.ulongValue())))
            )
        ).toBigDecimal()
    }
}
