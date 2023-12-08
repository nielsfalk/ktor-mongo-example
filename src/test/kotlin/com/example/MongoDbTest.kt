package com.example

import com.example.plugins.Jedi
import com.example.plugins.configureMongoDb
import com.example.plugins.configureSerialization
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json


@Suppress("unused")
class MongoDbTest : FunSpec({
    appTest("GET jedi") { client ->
        client.get("/mongo/jedi").apply {

            status shouldBe OK
            body<List<Jedi>>().nullIds() shouldBe listOf(Jedi(name = "Luke", age = 19))
        }
    }
})

private fun List<Jedi>.nullIds(): List<Jedi> = map { it.copy(id = null) }

private fun FunSpec.appTest(
    name: String,
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit
) {
    test(name) {
        testApplication {
            application {
                configureSerialization()
                configureMongoDb()
            }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                    })
                }
            }

            block(client)
        }
    }
}