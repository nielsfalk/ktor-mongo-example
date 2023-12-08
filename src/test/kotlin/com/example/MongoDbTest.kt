package com.example

import com.example.plugins.configureMongoDb
import com.example.plugins.configureSerialization
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.*


class MongoDbTest : FunSpec({
    appTest("hello world") {
        client.get("/mongo").apply {

            status shouldBe OK
            bodyAsText() shouldBe """{"hello":"word"}"""
        }
    }
})

private fun FunSpec.appTest(
    name: String,
    block: suspend ApplicationTestBuilder.() -> Unit
) {
    test(name) {
        testApplication {
            application {
                configureSerialization()
                configureMongoDb()
            }

            block()
        }
    }
}