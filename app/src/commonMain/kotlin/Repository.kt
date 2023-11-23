import com.ionspin.kotlin.bignum.serialization.kotlinx.biginteger.bigIntegerhumanReadableSerializerModule
import datalayer.sources.cache.RuntimeCache
import datalayer.sources.web2.HttpClient
import io.eqoty.secretk.client.SigningCosmWasmClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import types.Config

class Repository(
    val client: SigningCosmWasmClient,
    var senderAddress: String,
    val config: Config,
    val runtimeCache: RuntimeCache = RuntimeCache()
) {
    init {
        logger.i("Repository created with senderAddress: $senderAddress")
    }

    internal val httpClient by lazy { HttpClient() }

}