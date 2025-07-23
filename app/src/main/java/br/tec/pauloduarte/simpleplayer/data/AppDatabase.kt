package br.tec.pauloduarte.simpleplayer.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PlayList::class, Media::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playListDao(): PlayListDao
    abstract fun mediaDao(): MediaDao
}