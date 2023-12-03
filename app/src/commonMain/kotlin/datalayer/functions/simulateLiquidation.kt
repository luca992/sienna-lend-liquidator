package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.eqoty.secret.std.contract.msg.Snip20Msgs
import io.eqoty.secretk.types.MsgExecuteContract
import io.eqoty.secretk.types.TxOptions
import io.eqoty.secretk.types.response.TxResponseData
import io.ktor.util.*
import kotlinx.serialization.encodeToString
import msg.market.ExecuteMsg
import msg.market.LendSimulatedLiquidation
import msg.market.QueryMsg
import types.LendOverseerMarket
import types.Loan
import utils.json

fun BigDecimal.toFixed(decimalPlaces: Long, roundingMode: RoundingMode = RoundingMode.FLOOR): String {
    return roundToDigitPositionAfterDecimalPoint(decimalPlaces, roundingMode).toBigInteger().toString()
}

suspend fun Repository.simulateLiquidation(
    market: LendOverseerMarket,
    borrowerId: String,
    borrowersCollateralMarket: LendOverseerMarket,
    payable: BigDecimal,
): LendSimulatedLiquidation {
    updateBlockHeight()
    val blockHeight = runtimeCache.blockHeight.value
    return json.decodeFromString(
        client.queryContractSmart(
            contractAddress = market.contract.address,
            contractCodeHash = market.contract.codeHash,
            queryMsg = json.encodeToString(
                QueryMsg(
                    simulateLiquidation = QueryMsg.SimulateLiquidation(
                        blockHeight.ulongValue(),
                        borrower = borrowerId,
                        collateral = borrowersCollateralMarket.contract.address,
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
                borrower = loan.candidate.id, collateral = loan.candidate.marketInfo.contract.address
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

    return client.execute(listOf(liquidateMsg), txOptions = TxOptions(gasLimit = config.gasCosts.liquidate))
}

