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

            // Renderização da imagem com Glide (Firebase Storage / URL / Cache Local / Placeholder padrão)
            carregarImagem(item)

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

        private fun carregarImagem(item: CategoriaItem) {
            val imagemAlvo = item.imageUrl ?: item.imagem ?: item.imageUri ?: item.imagem_id

            if (!imagemAlvo.isNullOrEmpty()) {
                imgIconeBotao.visibility = View.VISIBLE
                val context = itemView.context
                when {
                    imagemAlvo.startsWith("http://") || imagemAlvo.startsWith("https://") -> {
                        Glide.with(context)
                            .load(imagemAlvo)
                            .centerCrop()
                            .placeholder(R.drawable.ic_default_button)
                            .error(R.drawable.ic_default_button)
                            .into(imgIconeBotao)
                    }
                    else -> {
                        val local = File(context.filesDir, "uploads/$imagemAlvo")
                        if (local.exists()) {
                            Glide.with(context)
                                .load(local)
                                .centerCrop()
                                .placeholder(R.drawable.ic_default_button)
                                .error(R.drawable.ic_default_button)
                                .into(imgIconeBotao)
                        } else {
                            val storageRef = if (imagemAlvo.contains("/")) {
                                FirebaseStorage.getInstance().reference.child(imagemAlvo)
                            } else {
                                FirebaseStorage.getInstance().reference.child("uploads/$imagemAlvo")
                            }
                            storageRef.downloadUrl
                                .addOnSuccessListener { uri ->
                                    Glide.with(context)
                                        .load(uri)
                                        .centerCrop()
                                        .placeholder(R.drawable.ic_default_button)
                                        .error(R.drawable.ic_default_button)
                                        .into(imgIconeBotao)
                                }
                                .addOnFailureListener {
                                    imgIconeBotao.setImageResource(R.drawable.ic_default_button)
                                }
                        }
                    }
                }
            } else {
                imgIconeBotao.setImageResource(R.drawable.ic_default_button)
            }
        }
    }
}
