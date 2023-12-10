package com.example

import com.example.Category.Parks
import com.example.plugins.lazyGetCollection
import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.json.JsonObject
import org.bson.types.ObjectId
import kotlin.random.Random
import kotlin.random.nextUInt

class MongoGeospatialTest : MongoFunSpec({
    val collection = database.lazyGetCollection<Place>(
        collectionName = "places",
        initializer = {
            //language=MongoDB-JSON
            createIndex(JsonObject("""{ location: "2dsphere" }"""))
            insertMany(
                listOf(
                    Place(
                        name = "Central Park",
                        location = Location(-73.97f, 40.77f),
                        category = Parks
                    ),
                    Place(
                        name = "Sara D. Roosevelt Park",
                        location = Location(-73.9928f, 40.7193f),
                        category = Parks
                    ),
                    Place(
                        name = "Polo Grounds",
                        location = Location(-73.9375f, 40.8303f),
                        category = Parks
                    )
                )
            )
        }
    )

    val centralPark = runBlocking {
        collection.find(eq("name", "Central Park")).first()
    }

    test("find location near") {
        val places: List<Place> = collection.find(
            near(
                Location(-73.9667f, 40.78f),
                5000
            )
        ).toList()

        places shouldBeEqual listOf(centralPark)
    }

    test("aggregate distance") {
        @Serializable
        data class AggregateResult(val place: Place, val distance: Float)

        collection.aggregate(
            distanceTo(Location(-73.9667f, 40.78f)),
            AggregateResult::class.java
        ).toList().apply {

            shouldContain(AggregateResult(centralPark, 1147.3976f))
        }
    }
})

private fun near(location: Location, maxDistanceInMeters: Int = 5000) =
    //language=MongoDB-JSON
    JsonObject(
        """
        {
            location:{
                ${'$'}near:{
                    ${'$'}geometry: { 
                        type: "${location.type}",  
                        coordinates: [ ${location.longitude}, ${location.latitude} ] 
                    },
                    ${'$'}maxDistance: $maxDistanceInMeters
                }
             }
        }
        """.trimIndent()
    )

private fun distanceTo(location: Location) =
    //language=MongoDB-JSON
    listOf(
        JsonObject(
            """
            {
                ${'$'}geoNear: {
                    near: { 
                        type: "${location.type}",  
                        coordinates: [ ${location.longitude}, ${location.latitude} ] 
                    },
                    spherical: true,
                    distanceField: "calcDistance"
                }
            }   
            """.trimIndent()
        ),
        JsonObject(
            """
            {
                "${'$'}project": {
                    "place": "${'$'}${'$'}ROOT",
                    "distance": "${'$'}${'$'}ROOT.calcDistance"
                }
            }
            """.trimIndent()
        )
    )

abstract class MongoFunSpec(
    body: MongoFunSpec.() -> Unit = {},
    databaseName: String = "test${Random.nextUInt()}",
    val database: MongoDatabase = MongoClient.create().getDatabase(databaseName)
) : FunSpec() {
    init {
        afterProject { runBlocking { database.drop() } }
        body()
    }
}

@Serializable
data class Place(
    @SerialName("_id")
    @Contextual
    val id: ObjectId? = null,
    val name: String,
    val location: Location,
    val category: Category
)

enum class Category {
    Parks
}

@Serializable
data class Location(
    val type: String = "Point",
    val coordinates: List<Float>
) {
    constructor(longitude: Float, latitude: Float) : this(coordinates = listOf(longitude, latitude))
}

val Location.longitude: Float
    get() = coordinates[0]
val Location.latitude: Float
    get() = coordinates[1]