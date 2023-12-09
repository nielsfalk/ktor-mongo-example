package com.example.plugins

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.bson.BsonDocument
import org.bson.types.ObjectId
import org.bson.codecs.kotlinx.defaultSerializersModule as bsonDefaultSerializersModule

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureMongoDb() {
    val mongoClient = MongoClient.create()
    val database = mongoClient.getDatabase("test")
    val collection = database.lazyGetCollection("jedi")
    suspend fun PipelineContext<Unit, ApplicationCall>.findJediById(
        id: ObjectId = call.parameters["id"]
            ?.let { if (ObjectId.isValid(it)) ObjectId(it) else null }
            ?: throw IllegalArgumentException("Invalid ID")
    ) =
        collection.find(
            eq("_id", id)
        ).firstOrNull()

    val json = Json { serializersModule = bsonDefaultSerializersModule }

    routing {
        get("/mongo/jedi") {
            val jedi = collection.find().toList()
            call.respond(OK, jedi.map { it.toModel() })
        }
        post("/mongo/jedi") {
            val jedi = call.receive<Jedi>()
            val id = collection.insertOne(jedi.toEntity()).insertedId!!

            call.response.header("Location", "/mongo/jedi/${id.asObjectId().value}")
            call.respond(
                Created,
                findJediById(id.asObjectId().value)
                    ?.toModel()
                    ?: throw Exception("Inserted id not found")
            )
        }
        get("/mongo/jedi/{id}") {
            findJediById()?.let {
                call.respond(OK, it.toModel())
            }
        }
        put("/mongo/jedi/{id}") {
            findJediById()?.let { found ->
                val update = call.receive<Jedi>()
                collection.updateOne(eq("_id", found.id),
                    BsonDocument.parse(json.encodeToString<Jedi>(update))
                        .filterKeys { it != "_id" }
                        .map { (key, value) -> set(key, value) }
                )
                call.respond(
                    OK,
                    findJediById(found.id!!)
                        ?.toModel()
                        ?: throw Exception("Id not found")
                )
            }
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

private fun JediEntity.toModel() =
    Jedi(
        id = id?.toHexString(),
        name = name,
        age = age
    )

private fun Jedi.toEntity(): JediEntity =
    JediEntity(
        id = id?.let(::ObjectId),
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
