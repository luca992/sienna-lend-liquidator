package types;

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.eqoty.cosmwasm.std.types.ContractInfo
import msg.market.LendAccountLiquidity

data class LendOverseerMarketAndUnderlyingAsset(
    val contract: ContractInfo,
    val symbol: String,
    val decimals: UInt,
    val ltvRatio: String,
    val underlying: ContractInfo,
) {
    val underlyingAssetId = UnderlyingAssetId(underlying.address, symbol)
}

data class Candidate(
    val id: String,
    val payable: BigDecimal,
    val payableUsd: BigDecimal,
    val seizable: BigInteger,
    val seizableUsd: BigDecimal,
    val marketInfo: LendOverseerMarketAndUnderlyingAsset,
    val totalPayable: BigInteger
)

data class LendConstants(
    val closeFactor: BigDecimal,
    val premium: BigDecimal,
)


data class LendMarketBorrower(
    val id: String,
    /** Borrow balance at the last interaction of the borrower. */
    val principalBalance: BigInteger,
    /** Current borrow balance. */
    val actualBalance: BigInteger,
    val liquidity: LendAccountLiquidity,
    var markets: MutableList<LendOverseerMarketAndUnderlyingAsset>
)

data class Loan(
    val candidate: Candidate,
    val market: LendOverseerMarketAndUnderlyingAsset,
)

data class UnderlyingAssetId(
    val underlyingAssetAddress: String,
    val symbol: String = addressToUnderlyingAssetId(underlyingAssetAddress).symbol,
)

const val scrtVariantSymbol = "SCRT"
val sscrtAssetId = UnderlyingAssetId("secret1k0jntykt7e4g3y88ltc60czgjuqdy4c9e8fzek", "SCRT")
val stkdScrtAssetId = UnderlyingAssetId("secret1k6u0cy4feepm6pehnz804zmwakuwdapm69tuc4", "SCRT")
val seScrtAssetId = UnderlyingAssetId("secret16zfat8th6hvzhesj8f6rz3vzd7ll69ys580p2t", "SCRT")



fun addressToUnderlyingAssetId(address: String): UnderlyingAssetId {
    val symbol = when (address) {
        sscrtAssetId.underlyingAssetAddress -> scrtVariantSymbol
        stkdScrtAssetId.underlyingAssetAddress -> scrtVariantSymbol
        seScrtAssetId.underlyingAssetAddress -> scrtVariantSymbol
        else -> throw Exception("Unknown address $address, cannot map to symbol")
    }
    return UnderlyingAssetId(address, symbol)
}


fun symbolToAssetId(symbol: String): UnderlyingAssetId {
    val underlyingAssetAddress = when (symbol) {
        "OSMO" -> "secret1zwwealwm0pcl9cul4nt6f38dsy6vzplw8lp3qg"
        "MANA" -> "secret178t2cp33hrtlthphmt9lpd25qet349mg4kcega"
        "USDT" -> "secret18wpjn83dayu4meu6wnn29khfkwdxs7kyrz9c8f"
        "USDC" -> "secret1h6z05y90gwm4sqxzhz4pkyp36cna9xtp7q0urv"
        "DAI" -> "secret1vnjck36ld45apf8u4fedxd5zy7f5l92y3w5qwq"
        "WBTC" -> "secret1g7jfnxmxkjgqdts9wlmn238mrzxz5r92zwqv4a"
        "XMR" -> "secret19ungtd2c7srftqdwgq0dspwvrw63dhu79qxv88"
        "ETH" -> "secret1wuzzjsdhthpvuyeeyhfq2ftsn3mvwf9rxy6ykw"
        "ATOM" -> "secret14mzwd0ps5q277l20ly2q3aetqe3ev4m4260gf4"
        "AAVE" -> "secret1yxwnyk8htvvq25x2z87yj0r5tqpev452fk6h5h"
        "BNB" -> "secret1tact8rxxrvynk4pwukydnle4l0pdmj0sq9j9d5"
        else -> {
            throw Exception("Unknown symbol $symbol, cannot map to CoinGeckoId")
        }
    }
    return UnderlyingAssetId(underlyingAssetAddress, symbol)
}
