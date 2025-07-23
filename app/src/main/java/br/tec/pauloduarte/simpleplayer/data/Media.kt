package br.tec.pauloduarte.simpleplayer.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Entity(
    foreignKeys = [
        ForeignKey(
            entity = PlayList::class,
            parentColumns = ["uid"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Media(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo(name = "playlistId", index = true)
    val playlistId: Int,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "groupName") val groupName: String? = null,
    @ColumnInfo(name = "url") val url: String? = null,
)