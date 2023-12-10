package com.example.plugins

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bson.BsonDocument
import org.bson.types.ObjectId

inline fun <reified T : Any> MongoDatabase.lazyGetCollection(
    collectionName: String,
    noinline initializer: (suspend MongoCollection<T>.() -> Unit)? = null
): MongoCollection<T> =
    runBlocking {
        if (listCollectionNames().filter { it == collectionName }.firstOrNull() == null) {
            createCollection(collectionName)
            initializer?.invoke(getCollection<T>(collectionName))
        }
        getCollection<T>(collectionName)
    }

suspend fun <T : Any> MongoCollection<T>.findById(id: ObjectId) =
    find(eq("_id", id)).firstOrNull()

@OptIn(ExperimentalSerializationApi::class)
val bsonAwareJson = Json { serializersModule = org.bson.codecs.kotlinx.defaultSerializersModule }

suspend inline fun <reified T : Any> MongoCollection<T>.updateOne(
    id: ObjectId,
    entity: T,
    json: Json = bsonAwareJson
): UpdateResult {
    val bsonUpdates = BsonDocument.parse(json.encodeToString<T>(entity))
        .filterKeys { it != "_id" }
        .map { (key, value) -> Updates.set(key, value) }
    val versionProperty = T::class.members.firstOrNull { it.name == "version" }
    return if (versionProperty == null)
        updateOne(eq("_id", id), bsonUpdates)
    else {
        val version: Long = versionProperty.call(entity) as Long
        updateOne(
            and(eq("_id", id), eq("version", version)),
            bsonUpdates + Updates.set("version", version + 1)
        )
    }
}
