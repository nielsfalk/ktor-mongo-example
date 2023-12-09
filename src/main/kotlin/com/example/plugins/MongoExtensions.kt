package com.example.plugins

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
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
import org.bson.codecs.kotlinx.defaultSerializersModule
import org.bson.types.ObjectId

inline fun <reified T : Any> MongoDatabase.lazyGetCollection(collectionName: String): MongoCollection<T> {
    runBlocking {
        if (listCollectionNames().filter { it == collectionName }.firstOrNull() == null) {
            createCollection(collectionName)
        }
    }
    return getCollection<T>(collectionName)
}

@OptIn(ExperimentalSerializationApi::class)
val json = Json { serializersModule = defaultSerializersModule }


suspend fun <T : Any> MongoCollection<T>.findById(id: ObjectId) =
    find(eq("_id", id)).firstOrNull()

suspend inline fun <reified T : Any> MongoCollection<T>.updateOne(
    id: ObjectId,
    update: T,
    json: Json = com.example.plugins.json
): UpdateResult {
    val bsonUpdate = BsonDocument.parse(json.encodeToString<T>(update))
        .filterKeys { it != "_id" }
        .map { (key, value) -> set(key, value) }
    return T::class.members.firstOrNull { it.name == "version" }
        ?.let {
            val version = it.call(update) as Long
            updateOne(
                and(eq("_id", id), eq("version", version)),
                bsonUpdate + set("version", version + 1)
            )
        }
        ?: updateOne(eq("_id", id), bsonUpdate)
}
