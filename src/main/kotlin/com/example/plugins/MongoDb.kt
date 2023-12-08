package com.example.plugins

import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureMongoDb() {
    routing {
        get("/mongo") {
            call.respond(OK, mapOf("hello" to "word"))
        }
    }
}

