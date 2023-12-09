package com.example

import com.example.plugins.Jedi
import com.example.plugins.configureMongoDb
import com.example.plugins.configureSerialization
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
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
import kotlin.random.Random
import kotlin.random.nextUInt


class MongoDbTest : AppFunSpec({
    test("GET list") {
        val id = insertTestJedi(Jedi(name = "Luke", age = 19))

        client().get("/mongo/jedi").apply {

            status shouldBe OK
            body<List<Jedi>>() shouldContain Jedi(id = id, name = "Luke", age = 19)
        }
    }

    test("POST") {
        client().post("/mongo/jedi") {
            setBody(Jedi(name = "Yoda", age = 534))
            contentType(Json)
        }

            .apply {
                status shouldBe Created
                headers["Location"] shouldStartWith "/mongo/jedi/"
                body<Jedi>() shouldBe Jedi(id = createdIdFromLocationHeader(), name = "Yoda", age = 534)
            }
    }

    test("GET") {
        val id = insertTestJedi(Jedi(name = "Yoda", age = 534))

        client().get("/mongo/jedi/$id").apply {

            status shouldBe OK
            body<Jedi>() shouldBeEqual Jedi(id = id, name = "Yoda", age = 534)
        }
    }

    test("GET with invalid id") {
        client().get("/mongo/jedi/invalid").apply {

            status shouldBe NotFound
        }
    }

    test("PUT") {
        val id = insertTestJedi(Jedi(name = "Yoda", age = 534))

        client().put("/mongo/jedi/$id") {
            setBody(Jedi(name = "Yoda", age = 1534, version = 0))
            contentType(Json)
        }.apply {

            status shouldBe OK
            body<Jedi>() shouldBeEqual Jedi(id = id, name = "Yoda", age = 1534, version = 1)
        }
    }

    test("PUT with wrong version") {
        val id = insertTestJedi(Jedi(name = "Yoda", age = 534))

        client().put("/mongo/jedi/$id") {
            setBody(Jedi(name = "Yoda", age = 1534, version = 999))
            contentType(Json)
        }.apply {

            status shouldBe BadRequest
            bodyAsText() shouldBeEqual "$id was not updated. Maybe the version is outdated"
        }
    }

    test("DELETE") {
        val id = insertTestJedi(Jedi(name = "Yoda", age = 534))
        client().delete("/mongo/jedi/$id").apply {

            status shouldBe NoContent
            client().get("/mongo/jedi/$id").status shouldBe NotFound
        }
    }
})

private fun HttpResponse.createdIdFromLocationHeader() = headers["Location"]!!.removePrefix("/mongo/jedi/")

private suspend fun AppFunSpec.insertTestJedi(jedi: Jedi) =
    client().post("/mongo/jedi") {
        this.setBody(jedi)
        this.contentType(Json)
    }.headers["Location"]!!.removePrefix("/mongo/jedi/")

abstract class AppFunSpec(
    body: AppFunSpec.() -> Unit = {},
    databaseName: String = "test${Random.nextUInt()}",
    private val database: MongoDatabase = MongoClient.create().getDatabase(databaseName)
) : FunSpec() {
    private lateinit var clientFactory: (HttpClientConfig<out HttpClientEngineConfig>.() -> Unit) -> HttpClient

    init {
        afterProject { runBlocking { database.drop() } }
        body()
    }

    fun client(
        config: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {
            install(ContentNegotiation) {
                json(Json { prettyPrint = true })
            }
        }
    ): HttpClient = clientFactory(config)

    override fun test(name: String, test: suspend TestScope.() -> Unit) {
        super.test(name) {
            testApplication {
                application {
                    configureSerialization()
                    configureMongoDb(database)
                }

                clientFactory =
                    { clientConfiguration ->
                        createClient {
                            clientConfiguration()
                        }
                    }
                test()
            }
        }
    }
}