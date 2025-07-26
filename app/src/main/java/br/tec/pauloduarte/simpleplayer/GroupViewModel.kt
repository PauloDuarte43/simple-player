package br.tec.pauloduarte.simpleplayer

import androidx.lifecycle.ViewModel
import br.tec.pauloduarte.simpleplayer.data.MediaDao
import kotlinx.coroutines.flow.Flow

class GroupViewModel(private val mediaDao: MediaDao) : ViewModel() {

    fun getGroups(playlistId: Int): Flow<List<String?>> {
        return mediaDao.getDistinctGroupNames(playlistId)
    }
}