package com.machine.newsapp.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// The complete, validated JSON is replaced atomically. Each news filter has its
// own row, so an offline category never accidentally shows another category.
@Entity(tableName = "feed_cache")
data class FeedCache(
    @PrimaryKey val feedKey: String,
    val payload: String,
    val fetchedAt: Long,
)
@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_cache WHERE feedKey = :key")
    fun observe(key: String): Flow<FeedCache?>
    @Query("SELECT * FROM feed_cache WHERE feedKey = :key")
    suspend fun get(key: String): FeedCache?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(cache: FeedCache)
}
@Database(entities = [FeedCache::class], version = 1, exportSchema = true)
abstract class FeedDatabase : RoomDatabase() { abstract fun feeds(): FeedDao }
