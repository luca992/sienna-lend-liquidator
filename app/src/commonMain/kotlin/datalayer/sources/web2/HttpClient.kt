package datalayer.sources.web2

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

class HttpClient {

    private val json = Json { namingStrategy = JsonNamingStrategy.SnakeCase }

    val client = HttpClient {

        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend inline fun <reified T : Any> getResponse(baseUrl: String, endpoint: String): T {
        val url = baseUrl + endpoint
        // please notice, Ktor Client is switching to a background thread under the hood
        // so the http call doesn't happen on the main thread, even if the coroutine has been launched on Dispatchers.Main
        val resp: T = client.get(url).body()
        return resp
    }


}
