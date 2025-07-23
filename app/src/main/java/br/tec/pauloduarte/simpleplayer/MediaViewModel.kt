package br.tec.pauloduarte.simpleplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import br.tec.pauloduarte.simpleplayer.data.Media
import br.tec.pauloduarte.simpleplayer.data.MediaDao
import kotlinx.coroutines.flow.Flow

class MediaViewModel(private val mediaDao: MediaDao) : ViewModel() {

    fun getMedias(playlistId: Int): Flow<PagingData<Media>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { mediaDao.getMediaForPlaylist(playlistId) }
        ).flow.cachedIn(viewModelScope)
    }

    fun searchMedias(playlistId: Int, query: String): Flow<PagingData<Media>> {
        val searchQuery = "%${query.replace(' ', '%')}%"
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { mediaDao.searchMediaInPlaylist(playlistId, searchQuery) }
        ).flow.cachedIn(viewModelScope)
    }
}