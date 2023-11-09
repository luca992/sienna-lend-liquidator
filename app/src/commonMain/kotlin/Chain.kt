enum class Chain(val id: String, val grpcGatewayEndpoint: String, val rpcEndpoint: String) {
    Pulsar3("pulsar-3", "https://pulsar.api.trivium.network:1317", "https://pulsar.api.trivium.network:26657"),
    Secret4("secret-4", "https://secret-4.api.trivium.network:1317", "https://secret-4.api.trivium.network:26657")
}
