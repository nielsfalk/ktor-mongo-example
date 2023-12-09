# ktor-mongo-example

this is an example where I combined the [mongodb-driver-kotlin-coroutine](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/) with [kotlin-serialization](https://kotlinlang.org/docs/serialization.html) and [ktor](https://ktor.io/). Everything is tested with [kotest](https://kotest.io/) and ktor-server-tests.

## Combining mongoDb and Ktor

```kotlin
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
```

## Optimistic Locking

I also extended the MongoCollection so Entity-Models can be updated with optimistic locking

```kotlin
@OptIn(ExperimentalSerializationApi::class)
val bsonAwareJson = Json { serializersModule = org.bson.codecs.kotlinx.defaultSerializersModule }

suspend inline fun <reified T : Any> MongoCollection<T>.updateOne(
    id: ObjectId,
    entity: T,
    json: Json = Json {
        serializersModule = bsonAwareJson
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
```

## AppFunSpec

with the AppFunSpec I combined koTest and ktor-server-tests so the test can be written very crisp.

```kotlin
class MongoDbTest : AppFunSpec({
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
})

```