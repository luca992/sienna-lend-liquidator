package msg.market

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable


@Serializable
data class LendSimulatedLiquidation(
    /** The amount that would be seized by that liquidation minus protocol fees.  */
    @Contextual val seize_amount: BigInteger,
    /** If the liquidation would be unsuccessful this will contain amount by which the seize amount falls flat. Otherwise, it's 0.  */
    @Contextual val shortfall: BigInteger,
)
