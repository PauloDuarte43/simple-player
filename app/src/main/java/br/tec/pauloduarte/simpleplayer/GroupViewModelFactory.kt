package br.tec.pauloduarte.simpleplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import br.tec.pauloduarte.simpleplayer.data.MediaDao

class GroupViewModelFactory(private val mediaDao: MediaDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GroupViewModel(mediaDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}