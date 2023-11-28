import androidx.compose.runtime.mutableStateOf
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import datalayer.functions.*
import types.LendOverseerMarket
import types.Loan
import utils.clamp

class AppViewModel(val repo: Repository, private val liquidator: Liquidator) {

    val userUnderlyingAssetBalance = repo.runtimeCache.userBalance
    val userMarketBalance = repo.runtimeCache.userMarketBalance
    var userMarketBalanceSlConversion = mutableStateOf<BigInteger?>(null)

    val marketToLoans = repo.runtimeCache.marketToLoans
    val allLendMarkets = repo.runtimeCache.lendOverseerMarkets
    var selectedLendMarket = mutableStateOf(repo.runtimeCache.lendOverseerMarkets.first())
    var clampToWalletBalance = mutableStateOf(false)
    var marketUnderlyingBalance = mutableStateOf<BigInteger?>(null)
    var marketExchangeRate = mutableStateOf<BigDecimal?>(null)
    var marketUnderlyingBalanceSlConversion = mutableStateOf<BigInteger?>(null)

    suspend fun setSelectedLendMarket(lendMarket: LendOverseerMarket) {
        marketUnderlyingBalance.value = null
        marketExchangeRate.value = null
        selectedLendMarket.value = lendMarket
        repo.updateUserBalance(lendMarket, BalanceType.UnderlyingAsset)
        updateMarketBalance()
        updateUserMarketBalance()
    }

    private suspend fun updateUserMarketBalance() {
        repo.updateUserBalance(selectedLendMarket.value, BalanceType.Market)
        userMarketBalanceSlConversion.value =
            (BigDecimal.fromBigInteger(userMarketBalance[selectedLendMarket.value.underlyingAssetId]!!)
                .divide(marketExchangeRate.value!!, DecimalMode(15, RoundingMode.FLOOR))).toBigInteger()
    }

    private suspend fun updateMarketBalance() {
        marketUnderlyingBalance.value = repo.getLendMarketState(selectedLendMarket.value)?.underlyingBalance
        marketExchangeRate.value = repo.getExchangeRate(selectedLendMarket.value)
        marketUnderlyingBalanceSlConversion.value = (BigDecimal.fromBigInteger(marketUnderlyingBalance.value!!)
            .divide(marketExchangeRate.value!!, DecimalMode(15, RoundingMode.FLOOR))).toBigInteger()
    }


    suspend fun getLoans() {
        liquidator.updateLiquidations(selectedLendMarket.value, clampToWalletBalance.value)
    }

    suspend fun liquidate(loan: Loan) {
        liquidator.liquidate(loan)
    }

    suspend fun init() {
        setSelectedLendMarket(selectedLendMarket.value)
    }

    suspend fun withdrawMaxUserBalance() {
        updateMarketBalance()
        val maxWithdrawal = clamp(userMarketBalanceSlConversion.value!!, (BigDecimal.fromBigInteger(marketUnderlyingBalanceSlConversion.value!!) * 0.999.toBigDecimal()).toBigInteger())
        repo.redeemToken(selectedLendMarket.value, maxWithdrawal)
        updateMarketBalance()
        updateUserMarketBalance()
    }

}