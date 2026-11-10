package fatec.labweb.projetocasa.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.CategoriaItem
import java.io.File

class ItemBotaoAdapter(
    private var todosItens: List<CategoriaItem> = emptyList(),
    private val onItemClick: (CategoriaItem) -> Unit = {},
    private val onPlayAudio: (CategoriaItem) -> Unit = {},
    private val onEditarClick: (CategoriaItem) -> Unit = {},
    private val onExcluirClick: (CategoriaItem) -> Unit = {}
) : RecyclerView.Adapter<ItemBotaoAdapter.BotaoViewHolder>() {

    private var itensExibidos: List<CategoriaItem> = todosItens

    fun atualizarLista(novaLista: List<CategoriaItem>) {
        this.todosItens = novaLista
        this.itensExibidos = novaLista
        notifyDataSetChanged()
    }

    fun atualizarItem(item: CategoriaItem) {
        val index = todosItens.indexOfFirst { it.id == item.id }
        if (index != -1) {
            val lista = todosItens.toMutableList()
            lista[index] = item
            this.todosItens = lista
            this.itensExibidos = lista
            notifyItemChanged(index)
        }
        notifyDataSetChanged()
    }

    fun filtrar(texto: String) {
        val query = texto.trim().lowercase()
        itensExibidos = if (query.isEmpty()) {
            todosItens
        } else {
            todosItens.filter {
                it.rotulo.lowercase().contains(query) || it.significado.lowercase().contains(query)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BotaoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_categoria_botao, parent, false)
        return BotaoViewHolder(view)
    }

    override fun onBindViewHolder(holder: BotaoViewHolder, position: Int) {
        holder.bind(itensExibidos[position])
    }

    override fun getItemCount(): Int = itensExibidos.size

    inner class BotaoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgIconeBotao: ImageView = itemView.findViewById(R.id.imgIconeBotao)
            ?: itemView.findViewById(R.id.imgBotaoItem)
        private val txtTitulo: TextView = itemView.findViewById(R.id.txtTituloBotao)
        private val txtSubtitulo: TextView? = itemView.findViewById(R.id.txtSubtituloBotao)
        private val btnTocarAudio: ImageButton? = itemView.findViewById(R.id.btnTocarAudio)
        private val btnEditar: ImageButton = itemView.findViewById(R.id.btnEditarBotao)
        private val btnExcluir: ImageButton = itemView.findViewById(R.id.btnExcluirBotao)

        fun bind(item: CategoriaItem) {
            val rotuloFinal = item.rotulo.ifBlank { item.title.ifBlank { "Botão" } }
            txtTitulo.text = rotuloFinal

            if (txtSubtitulo != null) {
                txtSubtitulo.text = if (item.significado.isNotBlank() && item.significado != rotuloFinal) {
                    item.significado
                } else {
                    "Ordem ${item.ordem + 1} • Voz ${item.defaultGender.name.lowercase()}"
                }
            }

            // 1. Exibição Obrigatória da Foto do Botão via Glide
            val fotoUrl = item.imageUrl?.takeIf { it.isNotBlank() }
                ?: item.imagem?.takeIf { it.isNotBlank() }
                ?: item.imageUri?.takeIf { it.isNotBlank() }

            if (!fotoUrl.isNullOrEmpty()) {
                imgIconeBotao.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(fotoUrl)
                    .centerCrop()
                    .into(imgIconeBotao)
            } else {
                imgIconeBotao.setImageResource(R.drawable.ic_default_button)
            }

            // Áudio: Ação do botão de som
            btnTocarAudio?.visibility = View.VISIBLE
            btnTocarAudio?.setOnClickListener {
                onPlayAudio(item)
            }

            // Clique no item todo aciona o áudio
            itemView.setOnClickListener {
                onPlayAudio(item)
                onItemClick(item)
            }

            // Ícone de editar (lápis)
            btnEditar.setOnClickListener {
                onEditarClick(item)
            }

            // Ícone de excluir (lixeira)
            btnExcluir.setOnClickListener {
                onExcluirClick(item)
            }
        }
    }
}
