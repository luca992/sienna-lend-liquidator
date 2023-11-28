package utils

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.math.pow

fun normalizeDenom(amount: BigDecimal, decimals: UInt): BigDecimal {
    return amount.divide(BigDecimal.fromDouble(10.0.pow(decimals.toInt())))
}

fun clamp(value: BigInteger, max: BigInteger): BigInteger {
    return if (value > max) {
        max
    } else {
        value
    }
}