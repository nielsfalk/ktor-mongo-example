package com.example

import com.example.plugins.Jedi
import com.example.plugins.configureMongoDb
import com.example.plugins.configureSerialization
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json


@Suppress("unused")
class MongoDbTest : FunSpec({
    appTest("GET list") { client ->
        client.get("/mongo/jedi").apply {

            status shouldBe OK
            body<List<Jedi>>().nullIds() shouldContain Jedi(name = "Luke", age = 19)
        }
    }

    appTest("POST") { client ->
        client.post("/mongo/jedi") {
            setBody(Jedi(name = "Yoda", age = 534))
            contentType(Json)
        }

            .apply {
                status shouldBe Created
                headers["Location"] shouldStartWith "/mongo/jedi/"
                body<Jedi>().nullId() shouldBe Jedi(name = "Yoda", age = 534)
            }
    }

    appTest("GET") { client ->
        val id = insertTestJedi(client, Jedi(name = "Yoda", age = 534))
        client.get("/mongo/jedi/$id").apply {

            status shouldBe OK
            body<Jedi>().nullId() shouldBeEqual Jedi(name = "Yoda", age = 534)
        }
    }

    appTest("PUT") { client ->
        val id = insertTestJedi(client, Jedi(name = "Yoda", age = 534))
        client.put("/mongo/jedi/$id") {
            setBody(Jedi(name = "Yoda", age = 1534))
            contentType(Json)
        }.apply {

            status shouldBe OK
            body<Jedi>().nullId() shouldBeEqual Jedi(name = "Yoda", age = 1534)
        }
    }
})


private suspend fun insertTestJedi(client: HttpClient, jedi: Jedi): String {
    val s = client.post("/mongo/jedi") {
        setBody(jedi)
        contentType(Json)
    }.headers["Location"]!!
    return s
        .substring(12)
}

private fun List<Jedi>.nullIds(): List<Jedi> = map { it.nullId() }
private fun Jedi.nullId() = copy(id = null)

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