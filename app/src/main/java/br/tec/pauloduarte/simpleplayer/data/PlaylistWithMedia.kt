package br.tec.pauloduarte.simpleplayer.data

import androidx.room.Embedded
import androidx.room.Relation

data class PlaylistWithMedia(
    @Embedded
    val playlist: PlayList,

    @Relation(
        parentColumn = "uid", // Chave primária da entidade "pai" (PlayList)
        entityColumn = "playlistId" // Chave estrangeira da entidade "filha" (Media)
    )
    val mediaItems: List<Media>
)