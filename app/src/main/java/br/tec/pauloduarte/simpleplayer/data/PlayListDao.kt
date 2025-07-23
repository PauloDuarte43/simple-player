package br.tec.pauloduarte.simpleplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlayListDao {
    @Query("SELECT * FROM playlist")
    fun getAll(): List<PlayList>

    @Query("SELECT * FROM playlist WHERE uid IN (:userIds)")
    fun loadAllByIds(userIds: IntArray): List<PlayList>

    @Query("SELECT * FROM playlist WHERE name LIKE :name LIMIT 1")
    fun findByName(name: String): PlayList

    @Insert
    fun insertAll(vararg users: PlayList)

    @Delete
    fun delete(user: PlayList)
}