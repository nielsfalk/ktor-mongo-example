package com.example.plugins

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.http.HttpStatusCode.Companion.BadRequest
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

fun Application.configureMongoDb(
    database: MongoDatabase = MongoClient.create().getDatabase("test"),
    collection: MongoCollection<JediEntity> = database.lazyGetCollection<JediEntity>("jedi")
) {
    routing {
        get("/mongo/jedi") { call.respond(OK, collection.find().toList().toModel()) }
        post("/mongo/jedi") {
            val insertResult = collection.insertOne(call.receive<Jedi>().toEntity())
            val id = insertResult.insertedId!!.asObjectId().value
            collection.findById(id)?.let {
                call.response.header("Location", "/mongo/jedi/$id")
                call.respond(Created, it.toModel())
            }
        }
        get("/mongo/jedi/{id}") { collection.findById()?.let { call.respond(OK, it.toModel()) } }
        put("/mongo/jedi/{id}") {
            collection.findById()?.let { found ->
                val updateResult = collection.updateOne(found.id!!, call.receive<Jedi>().toEntity())
                if (updateResult.modifiedCount == 0L) {
                    call.respond(BadRequest, "${found.id} was not updated. Maybe the version is outdated")
                } else
                    call.respond(
                        OK,
                        collection.findById(found.id)
                            ?.toModel()
                            ?: throw Exception("Id not found")
                    )
            }
        }
        delete("/mongo/jedi/{id}") {
            collection.findById()?.let { found ->
                val deleteResult = collection.deleteOne(eq("_id", found.id))
                if (deleteResult.deletedCount == 0L) throw Exception("Id not found")
                call.respond(NoContent)
            }
        }
    }
}

context (PipelineContext<Unit, ApplicationCall>)
suspend fun <T : Any> MongoCollection<T>.findById() =
    call.parameters["id"]
        ?.let { if (ObjectId.isValid(it)) ObjectId(it) else null }
        ?.let { findById(it) }


@Serializable
data class Jedi(
    val id: String? = null,
    val version: Long = 0,
    val name: String,
    val age: Int
)

@Serializable
data class JediEntity(
    @SerialName("_id")
    @Contextual
    val id: ObjectId? = null,
    val version: Long,
    val name: String,
    val age: Int
)

private fun JediEntity.toModel() =
    Jedi(
        id = id?.toHexString(),
        version = version,
        name = name,
        age = age
    )

private fun List<JediEntity>.toModel(): List<Jedi> = map { it.toModel() }

private fun Jedi.toEntity(): JediEntity =
    JediEntity(
        id = id?.let(::ObjectId),
        version = version,
        name = name,
        age = age
    )
