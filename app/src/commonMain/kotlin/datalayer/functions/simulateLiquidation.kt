package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.eqoty.secret.std.contract.msg.Snip20Msgs
import io.eqoty.secretk.types.MsgExecuteContract
import io.eqoty.secretk.types.TxOptions
import io.eqoty.secretk.types.response.TxResponseData
import io.ktor.util.*
import json
import kotlinx.serialization.encodeToString
import logger
import msg.market.ExecuteMsg
import msg.market.LendSimulatedLiquidation
import msg.market.QueryMsg
import types.LendMarketBorrower
import types.LendOverseerMarketAndUnderlyingAsset
import types.Loan

suspend fun Repository.getExchangeRate(
    market: LendOverseerMarketAndUnderlyingAsset,
    blockHeight: BigInteger
): BigDecimal {
    return json.decodeFromString<String>(
        client.queryContractSmart(
            contractAddress = market.contract.address,
            contractCodeHash = market.contract.codeHash,
            queryMsg = json.encodeToString(QueryMsg(exchangeRate = QueryMsg.ExchangeRate(blockHeight.ulongValue())))
        )
    ).toBigDecimal()
}

fun BigDecimal.toFixed(decimalPlaces: Long, roundingMode: RoundingMode = RoundingMode.FLOOR): String {
    return roundToDigitPositionAfterDecimalPoint(decimalPlaces, roundingMode)
        .toBigInteger()
        .toString()
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
                        amount = payable.toFixed(0)
                    )
                )
            )
        )
    )
}

suspend fun Repository.liquidate(
    loan: Loan,
): TxResponseData {
    val callbackB64 = json.encodeToString(
        ExecuteMsg(
            liquidate = ExecuteMsg.Liquidate(
                borrower = loan.candidate.id,
                collateral = loan.candidate.marketInfo.contract.address
            )
        )
    ).encodeBase64()
    val liquidateMsg = MsgExecuteContract(
        sender = senderAddress,
        contractAddress = loan.market.underlying.address,
        codeHash = loan.market.underlying.codeHash,
        msg = json.encodeToString(
            Snip20Msgs.Execute(
                send = Snip20Msgs.Execute.Send(
                    amount = loan.candidate.payable.toFixed(0).toBigInteger(),
                    recipient = loan.market.contract.address,
                    msg = callbackB64
                )
            )
        ),
    )

    logger.d { "Liquidate msg: ${liquidateMsg.msg}" }
    return client.execute(listOf(liquidateMsg), txOptions = TxOptions(gasLimit = config.gasCosts.liquidate))
}

