package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import json
import kotlinx.serialization.encodeToString
import msg.market.LendSimulatedLiquidation
import msg.market.QueryMsg
import types.LendMarketBorrower
import types.LendOverseerMarketAndUnderlyingAsset

suspend fun Repository.getExchangeRate(market: LendOverseerMarketAndUnderlyingAsset, blockHeight: BigInteger): BigDecimal {
    return json.decodeFromString<String>(
        client.queryContractSmart(
            contractAddress = market.contract.address,
            contractCodeHash = market.contract.codeHash,
            queryMsg = json.encodeToString(QueryMsg(exchangeRate = QueryMsg.ExchangeRate(blockHeight.ulongValue())))
        )
    ).toBigDecimal()
}


suspend fun Repository.simulateLiquidation(
    market: LendOverseerMarketAndUnderlyingAsset,
    lendMarketBorrower: LendMarketBorrower,
    blockHeight: BigInteger,
    payable: BigDecimal,
): LendSimulatedLiquidation {
    return json.decodeFromString(
        client.queryContractSmart(
            contractAddress = market.contract.address,
            contractCodeHash = market.contract.codeHash,
            queryMsg = json.encodeToString(
                QueryMsg(
                    simulateLiquidation = QueryMsg.SimulateLiquidation(
                        blockHeight.ulongValue(),
                        borrower = lendMarketBorrower.id,
                        collateral = market.contract.address,
                        amount = payable
                            .roundToDigitPositionAfterDecimalPoint(0, RoundingMode.FLOOR)
                            .toBigInteger()
                            .toString()
                    )
                )
            )
        )
    )
}

