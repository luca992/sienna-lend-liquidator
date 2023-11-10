import types.Config
import datalayer.sources.web2.HttpClient
import io.eqoty.secretk.client.SigningCosmWasmClient

class Repository(
    val client: SigningCosmWasmClient,
    var senderAddress: String,
    val config: Config
) {
    init {
        logger.i("Repository created with senderAddress: $senderAddress")
    }

    internal val httpClient by lazy { HttpClient() }

}