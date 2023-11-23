package datalayer.functions

import Repository
import com.ionspin.kotlin.bignum.integer.toBigInteger

suspend fun Repository.updateBlockHeight() {
    runtimeCache.blockHeight.value = client.getLatestBlock().block.header.height.toBigInteger()
}