package br.tec.pauloduarte.simpleplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.tec.pauloduarte.simpleplayer.data.PlayList

/**
 * Adaptador para exibir uma lista de objetos PlayList em um RecyclerView.
 *
 * @param playlists A lista mutável de playlists a ser exibida.
 * @param onPlayClick Uma função lambda a ser executada quando o botão de play é clicado.
 * @param onDeleteClick Uma função lambda a ser executada quando o botão de deletar é clicado.
 */
class PlayListAdapter(
    private var playlists: MutableList<PlayList>,
    private val onPlayClick: (PlayList) -> Unit,
    private val onDeleteClick: (PlayList) -> Unit
) : RecyclerView.Adapter<PlayListAdapter.ViewHolder>() {

    /**
     * ViewHolder que descreve a view de um item e seus metadados.
     * Ele segura as referências para as views dentro do layout do item.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.itemName)
        val descriptionTextView: TextView = view.findViewById(R.id.pathOrUrl)
        val playButton: ImageButton = view.findViewById(R.id.playButton)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    /**
     * Chamado quando o RecyclerView precisa de um novo ViewHolder para representar um item.
     * Aqui nós inflamos o layout do item a partir do XML.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item, parent, false)
        return ViewHolder(view)
    }

    /**
     * Retorna o número total de itens na lista de dados.
     */
    override fun getItemCount(): Int = playlists.size

    /**
     * Chamado pelo RecyclerView para exibir os dados em uma posição específica.
     * Este método atualiza o conteúdo do ViewHolder para refletir o item na posição dada.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Pega o objeto de dados para esta posição
        val playlist = playlists[position]

        // Preenche as views do ViewHolder com os dados do objeto
        holder.nameTextView.text = playlist.name
        holder.descriptionTextView.text = playlist.description ?: "" // Mostra vazio se a descrição for nula

        // Configura os listeners de clique, passando o objeto de dados específico
        holder.playButton.setOnClickListener { onPlayClick(playlist) }
        holder.deleteButton.setOnClickListener { onDeleteClick(playlist) }
    }

    /**
     * Método público para atualizar a lista de dados no adaptador.
     * Limpa a lista antiga, adiciona todos os novos itens e notifica o RecyclerView
     * que os dados mudaram, fazendo com que a lista seja redesenhada.
     */
    fun updateData(newPlaylists: List<PlayList>) {
        playlists.clear()
        playlists.addAll(newPlaylists)
        notifyDataSetChanged() // Notifica que toda a lista mudou
    }
}