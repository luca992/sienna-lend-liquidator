import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import datalayer.functions.*
import kotlinx.serialization.encodeToString
import msg.overseer.LendOverseerConfig
import msg.overseer.LendOverseerMarketQueryAnswer
import msg.overseer.QueryMsg
import types.*
import utils.clamp
import utils.fetchAllPages
import utils.json

const val PRICES_UPDATE_INTERVAL = 3 * 60 * 1000
val BLACKLISTED_SYMBOLS = listOf("LUNA", "UST")

//"secret149e7c5j7w24pljg6em6zj2p557fuyhg8cnk7z8", // sLUNA Luna
//"secret1w8d0ntrhrys4yzcfxnwprts7gfg5gfw86ccdpf", // sLUNA2 Luna
//"secret1qem6e0gw2wfuzyr9sgthykvk0zjcrzm6lu94ym", // sUSTC  Terra
val ASSETS_TO_IGNORE_SEIZING: List<UnderlyingAssetId> =
    listOf(/*stkdScrtAssetId,*//* symbolToAssetId("USDT")*//*, symbolToAssetId("USDC")*/ symbolToAssetId("XMR"))

class Liquidator(
    val repo: Repository,
    private var constants: LendConstants,
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

            val allMarkets = fetchAllPages<LendOverseerMarketQueryAnswer>({ pagination ->
                json.decodeFromString(
                    client.queryContractSmart(
                        contractAddress = config.overseer.address,
                        contractCodeHash = config.overseer.codeHash,
                        queryMsg = json.encodeToString(QueryMsg(markets = QueryMsg.Markets(pagination))),
                    )
                )
            }, 30u, { x -> !BLACKLISTED_SYMBOLS.contains(x.symbol) })

            val assets = repo.fetchUnderlyingMulticallAssets(allMarkets)
            val lendOverseerMarketQueryAnswerToLendOverseerMarket =
                allMarkets.zip(assets).map { (lendOverseerMarketQueryAnswer, underlyingAsset) ->
                    lendOverseerMarketQueryAnswer to LendOverseerMarket(
                        contract = lendOverseerMarketQueryAnswer.contract,
                        symbol = lendOverseerMarketQueryAnswer.symbol,
                        decimals = lendOverseerMarketQueryAnswer.decimals,
                        ltvRatio = lendOverseerMarketQueryAnswer.ltvRatio,
                        underlying = underlyingAsset
                    )
                }.toMap()
            repo.runtimeCache.lendOverSeerMarketQueryAnswerToLendOverseerMarket.putAll(
                lendOverseerMarketQueryAnswerToLendOverseerMarket
            )
            repo.updatePrices()
            repo.updateBlockHeight()

            val constants = LendConstants(
                closeFactor = overseerConfig.close_factor.toBigDecimal(),
                premium = overseerConfig.premium.toBigDecimal()
            )
            return Liquidator(
                repo, constants,
            )
        }
    }


    suspend fun updateLiquidations(specificMarket: LendOverseerMarket?, clampToWalletBalance: Boolean) {
        if (isExecuting) {
            return
        }
        repo.updateUserBalance(specificMarket?.let { listOf(it) } ?: repo.runtimeCache.lendOverseerMarkets,
            BalanceType.UnderlyingAsset)

        isExecuting = true

        try {
            repo.updateBlockHeight()

            val chosenMarkets = if (specificMarket != null) {
                repo.runtimeCache.lendOverseerMarkets.filter { x -> x == specificMarket }
            } else {
                repo.runtimeCache.lendOverseerMarkets
            }
            val marketCandidates = chosenMarkets.map { x -> marketCandidates(x, clampToWalletBalance) }


            marketCandidates.forEachIndexed { i, candidates ->
                val loans = mutableListOf<Loan>()
                candidates.forEach { candidate ->
                    loans.add(
                        Loan(candidate, market = chosenMarkets[i])
                    )
                }
                repo.runtimeCache.marketToLoans[chosenMarkets[i]] = loans
                logger.i("Found ${loans.size} liquidatable loans in ${chosenMarkets[i].underlyingAssetId.snip20Symbol} Lend Market")
            }

//            val liquidation = await this.choose_liquidation(loans)
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

    private suspend fun marketCandidates(market: LendOverseerMarket, clampToWalletBalance: Boolean): List<Candidate> {
        val candidates = fetchAllPages({ page ->
            repo.getBorrowers(market, page)
        }, 1u, { x ->
            if (x.liquidity.shortfall == BigInteger.ZERO) return@fetchAllPages false

            x.markets = x.markets.filter { m ->
                val cachedMarketId =
                    repo.runtimeCache.lendOverSeerMarketQueryAnswerToLendOverseerMarket[m]?.underlyingAssetId
                !BLACKLISTED_SYMBOLS.contains(m.symbol) && !ASSETS_TO_IGNORE_SEIZING.contains(cachedMarketId)
            }

            return@fetchAllPages x.markets.isNotEmpty()
        }).map {
            it.toLendMarketBorrower(repo.runtimeCache)
        }.toMutableList()


        if (candidates.isEmpty()) {
            logger.i("No liquidatable loans currently in ${market.contract.address}. Skipping...")

            return emptyList()
        }

        return findCandidates(market, candidates, clampToWalletBalance)
    }

    private suspend fun findCandidates(
        market: LendOverseerMarket, borrowers: MutableList<LendMarketBorrower>, clampToWalletBalance: Boolean
    ): List<Candidate> {
        // sorts the markets for each borrower by price in descending order (largest price first)
        val sortByPrice: Comparator<LendOverseerMarket> = Comparator { a, b ->
            val priceA = repo.runtimeCache.underlyingAssetToPrice[a.underlyingAssetId]!!
            val priceB = repo.runtimeCache.underlyingAssetToPrice[b.underlyingAssetId]!!
            if (priceA == priceB) {
                return@Comparator 0
            }
            return@Comparator if (priceA < priceB) 1 else -1
        }
        borrowers.forEach { x -> x.markets.sortWith(sortByPrice) }

        val calcNet = { borrower: LendMarketBorrower ->
            // the current amount the borrower has to repay to the market in the markets currency with a max of the
            // clamped at the amount the person running this liquidator actually in their balance
            val payable = maxPayable(market, borrower, clampToWalletBalance)

            (payable * constants.premium * repo.runtimeCache.underlyingAssetToPrice[borrower.markets[0].underlyingAssetId]!!).divide(
                repo.runtimeCache.underlyingAssetToPrice[market.underlyingAssetId]!!,
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

        return borrowers.map { processCandidates(market, it, clampToWalletBalance) }.flatten()
            .filter { it.seizable > BigInteger.ZERO }
//        var bestCandidate: Candidate? = null
//
//        // Because we sort the borrowers based on the best case scenario
//        // (where full liquidation is possible and receiving the best priced collateral)
//        // we can only make assumptions about whether the current loan is the best one to liquidate
//        // if we hit the best case scenario for it. So we compare loans in pairs, starting from the `hypothetical`
//        // best one and stopping as soon as the best case was encountered for either loan in the current pair.
//        // Otherwise, continue to the next pair.
//        var i = 0
//        do {
//            val a = processCandidate(market, borrowers[i], exchangeRate)
//
//            if (a.best_case || i == borrowers.size - 1) {
//                bestCandidate = a.candidate
//
//                break
//            }
//
//            val b = processCandidate(market, borrowers[i + 1], exchangeRate)
//
//            if (b.candidate.seizableUsd > a.candidate.seizableUsd) {
//                bestCandidate = b.candidate
//
//                if (b.best_case) {
//                    break
//                }
//            } else {
//                bestCandidate = a.candidate
//            }
//
//            i += 2
//        } while (i < borrowers.size)
//
//        if (bestCandidate != null && liquidationCostUsd() > bestCandidate.seizableUsd) return null
//
//        return bestCandidate
    }


    private suspend fun processCandidates(
        market: LendOverseerMarket, borrower: LendMarketBorrower, clampToWalletBalance: Boolean
    ): List<Candidate> {
        val payable = maxPayable(market, borrower, clampToWalletBalance)

        val candidates = mutableListOf<Candidate>()
        if (payable < 1) {
            return candidates
        }


        for (i in 0 until borrower.markets.size) {
            val m = borrower.markets[i]

            val info = repo.simulateLiquidation(market, borrower.id, m, payable)

            val exchange_rate = repo.getExchangeRate(m)
            // Values are in sl-tokens, so we need to convert to
            // the underlying in order for them to be useful here.
            val seizable = BigDecimal.fromBigInteger(info.seize_amount).times(exchange_rate)


            val actual_payable: BigDecimal
            val actual_seizable: BigDecimal
            val actual_seizable_usd: BigDecimal


            if (info.shortfall == BigInteger.ZERO) {
                actual_payable = payable
                actual_seizable = seizable
                actual_seizable_usd = repo.usdValue(seizable, m.underlyingAssetId, m.decimals)
            } else {
                // Otherwise check by how much we'd need to decrease our repay amount in order for the
                // liquidation to be successful and also decrease the seized amount by that percentage.
                actual_seizable = BigDecimal.fromBigInteger(info.seize_amount - info.shortfall) * exchange_rate

                if (actual_seizable.isZero()) {
                    actual_payable = BigDecimal.ZERO
                    actual_seizable_usd = BigDecimal.ZERO
                } else {
//                    val seizable_price =
//                        actual_seizable * storage.underlyingAssetToPrice[m.underlyingAssetId]!!
//                    val borrowed_premium =
//                        constants.premium * storage.underlyingAssetToPrice[market.underlyingAssetId]!!

//                    actual_payable = seizable_price.divide(borrowed_premium, DecimalMode(15, RoundingMode.ROUND_HALF_CEILING))

                    actual_payable = (payable * BigDecimal.fromBigInteger(info.seize_amount - info.shortfall).divide(
                        BigDecimal.fromBigInteger(info.seize_amount), DecimalMode(15, RoundingMode.FLOOR)
                    ))

                    actual_seizable_usd = repo.usdValue(actual_seizable, m.underlyingAssetId, m.decimals)
                }
            }

            candidates.add(
                Candidate(
                    id = borrower.id,
                    payable = actual_payable,
                    payableUsd = repo.usdValue(actual_payable, market.underlyingAssetId, market.decimals),
                    seizable = actual_seizable.toBigInteger(),
                    seizableUsd = actual_seizable_usd,
                    marketInfo = borrower.markets[i],
                    totalPayable = (BigDecimal.fromBigInteger(borrower.actualBalance) * constants.closeFactor).toBigInteger(),
                    clampedToWalletBalance = clampToWalletBalance
                )
            )
        }

        return candidates
    }


    /***
     * @param clamp if true clamp the payable amount to the user's actual balance for the market's underlying asset
     */
    private fun maxPayable(
        market: LendOverseerMarket, borrower: LendMarketBorrower, clamp: Boolean
    ): BigDecimal {
        return BigDecimal.fromBigInteger(
            when (clamp) {
                true -> clamp(
                    (BigDecimal.fromBigInteger(borrower.actualBalance) * constants.closeFactor).toFixed(0)
                        .toBigInteger(), repo.runtimeCache.userBalance[market.underlyingAssetId] ?: BigInteger.ZERO
                )

                false -> (BigDecimal.fromBigInteger(borrower.actualBalance) * constants.closeFactor).toFixed(0)
                    .toBigInteger()
            }

        )
    }

    private fun liquidationCostUsd(): BigDecimal {
        return repo.gasCostUsd(repo.config.gasCosts.liquidate.toBigDecimal())
    }


    suspend fun liquidate(loan: Loan) {
        logger.i("Attempting to liquate loan: ${loan.candidate.id}")
        val simulatedLiquidation = repo.simulateLiquidation(
            loan.market,
            loan.candidate.id,
            loan.candidate.marketInfo,
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

        repo.updateUserBalance(loan.market, BalanceType.UnderlyingAsset)
    }
}

