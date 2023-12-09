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

suspend fun <T : Any> MongoCollection<T>.findById(id: ObjectId) =
    find(eq("_id", id)).firstOrNull()

@OptIn(ExperimentalSerializationApi::class)
val json = Json { serializersModule = defaultSerializersModule }

suspend inline fun <reified T : Any> MongoCollection<T>.updateOne(
    id: ObjectId,
    update: T,
    json: Json = com.example.plugins.json
): UpdateResult {
    val encodeToString = json.encodeToString(update)
    val bsonUpdate = BsonDocument.parse(encodeToString)
        .filterKeys { it != "_id" }
    val hasVersion = T::class.members.firstOrNull { it.name == "version" }
    return if (hasVersion != null) {
        val version = bsonUpdate["version"]!!.asNumber().longValue()
        updateOne(
            and(
                eq("_id", id),
                eq("version", version)
            ),
            bsonUpdate.map { (key, value) -> Updates.set(key, value) }
                    + Updates.set("version", version + 1)
        )
    } else
        updateOne(
            eq("_id", id),
            bsonUpdate.map { (key, value) -> Updates.set(key, value) }
        )
}
