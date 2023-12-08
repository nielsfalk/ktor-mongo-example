package com.example.plugins

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

fun Application.configureMongoDb() {
    val mongoClient = MongoClient.create()
    val database = mongoClient.getDatabase("test")
    val collection = database.lazyGetCollection("jedi")

    routing {
        get("/mongo/jedi") {
            val jedi = collection.find().toList()
            call.respond(OK, jedi.map { it.toModel() })
        }
    }
}

@Serializable
data class Jedi(
    val id: String? = null,
    val name: String,
    val age: Int
)

@Serializable
data class JediEntity(
    @SerialName("_id")
    @Contextual
    val id: ObjectId? = null,
    val name: String,
    val age: Int
)

private fun JediEntity.toModel() = Jedi(
    id = id?.toHexString(),
    name = name,
    age = age
)

fun MongoDatabase.lazyGetCollection(collectionName: String): MongoCollection<JediEntity> {
    runBlocking {
        if (listCollectionNames().filter { it == collectionName }.firstOrNull() == null) {
            createCollection(collectionName)
            getCollection<JediEntity>(collectionName).insertOne(
                JediEntity(
                    name = "Luke",
                    age = 19
                )
            )
        }
    }
    return getCollection<JediEntity>(collectionName)
}
