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

    fun getMedias(playlistId: Int, groupName: String, filterEnabled: Boolean): Flow<PagingData<Media>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (filterEnabled) {
                    mediaDao.getMediaForPlaylistFiltered(playlistId)
                } else {
                    mediaDao.getMediaForPlaylistByGroup(playlistId, groupName)
                }
            }
        ).flow.cachedIn(viewModelScope)
    }

    fun searchMedias(playlistId: Int, query: String, groupName: String, filterEnabled: Boolean): Flow<PagingData<Media>> {
        val searchQuery = "%${query.replace(' ', '%')}%"
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (filterEnabled) {
                    mediaDao.searchMediaInPlaylistFiltered(playlistId, searchQuery)
                } else {
                    mediaDao.searchMediaInPlaylistByGroup(playlistId, searchQuery, groupName)
                }
            }
        ).flow.cachedIn(viewModelScope)
    }

    suspend fun getRandomMedia(playlistId: Int): Media? {
        return mediaDao.getRandomMedia(playlistId)
    }

    suspend fun getRandomMedia(playlistId: Int, filterOver18: Boolean): Media? {
        if (filterOver18) {
            return mediaDao.getRandomMediaFiltered(playlistId)
        }
        return mediaDao.getRandomMedia(playlistId)
    }
}