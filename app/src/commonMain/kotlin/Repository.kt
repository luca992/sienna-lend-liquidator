import io.eqoty.secretk.client.SigningCosmWasmClient

class Repository(
    val client: SigningCosmWasmClient,
    var senderAddress: String
) {
    init {
        logger.i("Repository created with senderAddress: $senderAddress")
    }
}