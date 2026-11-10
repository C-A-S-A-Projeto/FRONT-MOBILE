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
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.util.SoundManager
import java.io.File

/**
 * CategoriaAdapter: Adaptador de RecyclerView para exibição e renderização visual
 * das Categorias do Projeto C.A.S.A., com carregamento prioritário local e suporte aos desenhos do Google Drive.
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
        } else {
            val lista = todasCategorias.toMutableList()
            lista.add(categoria)
            this.todasCategorias = lista
            this.itensExibidos = lista
            notifyDataSetChanged()
        }
        notifyDataSetChanged()
    }

    fun removerItem(categoriaId: String) {
        val index = todasCategorias.indexOfFirst { it.id == categoriaId }
        if (index != -1) {
            val lista = todasCategorias.toMutableList()
            lista.removeAt(index)
            this.todasCategorias = lista
            this.itensExibidos = lista
            notifyItemRemoved(index)
            notifyDataSetChanged()
        }
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

            // 1. Renderização Prioritária com Glide: Arquivo Local -> URL -> Desenho Padrão
            val localFile = File(itemView.context.getExternalFilesDir("botoes_imagens"), "${item.id}.jpg")

            val imageSource: Any = when {
                localFile.exists() -> localFile
                !item.localPath.isNullOrEmpty() && File(item.localPath!!).exists() -> File(item.localPath!!)
                !item.imageUrl.isNullOrEmpty() -> item.imageUrl!!
                !item.imageUri.isNullOrEmpty() -> item.imageUri!!
                DesenhoBotao.obterDrawable(item.nome, item.slug) != null -> DesenhoBotao.obterDrawable(item.nome, item.slug)!!
                else -> R.drawable.ic_default_button
            }

            imgIconeBotao.visibility = View.VISIBLE
            imgIconeBotao.clearColorFilter()
            imgIconeBotao.imageTintList = null

            Glide.with(itemView.context)
                .load(imageSource)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_default_button)
                .error(R.drawable.ic_default_button)
                .into(imgIconeBotao)

            // 2. Ouvinte de clique para reprodução de áudio com feedback sonoro instantâneo
            btnTocarAudio?.setOnClickListener {
                SoundManager.playClickSound(it)
                onPlayAudio?.invoke(item)
            }

            // 3. Ouvinte de clique para editar
            btnEditar.setOnClickListener {
                SoundManager.playClickSound(it)
                onEditarClick(item)
            }

            // 4. Ouvinte de clique para excluir
            btnExcluir.setOnClickListener {
                SoundManager.playClickSound(it)
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
            btnAcessar.setOnClickListener {
                SoundManager.playClickSound(it)
                onAcessarClick(item)
            }
            itemView.setOnClickListener {
                SoundManager.playClickSound(it)
                val audioLocal = File(itemView.context.getExternalFilesDir("botoes_audios"), "${item.id}.m4a")
                if (audioLocal.exists() && onPlayAudio != null) {
                    onPlayAudio.invoke(item)
                } else if (!item.audioUrl.isNullOrBlank() && onPlayAudio != null) {
                    onPlayAudio.invoke(item)
                } else if (!item.audioPath.isNullOrBlank() && onPlayAudio != null) {
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
        } else {
            val lista = itens.toMutableList()
            lista.add(item)
            this.itens = lista
            this.itensExibidos = lista
            notifyDataSetChanged()
        }
        notifyDataSetChanged()
    }

    fun removerItem(botaoId: String) {
        val index = itens.indexOfFirst { it.id == botaoId }
        if (index != -1) {
            val lista = itens.toMutableList()
            lista.removeAt(index)
            this.itens = lista
            this.itensExibidos = lista
            notifyItemRemoved(index)
            notifyDataSetChanged()
        }
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

            // 1. Renderização Prioritária com Glide: Arquivo Local -> URL -> Desenho Padrão
            val localFile = File(itemView.context.getExternalFilesDir("botoes_imagens"), "${item.id}.jpg")

            val imageSource: Any = when {
                localFile.exists() -> localFile
                !item.localPath.isNullOrEmpty() && File(item.localPath!!).exists() -> File(item.localPath!!)
                !item.imageUrl.isNullOrEmpty() -> item.imageUrl!!
                !item.imageUri.isNullOrEmpty() -> item.imageUri!!
                !item.imagem.isNullOrEmpty() -> item.imagem!!
                DesenhoBotao.obterDrawable(item.rotulo, item.significado) != null -> DesenhoBotao.obterDrawable(item.rotulo, item.significado)!!
                else -> R.drawable.ic_default_button
            }

            imgIconeBotao.visibility = View.VISIBLE
            imgIconeBotao.clearColorFilter()
            imgIconeBotao.imageTintList = null

            Glide.with(itemView.context)
                .load(imageSource)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_default_button)
                .error(R.drawable.ic_default_button)
                .into(imgIconeBotao)

            // 2. Reprodução de Áudio ao clicar no ícone de som com feedback sonoro instantâneo
            btnTocarAudio?.visibility = View.VISIBLE
            btnTocarAudio?.setOnClickListener {
                SoundManager.playClickSound(it)
                onPlayAudio(item)
            }

            // 3. Clique no item todo aciona o áudio
            itemView.setOnClickListener {
                SoundManager.playClickSound(it)
                onPlayAudio(item)
                onItemClick(item)
            }

            // 4. Ícone de editar (lápis)
            btnEditar.setOnClickListener {
                SoundManager.playClickSound(it)
                onEditarClick(item)
            }

            // 5. Ícone de excluir (lixeira)
            btnExcluir.setOnClickListener {
                SoundManager.playClickSound(it)
                onExcluirClick(item)
            }
        }
    }
}
