package utils

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.math.pow


fun normalizeDenom(amount: BigDecimal, decimals: UInt): BigDecimal {
    return amount.divide(BigDecimal.fromDouble(10.0.pow(decimals.toInt())))
}