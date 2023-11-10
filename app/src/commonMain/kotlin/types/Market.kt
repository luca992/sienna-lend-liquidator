package types;

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.eqoty.cosmwasm.std.types.ContractInfo
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import msg.overseer.LendOverseerMarket

data class Market(
    val contract: ContractInfo,
    val symbol: String,
    val decimals: UInt,
    val underlying: ContractInfo,
)

data class Candidate(
    val id: String, val payable: BigDecimal, val seizable_usd: BigDecimal, val market_info: LendOverseerMarket
)

data class LendConstants(
    val closeFactor: BigDecimal,
    val premium: BigDecimal,
)

@Serializable
data class LendMarketBorrower(
    val id: String,
    /** Borrow balance at the last interaction of the borrower. */
    @Contextual val principalBalance: BigInteger,
    /** Current borrow balance. */
    @Contextual val actualBalance: BigInteger,
    val liquidity: LendAccountLiquidity,
    var markets: List<LendOverseerMarket>
)

@Serializable
data class LendAccountLiquidity(
    /** The USD value borrowable by the user, before it reaches liquidation. */
    @Contextual val liquidity: BigInteger,
    /** If > 0 the account is currently below the collateral requirement and is subject to liquidation. */
    @Contextual val shortfall: BigInteger
)