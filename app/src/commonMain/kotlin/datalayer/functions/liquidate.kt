package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.eqoty.secretk.types.MsgExecuteContract
import io.eqoty.secretk.types.TxOptions
import io.eqoty.secretk.types.response.TxResponseData
import kotlinx.serialization.encodeToString
import msg.market.ExecuteMsg
import types.LendOverseerMarket
import utils.json

suspend fun Repository.redeemToken(
    market: LendOverseerMarket,
    amount: BigInteger
): TxResponseData {
    val msg = MsgExecuteContract(
        sender = senderAddress,
        contractAddress = market.contract.address,
        codeHash = market.contract.codeHash,
        msg = json.encodeToString(
            ExecuteMsg(
                redeemToken = ExecuteMsg.RedeemToken(
                    burnAmount = amount
                )
            )
        ),
    )

    return client.execute(listOf(msg), txOptions = TxOptions(gasLimit = 300_000))
}

