package br.tec.pauloduarte.simpleplayer.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query


@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getMediaForPlaylistPaged(playlistId: Int): PagingSource<Int, Media>
}