package com.example.plugins

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

fun Application.configureMongoDb() {
    val mongoClient = MongoClient.create()
    val database = mongoClient.getDatabase("test")
    val collection = database.lazyGetCollection<JediEntity>("jedi")
    suspend fun PipelineContext<Unit, ApplicationCall>.findJediById(
        id: ObjectId = call.parameters["id"]
            ?.let { if (ObjectId.isValid(it)) ObjectId(it) else null }
            ?: throw IllegalArgumentException("Invalid ID")
    ) = collection.findJediById(id)

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
                collection.updateOne(
                    found.id!!,
                    call.receive<Jedi>().toEntity()
                )
                call.respond(
                    OK,
                    findJediById(found.id)
                        ?.toModel()
                        ?: throw Exception("Id not found")
                )
            }
        }
        delete("/mongo/jedi/{id}") {
            findJediById()?.let { found ->
                collection.deleteOne(eq("_id", found.id))
                call.respond(NoContent)
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
