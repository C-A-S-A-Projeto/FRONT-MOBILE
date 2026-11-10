package fatec.labweb.projetocasa.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.button.MaterialButton
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import java.io.File

/**
 * CategoriaAdapter: Adaptador de RecyclerView para exibição e renderização visual
 * das Categorias do Projeto C.A.S.A., com carregamento real de fotos via Glide e reprodução de áudio.
 */
class CategoriaAdapter(
    private var todasCategorias: List<Categoria> = emptyList(),
    private val onAcessarClick: (Categoria) -> Unit = {},
    private val onPlayAudio: ((Categoria) -> Unit)? = null,
    private val onEditarClick: (Categoria) -> Unit = {},
    private val onExcluirClick: (Categoria) -> Unit = {}
) : RecyclerView.Adapter<CategoriaAdapter.CategoriaViewHolder>() {

    private var itensExibidos: List<Categoria> = todasCategorias

    fun atualizarLista(novaLista: List<Categoria>) {
        this.todasCategorias = novaLista
        this.itensExibidos = novaLista
        notifyDataSetChanged()
    }

    fun atualizarDados(novaLista: List<Categoria>) {
        atualizarLista(novaLista)
    }

    fun atualizarItem(categoria: Categoria) {
        val index = todasCategorias.indexOfFirst { it.id == categoria.id }
        if (index != -1) {
            val lista = todasCategorias.toMutableList()
            lista[index] = categoria
            this.todasCategorias = lista
            this.itensExibidos = lista
            notifyItemChanged(index)
        }
        notifyDataSetChanged()
    }

    fun filtrar(texto: String) {
        val query = texto.trim().lowercase()
        itensExibidos = if (query.isEmpty()) {
            todasCategorias
        } else {
            todasCategorias.filter {
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
        holder.bind(itensExibidos[position])
    }

    override fun getItemCount(): Int = itensExibidos.size

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

            // 1. Renderização Visual das Fotos no Adapter (CategoriaAdapter.kt) via Glide sem cache
            if (!item.imageUrl.isNullOrEmpty()) {
                imgIconeBotao.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(item.imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
                    .into(imgIconeBotao)
            } else if (!item.imageUri.isNullOrEmpty()) {
                imgIconeBotao.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(item.imageUri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
                    .into(imgIconeBotao)
            } else {
                imgIconeBotao.setImageResource(R.drawable.ic_default_button)
            }

            // 2. Ouvinte de clique para reprodução de áudio
            btnTocarAudio?.setOnClickListener {
                onPlayAudio?.invoke(item)
            }

            // 3. Ouvinte de clique para editar
            btnEditar.setOnClickListener {
                onEditarClick(item)
            }

            // 4. Ouvinte de clique para excluir
            btnExcluir.setOnClickListener {
                onExcluirClick(item)
            }

            // 5. Cor de destaque do botão de acesso
            try {
                if (item.cor.isNotBlank()) {
                    btnAcessar.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(Color.parseColor(item.cor))
                }
            } catch (_: Exception) {
                btnAcessar.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2563EB"))
            }

            // 6. Cliques no botão ou no card para abrir os botões da categoria
            btnAcessar.setOnClickListener { onAcessarClick(item) }
            itemView.setOnClickListener {
                if (!item.audioPath.isNullOrBlank() && onPlayAudio != null) {
                    onPlayAudio.invoke(item)
                }
                onAcessarClick(item)
            }
        }
    }
}

/**
 * CategoriaBotaoAdapter: Adaptador de RecyclerView para os botões individuais dentro de uma Categoria.
 */
class CategoriaBotaoAdapter(
    private var itens: List<CategoriaItem> = emptyList(),
    private val onItemClick: (CategoriaItem) -> Unit = {},
    private val onPlayAudio: (CategoriaItem) -> Unit = {},
    private val onEditarClick: (CategoriaItem) -> Unit = {},
    private val onExcluirClick: (CategoriaItem) -> Unit = {}
) : RecyclerView.Adapter<CategoriaBotaoAdapter.BotaoViewHolder>() {

    private var itensExibidos: List<CategoriaItem> = itens

    fun atualizarLista(novaLista: List<CategoriaItem>) {
        this.itens = novaLista
        this.itensExibidos = novaLista
        notifyDataSetChanged()
    }

    fun atualizarItem(item: CategoriaItem) {
        val index = itens.indexOfFirst { it.id == item.id }
        if (index != -1) {
            val lista = itens.toMutableList()
            lista[index] = item
            this.itens = lista
            this.itensExibidos = lista
            notifyItemChanged(index)
        }
        notifyDataSetChanged()
    }

    fun filtrar(texto: String) {
        val query = texto.trim().lowercase()
        itensExibidos = if (query.isEmpty()) {
            itens
        } else {
            itens.filter {
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

            // 1. Exibição Obrigatória da Foto do Botão via Glide sem cache
            if (!item.imageUrl.isNullOrEmpty()) {
                imgIconeBotao.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(item.imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
                    .into(imgIconeBotao)
            } else if (!item.imageUri.isNullOrEmpty()) {
                imgIconeBotao.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(item.imageUri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
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
