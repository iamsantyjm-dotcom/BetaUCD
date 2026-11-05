package com.ucd.player.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// ---------- MediaStore ----------
data class VideoItem(
    val id: Long, val uri: String, val title: String, val durationMs: Long, val folder: String,
    val dateAdded: Long = 0L,   // segundos (epoch)
    val sizeBytes: Long = 0L,
    /** Carpeta real del archivo, por ejemplo "Movies/UCD/" (vacío en Android 9 o menor). */
    val relativePath: String = "",
)

class MediaStoreScanner(private val ctx: Context) {
    suspend fun scan(): List<VideoItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val hasRel = Build.VERSION.SDK_INT >= 29
        val proj = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.SIZE)
            if (hasRel) add(MediaStore.Video.Media.RELATIVE_PATH)
        }.toTypedArray()
        val out = mutableListOf<VideoItem>()
        ctx.contentResolver.query(
            collection, proj, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val iFolder = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val iRel = if (hasRel) c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                out += VideoItem(
                    id, ContentUris.withAppendedId(collection, id).toString(),
                    (c.getString(iName) ?: "video").substringBeforeLast('.'), c.getLong(iDur),
                    c.getString(iFolder) ?: "Sin carpeta",
                    c.getLong(iDate), c.getLong(iSize),
                    if (iRel >= 0) c.getString(iRel) ?: "" else "",
                )
            }
        }
        out
    }
}

// ---------- Room ----------
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class, parentColumns = ["id"],
        childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("playlistId")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val position: Int,            // mantiene el orden
    val videoUri: String,
    val title: String,
)

@Dao
interface PlaylistDao {
    @Insert suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("SELECT * FROM playlists ORDER BY name") fun playlists(): Flow<List<PlaylistEntity>>
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    fun items(id: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_items WHERE playlistId = :id")
    suspend fun nextPosition(id: Long): Int

    @Insert suspend fun insertItem(i: PlaylistItemEntity)
    @Query("DELETE FROM playlist_items WHERE videoUri IN (:uris)") suspend fun removeVideos(uris: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertItems(i: List<PlaylistItemEntity>)
    @Query("DELETE FROM playlist_items WHERE playlistId = :id") suspend fun clearItems(id: Long)

    @Transaction
    suspend fun addVideo(playlistId: Long, uri: String, title: String) =
        insertItem(PlaylistItemEntity(playlistId, nextPosition(playlistId), uri, title))

    /** Reordenar: reescribe posiciones 0..n según la nueva lista. */
    @Transaction
    suspend fun reorder(playlistId: Long, newOrder: List<PlaylistItemEntity>) {
        clearItems(playlistId)
        insertItems(newOrder.mapIndexed { i, it -> it.copy(playlistId = playlistId, position = i) })
    }
}

@Database(entities = [PlaylistEntity::class, PlaylistItemEntity::class], version = 1, exportSchema = false)
abstract class UcdDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun build(ctx: Context) = Room.databaseBuilder(ctx, UcdDatabase::class.java, "ucd.db").build()
    }
}
