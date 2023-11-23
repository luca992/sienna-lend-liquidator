import types.Loan

class AppViewModel(val repo: Repository, private val liquidator: Liquidator) {

    val balance = repo.runtimeCache.userBalance
    val marketToLoans = repo.runtimeCache.marketToLoans
    val allLendMarkets = repo.runtimeCache.lendOverseerMarkets
    var selectedLendMarket = repo.runtimeCache.lendOverseerMarkets.first()
    suspend fun getLoans() {
        liquidator.updateLiquidations(selectedLendMarket)
    }

    suspend fun liquidate(loan: Loan) {
        liquidator.liquidate(loan)
    }

}