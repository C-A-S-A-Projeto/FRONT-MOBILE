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
 * CategoriaAdapter: Adaptador de RecyclerView para exibição das Categorias e Botões do Projeto C.A.S.A.
 * Realiza carregamento real de imagens com Glide, suporte a fallback nativo e execução de áudio.
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

            // 1. Renderização de Imagem com Glide (URL / Storage / Cache Local / Placeholder padrão)
            val imageUrl = item.imageUrl ?: item.imageUri
            if (!imageUrl.isNullOrEmpty()) {
                imgIconeBotao.visibility = View.VISIBLE
                val context = itemView.context

                when {
                    imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> {
                        Glide.with(context)
                            .load(imageUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_default_button)
                            .error(R.drawable.ic_default_button)
                            .into(imgIconeBotao)
                    }
                    else -> {
                        val local = File(context.filesDir, "uploads/$imageUrl")
                        if (local.exists()) {
                            Glide.with(context)
                                .load(local)
                                .centerCrop()
                                .placeholder(R.drawable.ic_default_button)
                                .error(R.drawable.ic_default_button)
                                .into(imgIconeBotao)
                        } else {
                            val storageRef = if (imageUrl.contains("/")) {
                                FirebaseStorage.getInstance().reference.child(imageUrl)
                            } else {
                                FirebaseStorage.getInstance().reference.child("uploads/$imageUrl")
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
                // Se não tiver imagem personalizada, exibir o ícone/vetor padrão
                imgIconeBotao.setImageResource(R.drawable.ic_default_button)
            }

            // 2. Ouvinte para o botão de áudio
            btnTocarAudio?.setOnClickListener {
                onPlayAudio?.invoke(item)
            }

            // 3. Ouvinte para o botão de editar
            btnEditar.setOnClickListener {
                onEditarClick(item)
            }

            // 4. Ouvinte para o botão de excluir
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
