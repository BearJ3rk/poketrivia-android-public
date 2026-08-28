package com.example.poketrivia

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

data class NamedResource(val name: String, val url: String)
data class GenerationResponse(@SerializedName("pokemon_species") val species: List<NamedResource>)
data class PokemonResponse(val id: Int, val name: String, val height: Int, val sprites: Sprites, val cries: Cries?)
data class Cries(val latest: String?, val legacy: String?)
data class Sprites(@SerializedName("front_default") val image: String?, val other: OtherSprites)
data class OtherSprites(@SerializedName("official-artwork") val artwork: Artwork)
data class Artwork(@SerializedName("front_default") val image: String?, @SerializedName("front_shiny") val shiny: String?)
data class SpeciesResponse(
    val name: String,
    val color: NamedResource,
    val generation: NamedResource,
    @SerializedName("flavor_text_entries") val flavor: List<FlavorText>
)
data class FlavorText(@SerializedName("flavor_text") val text: String, val language: NamedResource)
data class ReleaseAsset(
    val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)
data class ReleaseResponse(
    @SerializedName("tag_name") val tag: String,
    @SerializedName("html_url") val url: String,
    val assets: List<ReleaseAsset> = emptyList()
) {
    val apkUrl: String? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.downloadUrl
}

interface PokeApi {
    @GET("api/v2/generation/{id}") suspend fun generation(@Path("id") id: Int): GenerationResponse
    @GET("api/v2/pokemon/{name}") suspend fun pokemon(@Path("name") name: String): PokemonResponse
    @GET("api/v2/pokemon-species/{name}") suspend fun species(@Path("name") name: String): SpeciesResponse
    @GET suspend fun release(@Url url: String): ReleaseResponse
}

@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val player: String,
    val score: Int,
    val total: Int,
    val difficulty: String,
    val generation: String,
    val durationMs: Long = 0,
    val runMode: String = "LEGACY",
    val livesRemaining: Int = 0,
    val playedAt: Long = System.currentTimeMillis()
)

@Dao interface ScoreDao {
    @Query("""
        SELECT s.* FROM scores s
        WHERE NOT EXISTS (
            SELECT 1 FROM scores better
            WHERE better.runMode = s.runMode
              AND lower(better.player) = lower(s.player)
              AND (
                  better.score > s.score
                  OR (better.score = s.score AND better.durationMs > 0 AND (s.durationMs = 0 OR better.durationMs < s.durationMs))
                  OR (better.score = s.score AND better.durationMs = s.durationMs AND better.id < s.id)
              )
        )
        ORDER BY runMode, score DESC, CASE WHEN durationMs = 0 THEN 1 ELSE 0 END, durationMs ASC, playedAt ASC
    """) fun leaderboard(): kotlinx.coroutines.flow.Flow<List<ScoreEntity>>
    @Query("""
        SELECT * FROM scores
        WHERE runMode = :runMode AND lower(player) = lower(:player)
        ORDER BY score DESC, CASE WHEN durationMs = 0 THEN 1 ELSE 0 END, durationMs ASC, playedAt ASC
        LIMIT 1
    """) suspend fun personalBest(player: String, runMode: String): ScoreEntity?
    @Insert suspend fun insert(score: ScoreEntity): Long
    @Update suspend fun update(score: ScoreEntity)
    @Query("DELETE FROM scores WHERE runMode = :runMode AND lower(player) = lower(:player) AND id != :keepId")
    suspend fun deleteOtherScores(player: String, runMode: String, keepId: Long)
}

@Database(entities = [ScoreEntity::class], version = 3, exportSchema = true)
abstract class TriviaDatabase : RoomDatabase() { abstract fun scores(): ScoreDao }

private val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scores ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
    }
}

private val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scores ADD COLUMN runMode TEXT NOT NULL DEFAULT 'LEGACY'")
        db.execSQL("ALTER TABLE scores ADD COLUMN livesRemaining INTEGER NOT NULL DEFAULT 0")
    }
}

class TriviaRepository(context: Context) {
    val api: PokeApi = Retrofit.Builder().baseUrl("https://pokeapi.co/").addConverterFactory(GsonConverterFactory.create()).build().create(PokeApi::class.java)
    private val db = Room.databaseBuilder(context, TriviaDatabase::class.java, "trivia.db").addMigrations(Migration1To2, Migration2To3).build()
    val scores = db.scores().leaderboard()
    suspend fun save(score: ScoreEntity) {
        val dao = db.scores()
        val best = dao.personalBest(score.player, score.runMode)
        val keepId = if (best == null) {
            dao.insert(score)
        } else if (score.isBetterThan(best)) {
            dao.update(score.copy(id = best.id))
            best.id
        } else {
            best.id
        }
        dao.deleteOtherScores(score.player, score.runMode, keepId)
    }
}

private fun ScoreEntity.isBetterThan(other: ScoreEntity): Boolean {
    if (score != other.score) return score > other.score
    if (durationMs > 0 && other.durationMs == 0L) return true
    if (durationMs == 0L && other.durationMs > 0) return false
    return durationMs < other.durationMs
}
