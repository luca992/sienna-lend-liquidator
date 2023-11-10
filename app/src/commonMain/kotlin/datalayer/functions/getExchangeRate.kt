package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import json
import kotlinx.serialization.encodeToString
import msg.market.QueryMsg
import types.Market

suspend fun Repository.getExchangeRate(market: Market, blockHeight: BigInteger): BigDecimal {
    return json.decodeFromString<String>(
        client.queryContractSmart(
            contractAddress = market.contract.address,
            contractCodeHash = market.contract.codeHash,
            queryMsg = json.encodeToString(QueryMsg(exchangeRate = QueryMsg.ExchangeRate(blockHeight.ulongValue())))
        )
    ).toBigDecimal()
}

