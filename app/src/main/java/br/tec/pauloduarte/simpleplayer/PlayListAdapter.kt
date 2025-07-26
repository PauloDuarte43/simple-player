package br.tec.pauloduarte.simpleplayer

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.tec.pauloduarte.simpleplayer.data.PlayList

class PlayListAdapter(
    private var playlists: MutableList<PlayList>,
    private val onPlayClick: (PlayList) -> Unit,
    private val onDeleteClick: (PlayList) -> Unit
) : RecyclerView.Adapter<PlayListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.itemName)
        val descriptionTextView: TextView = view.findViewById(R.id.pathOrUrl)
        val playButton: ImageButton = view.findViewById(R.id.playButton)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = playlists.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = playlists[position]

        holder.nameTextView.text = playlist.name
        holder.descriptionTextView.text = playlist.description ?: ""

        val action = {
            val context = holder.itemView.context
            val intent = Intent(context, GroupActivity::class.java)
            intent.putExtra("PLAYLIST_ID", playlist.uid)
            context.startActivity(intent)
        }

        holder.playButton.setOnClickListener { action() }
        holder.itemView.setOnClickListener { action() }
        holder.deleteButton.setOnClickListener { onDeleteClick(playlist) }
    }

    fun updateData(newPlaylists: List<PlayList>) {
        playlists.clear()
        playlists.addAll(newPlaylists)
        notifyDataSetChanged()
    }
}