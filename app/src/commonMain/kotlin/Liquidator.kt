import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import datalayer.functions.fetchUnderlyingMulticallAssets
import datalayer.functions.getBorrowers
import datalayer.functions.getExchangeRate
import kotlinx.serialization.encodeToString
import msg.overseer.LendOverseerConfig
import msg.overseer.LendOverseerMarket
import msg.overseer.QueryMsg
import types.Candidate
import types.LendConstants
import types.LendMarketBorrower
import types.Market
import utils.fetchAllPages

const val PRICES_UPDATE_INTERVAL = 3 * 60 * 1000
val BLACKLISTED_SYMBOLS = listOf("LUNA", "UST")

class Liquidator(
    val repo: Repository,
    private var markets: List<Market>,
    private var constants: LendConstants,
    private var storage: Storage,
//    private var manager: LiquidationsManager
) {

    private var isExecuting = false

    companion object {
        suspend fun create(repo: Repository): Liquidator {
            val client = repo.client
            val config = repo.config

            val msg = QueryMsg(
                config = QueryMsg.Config()
            )
            val overseerConfig: LendOverseerConfig = json.decodeFromString(
                client.queryContractSmart(
                    contractAddress = config.overseer.address,
                    contractCodeHash = config.overseer.codeHash,
                    queryMsg = json.encodeToString(msg)
                )
            )

            val allMarkets = fetchAllPages<LendOverseerMarket>({ pagination ->
                json.decodeFromString(
                    client.queryContractSmart(
                        contractAddress = config.overseer.address,
                        contractCodeHash = config.overseer.codeHash,
                        queryMsg = json.encodeToString(QueryMsg(markets = QueryMsg.Markets(pagination))),
                    )
                )
            }, 30u, { x -> !BLACKLISTED_SYMBOLS.contains(x.symbol) })

            val storage = Storage.init(repo, allMarkets.map { it.symbol }.toMutableSet())

            val assets = repo.fetchUnderlyingMulticallAssets(allMarkets)

            val markets = mutableListOf<Market>()

            allMarkets.forEachIndexed { i, market ->
                val m = Market(
                    contract = market.contract,
                    symbol = market.symbol,
                    decimals = market.decimals,
                    underlying = assets[i]
                )
                markets.add(m)
            }
            val constants = LendConstants(
                closeFactor = overseerConfig.close_factor.toBigDecimal(),
                premium = overseerConfig.premium.toBigDecimal()
            )
            return Liquidator(
                repo, markets, constants, storage
            )
        }
    }


    suspend fun runOnce() {
        return this.runLiquidationsRound()
    }

    fun stop() {
//        if (this.liquidations_handle) {
//            // clearInterval(this.liquidations_handle)
//            // clearInterval(this.prices_update_handle)
//        }
    }

    private suspend fun runLiquidationsRound() {
        if (isExecuting) {
            return
        }

        if (this.storage.userBalance.isZero()) {
            logger.i("Ran out of balance. Terminating...")
            this.stop()

            return
        }

        isExecuting = true

        try {
            storage.updateBlockHeight()

            val candidates = this.markets.map { x -> this.marketCandidate(x) }
//            const loans : Loan [] = []
//
//            for (const [i, candidate] of candidates.entries()) {
//                if (candidate) {
//                    loans.push({
//                        candidate,
//                        market: this.markets[i]
//                    })
//                }
//            }
//
//            const liquidation = await this.choose_liquidation(loans)
//
//            if (liquidation) {
//                await this.manager.liquidate(this.storage, liquidation)
//            }
        } catch (t: Throwable) {
            logger.e("Caught an error during liquidations round: ${t.message}")

            t.printStackTrace()
        } finally {
            isExecuting = false
        }
    }

    private suspend fun marketCandidate(market: Market): Candidate? {
        val candidates = fetchAllPages(
            { page -> repo.getBorrowers(market, page, storage.blockHeight) },
            1u,
            { x ->
                if (x.liquidity.shortfall == BigInteger.ZERO) return@fetchAllPages false

                x.markets = x.markets.filter { m -> !BLACKLISTED_SYMBOLS.contains(m.symbol) }

                return@fetchAllPages x.markets.isNotEmpty()
            })

        if (candidates.isEmpty()) {
            logger.i("No liquidatable loans currently in ${market.contract.address}. Skipping...")

            return null
        }

        return repo.findBestCandidate(market, candidates)
    }

    private suspend fun Repository.findBestCandidate(market: Market, borrowers: List<LendMarketBorrower>): Candidate? {
        val sortByPrice: Comparator<LendOverseerMarket> = Comparator { a, b ->
            val priceA = storage.prices[a.symbol]!!
            val priceB = storage.prices[b.symbol]!!
            if (priceA == priceB) {
                return@Comparator 0
            }
            return@Comparator if (priceA > priceB) 1 else -1
        }
        borrowers.forEach { x -> x.markets.sortedWith(sortByPrice) }

        val calc_net = { borrower: LendMarketBorrower ->
            val payable = maxPayable(borrower)

            payable.times(constants.premium)
                .times(storage.prices[borrower.markets[0].symbol]!!)
                .divide(storage.prices[market.symbol]!!)
        }
//
//        borrowers.sort((a, b) => {
//            const net_a = calc_net(a)
//            const net_b = calc_net(b)
//
//            if (net_a.isEqualTo(net_b)) {
//                return sort_by_price(a.markets[0], b.markets[0])
//            }
//
//            return net_b.minus(net_a).toNumber()
//        })
//
        val exchangeRate = repo.getExchangeRate(market, storage.blockHeight)
        var bestCandidate: Candidate? = null

        // Because we sort the borrowers based on the best case scenario
        // (where full liquidation is possible and receiving the best priced collateral)
        // we can only make assumptions about whether the current loan is the best one to liquidate
        // if we hit the best case scenario for it. So we compare loans in pairs, starting from the `hypothetical`
        // best one and stopping as soon as the best case was encountered for either loan in the current pair.
        // Otherwise, continue to the next pair.
        var i = 0
        do {
            val a = processCandidate(market, borrowers[i], exchangeRate)

            if (a.best_case || i == borrowers.size - 1) {
                bestCandidate = a.candidate

                break
            }

            val b = processCandidate(market, borrowers[i + 1], exchangeRate)

            if (b.candidate.seizable_usd > a.candidate.seizable_usd) {
                bestCandidate = b.candidate

                if (b.best_case) {
                    break
                }
            } else {
                bestCandidate = a.candidate

                break
            }

            i += 2
        } while (i < borrowers.size)

        if (bestCandidate != null && liquidationCostUsd() > bestCandidate.seizable_usd)
            return null

        return bestCandidate
    }

    data class ProcessCandidateResult(
        val best_case: Boolean,
        val candidate: Candidate
    )

    private suspend fun processCandidate(
        market: Market,
        borrower: LendMarketBorrower,
        exchange_rate: BigDecimal
    ): ProcessCandidateResult {

        TODO()
//        const payable = this.max_payable(borrower)
//
//        let best_seizable_usd = new BigNumber(0)
//        let best_payable = new BigNumber(0)
//        let market_index = 0
//
//        if (payable.lt(1)) {
//            return {
//                best_case: true,
//                candidate: {
//                id: borrower.id,
//                payable: best_payable,
//                seizable_usd: best_seizable_usd,
//                market_info: borrower.markets[market_index]
//            }
//            }
//        }
//
//        for (let i = 0; i < borrower.markets.length; i++) {
//            const m = borrower.markets[i]
//
//            // Values are in sl-tokens so we need to convert to
//            // the underlying in order for them to be useful here.
//            const info = await Tx.retry(() =>
//            market.contract.simulateLiquidation(
//                borrower.id,
//                m.contract.address,
//                payable.toFixed(0),
//                this.storage.block_height
//            )
//            )
//
//            const seizable = new BigNumber(info.seize_amount).multipliedBy(exchange_rate)
//
//            if (i == 0 && info.shortfall == '0') {
//                // We can liquidate using the most profitable asset so no need to go further.
//                return {
//                    best_case: true,
//                    candidate: {
//                    id: borrower.id,
//                    payable,
//                    seizable_usd: this.storage.usd_value(seizable, m.symbol, m.decimals),
//                    market_info: m
//                }
//                }
//            }
//
//            let actual_payable;
//            let actual_seizable_usd;
//
//            let done = false
//
//            if(info.shortfall == '0') {
//                actual_payable = payable
//                actual_seizable_usd = this.storage.usd_value(seizable, m.symbol, m.decimals)
//
//                // We don't have to check further since this is the second best scenario that we've got.
//                done = true
//            } else {
//                // Otherwise check by how much we'd need to decrease our repay amount in order for the
//                // liquidation to be successful and also decrease the seized amount by that percentage.
//                const actual_seizable = new BigNumber(info.seize_amount).minus(info.shortfall)
//
//                if (actual_seizable.isZero()) {
//                    actual_payable = new BigNumber(0)
//                    actual_seizable_usd = new BigNumber(0)
//                } else {
//                    const seizable_price = actual_seizable.multipliedBy(this.storage.prices[m.symbol]).multipliedBy(exchange_rate)
//                    const borrowed_premium = new BigNumber(this.constants.premium).multipliedBy(this.storage.prices[market.symbol])
//
//                    actual_payable = seizable_price.dividedBy(borrowed_premium)
//
//                    actual_seizable_usd = this.storage.usd_value(actual_seizable, m.symbol, m.decimals)
//                }
//            }
//
//            if (actual_seizable_usd.gt(best_seizable_usd)) {
//                best_payable = actual_payable
//                best_seizable_usd = actual_seizable_usd
//                market_index = i
//
//                if (done)
//                    break
//            }
//        }
//
//        return {
//            best_case: false,
//            candidate: {
//            id: borrower.id,
//            payable: best_payable,
//            seizable_usd: best_seizable_usd,
//            market_info: borrower.markets[market_index]
//        }
//        }
    }

    private fun maxPayable(borrower: LendMarketBorrower): BigDecimal {
        return BigDecimal.fromBigInteger(borrower.actualBalance) * constants.closeFactor
    }

    private fun liquidationCostUsd(): BigDecimal {
        return storage.gas_cost_usd(repo.config.gasCosts.liquidate.toBigDecimal())
    }

}

