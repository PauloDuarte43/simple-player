package br.tec.pauloduarte.simpleplayer.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE playlistId = :playlistId AND (groupName NOT LIKE '%XXX%' AND groupName NOT LIKE '%Adult%' AND groupName NOT LIKE '%+18%') ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomMediaFiltered(playlistId: Int): Media?

    @Query("SELECT * FROM media WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getMediaForPlaylist(playlistId: Int): PagingSource<Int, Media>

    @Query("SELECT * FROM media WHERE playlistId = :playlistId AND name LIKE :query ORDER BY name ASC")
    fun searchMediaInPlaylist(playlistId: Int, query: String): PagingSource<Int, Media>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg medias: Media)

    @Query("SELECT COUNT(*) FROM media WHERE playlistId = :playlistId")
    suspend fun getMediaCountForPlaylist(playlistId: Int): Int

    @Query("SELECT * FROM media WHERE playlistId = :playlistId AND (groupName NOT LIKE '%XXX%' AND groupName NOT LIKE '%Adult%' AND groupName NOT LIKE '%+18%') ORDER BY name ASC")
    fun getMediaForPlaylistFiltered(playlistId: Int): PagingSource<Int, Media>

    @Query("SELECT * FROM media WHERE playlistId = :playlistId AND name LIKE :query AND (groupName NOT LIKE '%XXX%' AND groupName NOT LIKE '%Adult%' AND groupName NOT LIKE '%+18%') ORDER BY name ASC")
    fun searchMediaInPlaylistFiltered(playlistId: Int, query: String): PagingSource<Int, Media>

    @Query("SELECT * FROM media WHERE playlistId = :playlistId ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomMedia(playlistId: Int): Media?

    @Query("SELECT DISTINCT groupName FROM media WHERE playlistId = :playlistId ORDER BY groupName ASC")
    fun getDistinctGroupNames(playlistId: Int): Flow<List<String?>>

    @Query("SELECT * FROM media WHERE playlistId = :playlistId AND (:groupName = 'Todos' OR groupName = :groupName) ORDER BY name ASC")
    fun getMediaForPlaylistByGroup(playlistId: Int, groupName: String): PagingSource<Int, Media>

    @Query("SELECT * FROM media WHERE playlistId = :playlistId AND name LIKE :query AND (:groupName = 'Todos' OR groupName = :groupName) ORDER BY name ASC")
    fun searchMediaInPlaylistByGroup(playlistId: Int, query: String, groupName: String): PagingSource<Int, Media>
}