package datalayer.sources.cache

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.eqoty.secret.std.types.Permit
import msg.overseer.LendOverseerMarketQueryAnswer
import types.LendOverseerMarket
import types.Loan
import types.UnderlyingAssetId

class RuntimeCache {
    var lendOverSeerMarketQueryAnswerToLendOverseerMarket: MutableMap<LendOverseerMarketQueryAnswer, LendOverseerMarket> =
        mutableMapOf()
    val lendOverseerMarkets get() = lendOverSeerMarketQueryAnswerToLendOverseerMarket.values.toList()

    val underlyingAssetToPrice = mutableStateMapOf<UnderlyingAssetId, BigDecimal>()
    var blockHeight = mutableStateOf(0.toBigInteger())
    var userMarketBalance = mutableStateMapOf<UnderlyingAssetId, BigInteger>()
    var userBalance = mutableStateMapOf<UnderlyingAssetId, BigInteger>()
    var marketToLoans = mutableStateMapOf<LendOverseerMarket, List<Loan>>()

    val senderToAddressPermitMap: MutableMap<String, MutableMap<String, Permit>> = mutableMapOf()
    val underlyingAssetToContractCodeHash: MutableMap<UnderlyingAssetId, String> = mutableMapOf()


    init {
        // We always need SCRT, in order to check gas costs, this is the sscrt contract
//        underlyingMarketAssetIds.add(sscrtAssetId)
    }

}