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
        private val imgCategoria: ImageView = itemView.findViewById(R.id.imgCategoria)
        private val btnTocarAudio: ImageView? = itemView.findViewById(R.id.btnTocarAudioCategoria)
        private val btnEditar: ImageView = itemView.findViewById(R.id.btnEditarCategoria)
        private val btnExcluir: ImageView = itemView.findViewById(R.id.btnExcluirCategoria)
        private val btnAcessar: MaterialButton = itemView.findViewById(R.id.btnAcessarCategoria)

        fun bind(categoria: Categoria) {
            txtNomeCategoria.text = categoria.nome
            txtQuantidadeBotoes.text = "${categoria.botoes.size} / 5 botões"

            // Carregamento REAL de Fotos com Glide (Firebase Storage / URL / Cache Local)
            carregarImagemReal(categoria)

            // Botão de som: Dispara reprodução de áudio via MediaPlayer nativo
            btnTocarAudio?.setOnClickListener {
                onPlayAudioClick(categoria)
            }

            // Ícone de editar (lápis): Dispara abertura do modal de edição com update no Firestore
            btnEditar.setOnClickListener {
                onEditarClick(categoria)
            }

            // Ícone de excluir (lixeira): Dispara modal de confirmação e exclusão no Firestore
            btnExcluir.setOnClickListener {
                onExcluirClick(categoria)
            }

            // Cor de destaque personalizada do botão de acesso
            try {
                if (categoria.cor.isNotBlank()) {
                    btnAcessar.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(Color.parseColor(categoria.cor))
                }
            } catch (_: Exception) {
                btnAcessar.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2563EB"))
            }

            // Clique no botão ou no card para navegar aos botões da categoria
            btnAcessar.setOnClickListener { onAcessarClick(categoria) }
            itemView.setOnClickListener { onAcessarClick(categoria) }
        }

        private fun carregarImagemReal(categoria: Categoria) {
            val imageUrl = categoria.imageUri?.takeIf { it.isNotBlank() }
            val slugLower = (categoria.slug.ifBlank { categoria.nome }).lowercase()
            val placeholderIcone = when {
                slugLower.contains("casa") -> R.drawable.baseline_home_24
                slugLower.contains("escola") -> android.R.drawable.ic_menu_agenda
                slugLower.contains("desejo") -> android.R.drawable.ic_menu_compass
                slugLower.contains("objeto") -> android.R.drawable.ic_menu_preferences
                slugLower.contains("animai") -> android.R.drawable.ic_menu_gallery
                else -> android.R.drawable.ic_menu_info_details
            }

            if (imageUrl.isNullOrBlank()) {
                imgCategoria.setImageResource(placeholderIcone)
                return
            }

            val context = itemView.context
            when {
                // URL remota direta (Firebase Storage downloadUrl ou HTTPS)
                imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> {
                    Glide.with(context)
                        .load(imageUrl)
                        .placeholder(placeholderIcone)
                        .error(placeholderIcone)
                        .into(imgCategoria)
                }

                // Arquivo no cache local
                else -> {
                    val local = File(context.filesDir, "uploads/$imageUrl")
                    if (local.exists()) {
                        Glide.with(context)
                            .load(local)
                            .placeholder(placeholderIcone)
                            .error(placeholderIcone)
                            .into(imgCategoria)
                    } else {
                        // Resolução dinâmica da URL do Firebase Storage
                        val storageRef = if (imageUrl.contains("/")) {
                            FirebaseStorage.getInstance().reference.child(imageUrl)
                        } else {
                            FirebaseStorage.getInstance().reference.child("uploads/$imageUrl")
                        }
                        storageRef.downloadUrl
                            .addOnSuccessListener { uri ->
                                Glide.with(context)
                                    .load(uri)
                                    .placeholder(placeholderIcone)
                                    .error(placeholderIcone)
                                    .into(imgCategoria)
                            }
                            .addOnFailureListener {
                                imgCategoria.setImageResource(placeholderIcone)
                            }
                    }
                }
            }
        }
    }
}
