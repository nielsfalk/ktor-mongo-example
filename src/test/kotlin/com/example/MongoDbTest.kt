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
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json


@Suppress("unused")
class MongoDbTest : FunSpec({
    appTest("GET list") { client ->
        val id = insertTestJedi(client, Jedi(name = "Luke", age = 19))

        client.get("/mongo/jedi").apply {

            status shouldBe OK
            body<List<Jedi>>() shouldContain Jedi(id = id, name = "Luke", age = 19)
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
                body<Jedi>() shouldBe Jedi(id = createdIdFromLocationHeader(), name = "Yoda", age = 534)
            }
    }

    appTest("GET") { client ->
        val id = insertTestJedi(client, Jedi(name = "Yoda", age = 534))

        client.get("/mongo/jedi/$id").apply {

            status shouldBe OK
            body<Jedi>() shouldBeEqual Jedi(id = id, name = "Yoda", age = 534)
        }
    }

    appTest("GET with invalid id") { client ->
        client.get("/mongo/jedi/invalid").apply {

            status shouldBe NotFound
        }
    }

    appTest("PUT") { client ->
        val id = insertTestJedi(client, Jedi(name = "Yoda", age = 534))

        client.put("/mongo/jedi/$id") {
            setBody(Jedi(name = "Yoda", age = 1534))
            contentType(Json)
        }.apply {

            status shouldBe OK
            body<Jedi>() shouldBeEqual Jedi(id = id, name = "Yoda", age = 1534, version = 1)
        }
    }

    appTest("PUT with wrong version") { client ->
        val id = insertTestJedi(client, Jedi(name = "Yoda", age = 534))

        client.put("/mongo/jedi/$id") {
            setBody(Jedi(name = "Yoda", age = 1534, version = 999))
            contentType(Json)
        }.apply {

            status shouldBe BadRequest
            bodyAsText() shouldBeEqual "${id} was not updated. Maybe the version was outdated"
        }
    }

    appTest("DELETE") { client ->
        val id = insertTestJedi(client, Jedi(name = "Yoda", age = 534))
        client.delete("/mongo/jedi/$id").apply {

            status shouldBe NoContent
            client.get("/mongo/jedi/$id").status shouldBe NotFound
        }
    }
})

private fun HttpResponse.createdIdFromLocationHeader() = headers["Location"]!!.removePrefix("/mongo/jedi/")


private suspend fun insertTestJedi(client: HttpClient, jedi: Jedi): String {
    val s = client.post("/mongo/jedi") {
        setBody(jedi)
        contentType(Json)
    }.headers["Location"]!!
    return s
        .substring(12)
}

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