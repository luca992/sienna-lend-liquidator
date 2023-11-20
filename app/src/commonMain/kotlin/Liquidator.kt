import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import datalayer.functions.*
import kotlinx.serialization.encodeToString
import msg.overseer.LendOverseerConfig
import msg.overseer.LendOverseerMarket
import msg.overseer.QueryMsg
import types.*
import utils.fetchAllPages

const val PRICES_UPDATE_INTERVAL = 3 * 60 * 1000
val BLACKLISTED_SYMBOLS = listOf("LUNA", "UST", "AAVE")

//"secret149e7c5j7w24pljg6em6zj2p557fuyhg8cnk7z8", // sLUNA Luna
//"secret1w8d0ntrhrys4yzcfxnwprts7gfg5gfw86ccdpf", // sLUNA2 Luna
//"secret1qem6e0gw2wfuzyr9sgthykvk0zjcrzm6lu94ym", // sUSTC  Terra
val ASSETS_TO_IGNORE_SEIZING: List<UnderlyingAssetId> =
    listOf(stkdScrtAssetId, symbolToAssetId("USDT"), /* symbolToAssetId("USDC")*/)

class Liquidator(
    val repo: Repository,
    private var constants: LendConstants,
    var storage: Storage,
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

            val assets = repo.fetchUnderlyingMulticallAssets(allMarkets)
            val marketToMarketAndUnderlyingAsset = allMarkets.zip(assets).map { (lendOverseerMarket, underlyingAsset) ->
                lendOverseerMarket to LendOverseerMarketAndUnderlyingAsset(
                    contract = lendOverseerMarket.contract,
                    symbol = lendOverseerMarket.symbol,
                    decimals = lendOverseerMarket.decimals,
                    ltvRatio = lendOverseerMarket.ltvRatio,
                    underlying = underlyingAsset
                )
            }.toMap()

            val storage = Storage.init(repo, marketToMarketAndUnderlyingAsset)

            val constants = LendConstants(
                closeFactor = overseerConfig.close_factor.toBigDecimal(),
                premium = overseerConfig.premium.toBigDecimal()
            )
            return Liquidator(
                repo, constants, storage
            )
        }
    }


    suspend fun runOnce(specificMarket: UnderlyingAssetId? = null): List<Loan> {
        return this.runLiquidationsRound(specificMarket)
    }

    fun stop() {
//        if (this.liquidations_handle) {
//            // clearInterval(this.liquidations_handle)
//            // clearInterval(this.prices_update_handle)
//        }
    }

    private suspend fun runLiquidationsRound(specificMarket: UnderlyingAssetId?): List<Loan> {
        if (isExecuting) {
            return emptyList()
        }

        if (this.storage.userBalance.value.isZero()) {
            logger.i("Ran out of balance. Terminating...")
            this.stop()

            return emptyList()
        }

        isExecuting = true

        return try {
            storage.updateBlockHeight()

            val chosenMarkets = if (specificMarket != null) {
                storage.markets.filter { x -> x.underlyingAssetId == specificMarket }
            } else {
                storage.markets
            }
            val candidates = chosenMarkets.map { x -> marketCandidate(x) }
            val loans = mutableListOf<Loan>()

            candidates.forEachIndexed { i, candidate ->
                if (candidate != null) {
                    loans.add(
                        Loan(candidate, market = chosenMarkets[i])
                    )
                }
            }
            logger.i(loans.toString())
            loans

//            val liquidation = await this.choose_liquidation(loans)
//
//            if (liquidation) {
//                await this.manager.liquidate(this.storage, liquidation)
//            }
        } catch (t: Throwable) {
            logger.e("Caught an error during liquidations round: ${t.message}")

            t.printStackTrace()
            emptyList()
        } finally {
            isExecuting = false
        }
    }

    private suspend fun marketCandidate(market: LendOverseerMarketAndUnderlyingAsset): Candidate? {
        val candidates = fetchAllPages({ page ->
            repo.getBorrowers(market, page, storage.blockHeight)
        }, 1u, { x ->
            if (x.liquidity.shortfall == BigInteger.ZERO) return@fetchAllPages false

            x.markets = x.markets.filter { m ->
                val cachedMarketId = storage.marketToMarketAndUnderlyingAsset[m]?.underlyingAssetId
                !BLACKLISTED_SYMBOLS.contains(m.symbol) && !ASSETS_TO_IGNORE_SEIZING.contains(cachedMarketId)
            }

            return@fetchAllPages x.markets.isNotEmpty()
        }).map {
            it.toLendMarketBorrower(storage)
        }.toMutableList()


        if (candidates.isEmpty()) {
            logger.i("No liquidatable loans currently in ${market.contract.address}. Skipping...")

            return null
        }

        return findBestCandidate(market, candidates)
    }

    private suspend fun findBestCandidate(
        market: LendOverseerMarketAndUnderlyingAsset, borrowers: MutableList<LendMarketBorrower>
    ): Candidate? {
        // sorts the markets for each borrower by price in descending order (largest price first)
        val sortByPrice: Comparator<LendOverseerMarketAndUnderlyingAsset> = Comparator { a, b ->
            val priceA = storage.underlyingAssetToPrice[a.underlyingAssetId]!!
            val priceB = storage.underlyingAssetToPrice[b.underlyingAssetId]!!
            if (priceA == priceB) {
                return@Comparator 0
            }
            return@Comparator if (priceA < priceB) 1 else -1
        }
        borrowers.forEach { x -> x.markets.sortWith(sortByPrice) }

        val calcNet = { borrower: LendMarketBorrower ->
            // the current amount the borrower has to repay to the market in the markets currency with a max of the
            // clamped at the amount the person running this liquidator actually in their balance
            val payable = maxPayable(borrower)

            (payable * constants.premium * storage.underlyingAssetToPrice[borrower.markets[0].underlyingAssetId]!!).divide(
                storage.underlyingAssetToPrice[market.underlyingAssetId]!!,
                DecimalMode(15, RoundingMode.ROUND_HALF_CEILING)
            )
        }

        // now sort the borrowers
        borrowers.sortWith { a, b ->
            val netA = calcNet(a)
            val netB = calcNet(b)

            if (netA == netB) {
                return@sortWith sortByPrice.compare(a.markets[0], b.markets[0])
            }

            return@sortWith if (netA < netB) 1 else -1
        }

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

            if (b.candidate.seizableUsd > a.candidate.seizableUsd) {
                bestCandidate = b.candidate

                if (b.best_case) {
                    break
                }
            } else {
                bestCandidate = a.candidate
            }

            i += 2
        } while (i < borrowers.size)

        if (bestCandidate != null && liquidationCostUsd() > bestCandidate.seizableUsd) return null

        return bestCandidate
    }

    data class ProcessCandidateResult(
        val best_case: Boolean, val candidate: Candidate
    )

    private suspend fun processCandidate(
        market: LendOverseerMarketAndUnderlyingAsset, borrower: LendMarketBorrower, exchange_rate: BigDecimal
    ): ProcessCandidateResult {
        val payable = maxPayable(borrower)

        var bestSeizable = BigInteger.ZERO
        var bestSeizableUsd = BigDecimal.ZERO
        var bestPayable = BigDecimal.ZERO
        var marketIndex = 0

        if (payable < 1) {
            return ProcessCandidateResult(
                best_case = true,
                candidate = Candidate(
                    id = borrower.id,
                    payable = payable,
                    payableUsd = payable,
                    seizable = bestSeizable,
                    seizableUsd = bestSeizableUsd,
                    marketInfo = borrower.markets[marketIndex]
                ),
            )
        }

        borrower.markets.forEachIndexed { i, m ->

            // Values are in sl-tokens so we need to convert to
            // the underlying in order for them to be useful here.
            val info = repo.simulateLiquidation(market, borrower.id, m, storage.blockHeight, payable)

            val seizable = BigDecimal.fromBigInteger(info.seize_amount).times(exchange_rate)

            if (i == 0 && info.shortfall == BigInteger.ZERO) {
                // We can liquidate using the most profitable asset so no need to go further.
                return ProcessCandidateResult(
                    best_case = true,
                    candidate = Candidate(
                        id = borrower.id,
                        payable = payable,
                        payableUsd = storage.usdValue(payable, market.underlyingAssetId, market.decimals),
                        seizable = seizable.toBigInteger(),
                        seizableUsd = storage.usdValue(seizable, m.underlyingAssetId, m.decimals),
                        marketInfo = m
                    ),
                )
            }


            val actual_payable: BigDecimal
            val actual_seizable: BigInteger
            val actual_seizable_usd: BigDecimal

            var done = false

            if (info.shortfall == BigInteger.ZERO) {
                actual_payable = payable
                actual_seizable = seizable.toBigInteger()
                actual_seizable_usd = storage.usdValue(seizable, m.underlyingAssetId, m.decimals)

                // We don't have to check further since this is the second best scenario that we've got.
                done = true
            } else {
                // Otherwise check by how much we'd need to decrease our repay amount in order for the
                // liquidation to be successful and also decrease the seized amount by that percentage.
                actual_seizable = info.seize_amount - info.shortfall

                if (actual_seizable.isZero()) {
                    actual_payable = BigDecimal.ZERO
                    actual_seizable_usd = BigDecimal.ZERO
                } else {
                    val seizable_price =
                        BigDecimal.fromBigInteger(actual_seizable) * storage.underlyingAssetToPrice[m.underlyingAssetId]!! * exchange_rate
                    val borrowed_premium =
                        constants.premium * storage.underlyingAssetToPrice[market.underlyingAssetId]!!

                    actual_payable =
                        seizable_price.divide(borrowed_premium, DecimalMode(15, RoundingMode.ROUND_HALF_CEILING))

                    actual_seizable_usd =
                        storage.usdValue(BigDecimal.fromBigInteger(actual_seizable), m.underlyingAssetId, m.decimals)
                }
            }

            if (actual_seizable_usd > bestSeizableUsd) {
                bestPayable = actual_payable
                bestSeizable = actual_seizable
                bestSeizableUsd = actual_seizable_usd
                marketIndex = i

                if (done) return@forEachIndexed
            }
        }

        return ProcessCandidateResult(
            best_case = false,
            candidate = Candidate(
                id = borrower.id,
                payable = bestPayable,
                payableUsd = storage.usdValue(bestPayable, market.underlyingAssetId, market.decimals),
                seizable = bestSeizable,
                seizableUsd = bestSeizableUsd,
                marketInfo = borrower.markets[marketIndex]
            ),
        )
    }


    private fun maxPayable(borrower: LendMarketBorrower): BigDecimal {
        return BigDecimal.fromBigInteger(
            clamp(
                (BigDecimal.fromBigInteger(borrower.actualBalance) * constants.closeFactor).toFixed(0).toBigInteger(),
                storage.userBalance.value
            )
        )
    }

    private fun clamp(value: BigInteger, max: BigInteger): BigInteger {
        return if (value > max) {
            max
        } else {
            value
        }
    }

    private fun liquidationCostUsd(): BigDecimal {
        return storage.gasCostUsd(repo.config.gasCosts.liquidate.toBigDecimal())
    }


    suspend fun liquidate(loan: Loan) {
        logger.i("Attempting to liquate loan: ${loan.candidate.id}")
        val simulatedLiquidation = repo.simulateLiquidation(
            loan.market,
            loan.candidate.id,
            loan.candidate.marketInfo,
            storage.blockHeight,
            loan.candidate.payable
        )
        logger.i("Simulated liquidation: $simulatedLiquidation")
        if (simulatedLiquidation.shortfall != BigInteger.ZERO) {
            logger.i("Failed to simulate liquidate loan: ${loan.candidate.id}. Shortfall not zero: ${simulatedLiquidation.shortfall}")
            return
        }
        val response = repo.liquidate(loan)
        val repaidAmount = loan.candidate.payable.toPlainString()
        logger.i("Successfully liquidated a loan by repaying $repaidAmount ${loan.market.symbol} and seized ~$${loan.candidate.seizableUsd} worth of ${loan.candidate.marketInfo.symbol} (transfered to market: ${loan.candidate.marketInfo.contract.address})!")
        logger.i("TX hash: ${response.txhash}")

        storage.updateUserBalance()
    }
}

