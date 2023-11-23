import androidx.compose.runtime.mutableStateOf
import com.ionspin.kotlin.bignum.integer.BigInteger
import datalayer.functions.getLendMarketState
import datalayer.functions.updateUserBalance
import types.LendOverseerMarket
import types.Loan

class AppViewModel(val repo: Repository, private val liquidator: Liquidator) {

    val balance = repo.runtimeCache.userBalance
    val marketToLoans = repo.runtimeCache.marketToLoans
    val allLendMarkets = repo.runtimeCache.lendOverseerMarkets
    var selectedLendMarket = mutableStateOf(repo.runtimeCache.lendOverseerMarkets.first())
    var clampToWalletBalance = mutableStateOf(false)
    var marketUnderlyingBalance = mutableStateOf<BigInteger?>(null)

    suspend fun setSelectedLendMarket(lendMarket: LendOverseerMarket) {
        selectedLendMarket.value = lendMarket
        repo.updateUserBalance(lendMarket)
        marketUnderlyingBalance.value = repo.getLendMarketState(lendMarket)?.underlyingBalance
    }

    suspend fun getLoans() {
        liquidator.updateLiquidations(selectedLendMarket.value, clampToWalletBalance.value)
    }

    suspend fun liquidate(loan: Loan) {
        liquidator.liquidate(loan)
    }

    suspend fun init() {
        repo.updateUserBalance(selectedLendMarket.value)
        marketUnderlyingBalance.value = repo.getLendMarketState(selectedLendMarket.value)?.underlyingBalance
    }

}