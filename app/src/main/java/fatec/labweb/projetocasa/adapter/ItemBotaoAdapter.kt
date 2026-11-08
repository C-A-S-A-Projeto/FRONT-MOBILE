package fatec.labweb.projetocasa.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.CategoriaItem
import java.io.File

class ItemBotaoAdapter(
    private var itens: List<CategoriaItem> = emptyList(),
    private val onEditarClick: (CategoriaItem) -> Unit = {},
    private val onExcluirClick: (CategoriaItem) -> Unit = {}
) : RecyclerView.Adapter<ItemBotaoAdapter.BotaoViewHolder>() {

    fun atualizarLista(novaLista: List<CategoriaItem>) {
        this.itens = novaLista
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BotaoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_botao_comunicacao, parent, false)
        return BotaoViewHolder(view)
    }

    override fun onBindViewHolder(holder: BotaoViewHolder, position: Int) {
        holder.bind(itens[position])
    }

    override fun getItemCount(): Int = itens.size

    inner class BotaoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgBotao: ImageView = itemView.findViewById(R.id.imgBotaoItem)
        private val txtTitulo: TextView = itemView.findViewById(R.id.txtTituloBotao)
        private val btnEditar: ImageButton = itemView.findViewById(R.id.btnEditarBotao)
        private val btnExcluir: ImageButton = itemView.findViewById(R.id.btnExcluirBotao)

        fun bind(item: CategoriaItem) {
            txtTitulo.text = item.rotulo.ifBlank { item.title }

            val nomeArquivo = item.imagem
            if (!nomeArquivo.isNullOrBlank()) {
                val arquivoLocal = File(itemView.context.filesDir, "uploads/$nomeArquivo")
                if (arquivoLocal.exists()) {
                    Glide.with(itemView.context).load(arquivoLocal).into(imgBotao)
                } else {
                    imgBotao.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                imgBotao.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            btnEditar.setOnClickListener { onEditarClick(item) }
            btnExcluir.setOnClickListener { onExcluirClick(item) }
        }
    }
}
