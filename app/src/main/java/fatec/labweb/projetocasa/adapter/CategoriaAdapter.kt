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

    fun filtrar(texto: String) {
        val query = texto.trim().lowercase()
        itensExibidos = if (query.isEmpty()) {
            todasCategorias
        } else {
            todasCategorias.filter { it.nome.lowercase().contains(query) || it.slug.lowercase().contains(query) }
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
        private val imgCategoria: ImageView = itemView.findViewById(R.id.imgCategoria)
        private val btnTocarAudio: ImageView? = itemView.findViewById(R.id.btnTocarAudioCategoria)
        private val btnEditar: ImageView = itemView.findViewById(R.id.btnEditarCategoria)
        private val btnExcluir: ImageView = itemView.findViewById(R.id.btnExcluirCategoria)
        private val btnAcessar: MaterialButton = itemView.findViewById(R.id.btnAcessarCategoria)

        fun bind(categoria: Categoria) {
            txtNomeCategoria.text = categoria.nome
            txtQuantidadeBotoes.text = "${categoria.botoes.size} / 5 botões"

            // Carregamento de imagem real com Glide (Firebase Storage / Local Cache / Ícone temático)
            carregarImagemOuIcone(categoria)

            // Áudio: Ação do botão de som
            btnTocarAudio?.setOnClickListener {
                onPlayAudio?.invoke(categoria)
            }

            // Ação de Editar Categoria
            btnEditar.setOnClickListener {
                onEditarClick(categoria)
            }

            // Ação de Excluir Categoria
            btnExcluir.setOnClickListener {
                onExcluirClick(categoria)
            }

            // Cor de destaque no botão se fornecida
            try {
                if (categoria.cor.isNotBlank()) {
                    btnAcessar.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(categoria.cor))
                }
            } catch (_: Exception) {
                btnAcessar.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2563EB"))
            }

            btnAcessar.setOnClickListener { onAcessarClick(categoria) }
            itemView.setOnClickListener {
                if (!categoria.audioPath.isNullOrBlank() && onPlayAudio != null) {
                    onPlayAudio.invoke(categoria)
                }
                onAcessarClick(categoria)
            }
        }

        private fun carregarImagemOuIcone(categoria: Categoria) {
            val imgAlvo = categoria.imageUri?.takeIf { it.isNotBlank() }
            val slugLower = (categoria.slug.ifBlank { categoria.nome }).lowercase()
            val defaultResId = when {
                slugLower.contains("casa") -> R.drawable.baseline_home_24
                slugLower.contains("escola") -> android.R.drawable.ic_menu_agenda
                slugLower.contains("desejo") -> android.R.drawable.ic_menu_compass
                slugLower.contains("objeto") -> android.R.drawable.ic_menu_preferences
                slugLower.contains("animai") -> android.R.drawable.ic_menu_gallery
                else -> android.R.drawable.ic_menu_info_details
            }

            if (imgAlvo.isNullOrBlank()) {
                imgCategoria.setImageResource(defaultResId)
                return
            }

            val context = itemView.context
            when {
                imgAlvo.startsWith("http://") || imgAlvo.startsWith("https://") -> {
                    Glide.with(context)
                        .load(imgAlvo)
                        .placeholder(defaultResId)
                        .error(defaultResId)
                        .into(imgCategoria)
                }
                else -> {
                    val local = File(context.filesDir, "uploads/$imgAlvo")
                    if (local.exists()) {
                        Glide.with(context)
                            .load(local)
                            .placeholder(defaultResId)
                            .error(defaultResId)
                            .into(imgCategoria)
                    } else {
                        val storageRef = if (imgAlvo.contains("/")) {
                            FirebaseStorage.getInstance().reference.child(imgAlvo)
                        } else {
                            FirebaseStorage.getInstance().reference.child("uploads/$imgAlvo")
                        }
                        storageRef.downloadUrl
                            .addOnSuccessListener { uri ->
                                Glide.with(context)
                                    .load(uri)
                                    .placeholder(defaultResId)
                                    .error(defaultResId)
                                    .into(imgCategoria)
                            }
                            .addOnFailureListener {
                                imgCategoria.setImageResource(defaultResId)
                            }
                    }
                }
            }
        }
    }
}
