package br.tec.pauloduarte.simpleplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import br.tec.pauloduarte.simpleplayer.data.Media

class MediaAdapter(
    private val onItemClick: (Media) -> Unit
) : PagingDataAdapter<Media, MediaAdapter.ViewHolder>(MEDIA_COMPARATOR) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val mediaNameTextView: TextView = view.findViewById(R.id.mediaName)
        val mediaGroupTextView: TextView = view.findViewById(R.id.mediaGroup)

        fun bind(media: Media?, onItemClick: (Media) -> Unit) {
            media?.let {
                itemView.setOnClickListener { onItemClick(media) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.media_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = getItem(position)
        holder.mediaNameTextView.text = media?.name
        holder.mediaGroupTextView.text = media?.groupName
        holder.bind(media, onItemClick)
    }

    companion object {
        private val MEDIA_COMPARATOR = object : DiffUtil.ItemCallback<Media>() {
            override fun areItemsTheSame(oldItem: Media, newItem: Media): Boolean =
                oldItem.uid == newItem.uid

            override fun areContentsTheSame(oldItem: Media, newItem: Media): Boolean =
                oldItem == newItem
        }
    }
}