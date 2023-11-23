package datalayer.functions

import Repository
import io.eqoty.secret.std.types.Permission
import io.eqoty.secret.std.types.Permit
import io.eqoty.secretk.extensions.accesscontrol.PermitFactory

suspend fun Repository.getPermit(senderAddress: String, contractAddress: String): Permit {
        return runtimeCache.senderToAddressPermitMap.getOrPut(senderAddress) {
            mutableMapOf()
        }.getOrPut(contractAddress) {
            PermitFactory.newPermit(
                client.wallet!!,
                senderAddress,
                client.getChainId(),
                "Permit",
                listOf(contractAddress),
                listOf(Permission.Balance),
            )
        }
    }