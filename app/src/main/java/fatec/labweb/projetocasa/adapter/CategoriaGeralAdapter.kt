package fatec.labweb.projetocasa.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.Categoria
import java.io.File

/**
 * CategoriaGeralAdapter: Renderiza a lista de categorias do Projeto C.A.S.A.
 * com carregamento real de imagens via Glide, reprodução de áudio, edição e exclusão.
 */
class CategoriaGeralAdapter(
    private var listaCategorias: List<Categoria> = emptyList(),
    private val onAcessarClick: (Categoria) -> Unit = {},
    private val onPlayAudioClick: (Categoria) -> Unit = {},
    private val onEditarClick: (Categoria) -> Unit = {},
    private val onExcluirClick: (Categoria) -> Unit = {}
) : RecyclerView.Adapter<CategoriaGeralAdapter.CategoriaViewHolder>() {

    private var itensFiltrados: List<Categoria> = listaCategorias

    fun atualizarDados(novaLista: List<Categoria>) {
        this.listaCategorias = novaLista
        this.itensFiltrados = novaLista
        notifyDataSetChanged()
    }

    fun atualizarLista(novaLista: List<Categoria>) {
        atualizarDados(novaLista)
    }

    fun atualizarItem(categoria: Categoria) {
        val index = listaCategorias.indexOfFirst { it.id == categoria.id }
        if (index != -1) {
            val lista = listaCategorias.toMutableList()
            lista[index] = categoria
            this.listaCategorias = lista
            this.itensFiltrados = lista
            notifyItemChanged(index)
        } else {
            notifyDataSetChanged()
        }
    }

    fun filtrar(texto: String) {
        val query = texto.trim().lowercase()
        itensFiltrados = if (query.isEmpty()) {
            listaCategorias
        } else {
            listaCategorias.filter {
                it.nome.lowercase().contains(query) || it.slug.lowercase().contains(query)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoriaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_categoria, parent, false)
        return CategoriaViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoriaViewHolder, position: Int) {
        holder.bind(itensFiltrados[position])
    }

    override fun getItemCount(): Int = itensFiltrados.size

    inner class CategoriaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtNomeCategoria: TextView = itemView.findViewById(R.id.txtNomeCategoria)
        private val txtQuantidadeBotoes: TextView = itemView.findViewById(R.id.txtQuantidadeBotoes)
        private val imgIconeBotao: ImageView = itemView.findViewById(R.id.imgIconeBotao)
            ?: itemView.findViewById(R.id.imgCategoria)
        private val btnTocarAudio: ImageView? = itemView.findViewById(R.id.btnTocarAudioCategoria)
        private val btnEditar: ImageView = itemView.findViewById(R.id.btnEditarCategoria)
        private val btnExcluir: ImageView = itemView.findViewById(R.id.btnExcluirCategoria)
        private val btnAcessar: MaterialButton = itemView.findViewById(R.id.btnAcessarCategoria)

        fun bind(item: Categoria) {
            txtNomeCategoria.text = item.nome
            txtQuantidadeBotoes.text = "${item.botoes.size} / 5 botões"

            // 1. Exibição Obrigatória da Foto do Botão via Glide
            val imgUrl = item.imageUrl?.takeIf { it.isNotBlank() } ?: item.imageUri?.takeIf { it.isNotBlank() }
            if (!imgUrl.isNullOrEmpty()) {
                imgIconeBotao.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(imgUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
                    .into(imgIconeBotao)
            } else {
                imgIconeBotao.setImageResource(R.drawable.ic_default_button)
            }

            // Botão de som: Dispara reprodução de áudio via MediaPlayer nativo
            btnTocarAudio?.setOnClickListener {
                onPlayAudioClick(item)
            }

            // Ícone de editar (lápis)
            btnEditar.setOnClickListener {
                onEditarClick(item)
            }

            // Ícone de excluir (lixeira)
            btnExcluir.setOnClickListener {
                onExcluirClick(item)
            }

            // Cor de destaque personalizada do botão de acesso
            try {
                if (item.cor.isNotBlank()) {
                    btnAcessar.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(Color.parseColor(item.cor))
                }
            } catch (_: Exception) {
                btnAcessar.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2563EB"))
            }

            // Clique no botão ou no card para navegar aos botões da categoria
            btnAcessar.setOnClickListener { onAcessarClick(item) }
            itemView.setOnClickListener { onAcessarClick(item) }
        }
    }
}
