package utils

import com.ionspin.kotlin.bignum.serialization.kotlinx.biginteger.bigIntegerhumanReadableSerializerModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

val json = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    serializersModule = bigIntegerhumanReadableSerializerModule
}
