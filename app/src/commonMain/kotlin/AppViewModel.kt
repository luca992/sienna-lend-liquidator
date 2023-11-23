import androidx.compose.runtime.mutableStateOf
import datalayer.functions.updateUserBalance
import types.LendOverseerMarket
import types.Loan

class AppViewModel(val repo: Repository, private val liquidator: Liquidator) {

    val balance = repo.runtimeCache.userBalance
    val marketToLoans = repo.runtimeCache.marketToLoans
    val allLendMarkets = repo.runtimeCache.lendOverseerMarkets
    var selectedLendMarket = mutableStateOf(repo.runtimeCache.lendOverseerMarkets.first())

    suspend fun setSelectedLendMarket(lendMarket: LendOverseerMarket) {
        selectedLendMarket.value = lendMarket
        repo.updateUserBalance(lendMarket)
    }

    suspend fun getLoans() {
        liquidator.updateLiquidations(selectedLendMarket.value)
    }

    suspend fun liquidate(loan: Loan) {
        liquidator.liquidate(loan)
    }

    suspend fun init() {
        repo.updateUserBalance(selectedLendMarket.value)
    }

}