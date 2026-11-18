package fatec.labweb.projetocasa.adapter

import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.repository.CategoriaRepository
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import fatec.labweb.projetocasa.util.SoundManager
import java.io.File

class ItemBotaoAdapter(
    private var todosItens: List<CategoriaItem> = emptyList(),
    private val onItemClick: (CategoriaItem) -> Unit = {},
    private val onPlayAudio: (CategoriaItem) -> Unit = {},
    private val onEditarClick: (CategoriaItem) -> Unit = {},
    private val onExcluirClick: (CategoriaItem) -> Unit = {},
    private val onOrdemAlterada: (List<CategoriaItem>) -> Unit = {}
) : RecyclerView.Adapter<ItemBotaoAdapter.BotaoViewHolder>() {

    private var itensExibidos: List<CategoriaItem> = todosItens

    fun atualizarLista(novaLista: List<CategoriaItem>) {
        val copia = novaLista.map { it.copy() }
        this.todosItens = copia
        this.itensExibidos = copia
        notifyDataSetChanged()
    }

    fun atualizarItem(item: CategoriaItem) {
        val itemCopia = item.copy()
        val index = todosItens.indexOfFirst { it.id == itemCopia.id }
        if (index != -1) {
            val lista = todosItens.toMutableList()
            lista[index] = itemCopia
            this.todosItens = lista
            this.itensExibidos = lista
            notifyItemChanged(index)
        } else {
            val lista = todosItens.toMutableList()
            lista.add(itemCopia)
            this.todosItens = lista
            this.itensExibidos = lista
            notifyDataSetChanged()
        }
        notifyDataSetChanged()
    }

    fun adicionarBotao(novoBotao: CategoriaItem) {
        val botaoCopia = novoBotao.copy()
        val index = todosItens.indexOfFirst { it.id == botaoCopia.id }
        val lista = todosItens.toMutableList()
        if (index != -1) {
            lista[index] = botaoCopia
        } else {
            lista.add(botaoCopia)
        }
        this.todosItens = lista
        this.itensExibidos = lista
        notifyDataSetChanged()
    }

    fun obterLista(): MutableList<CategoriaItem> = todosItens.toMutableList()

    fun moverItem(dePos: Int, paraPos: Int) {
        if (dePos < 0 || paraPos < 0 || dePos >= todosItens.size || paraPos >= todosItens.size) return
        val lista = todosItens.toMutableList()
        val item = lista.removeAt(dePos)
        lista.add(paraPos, item)
        lista.forEachIndexed { idx, itm -> itm.ordem = idx }
        this.todosItens = lista
        this.itensExibidos = lista
        notifyItemMoved(dePos, paraPos)
        notifyItemRangeChanged(minOf(dePos, paraPos), kotlin.math.abs(dePos - paraPos) + 1)
        onOrdemAlterada(lista)
    }

    fun trocarItens(slotA: Int, slotB: Int) {
        if (slotA < 0 || slotB < 0 || slotA >= todosItens.size || slotB >= todosItens.size) return
        val lista = todosItens.toMutableList()
        java.util.Collections.swap(lista, slotA, slotB)
        lista.forEachIndexed { idx, itm -> itm.ordem = idx }
        this.todosItens = lista
        this.itensExibidos = lista
        notifyDataSetChanged()
        onOrdemAlterada(lista)
    }

    fun removerItem(botaoId: String) {
        val index = todosItens.indexOfFirst { it.id == botaoId }
        if (index != -1) {
            val lista = todosItens.toMutableList()
            lista.removeAt(index)
            this.todosItens = lista
            this.itensExibidos = lista
            notifyItemRemoved(index)
            notifyDataSetChanged()
        }
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
        holder.itemView.isSoundEffectsEnabled = true
        holder.bind(itensExibidos[position], position)
    }

    override fun getItemCount(): Int = itensExibidos.size

    inner class BotaoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val btnReordenar: ImageView? = itemView.findViewById(R.id.btnReordenar)
        private val txtBadgeSlot: TextView? = itemView.findViewById(R.id.txtBadgeSlot)
        private val imgIconeBotao: ImageView = itemView.findViewById(R.id.imgIconeBotao)
            ?: itemView.findViewById(R.id.imgBotaoItem)
        private val txtTitulo: TextView = itemView.findViewById(R.id.txtTituloBotao)
        private val txtSubtitulo: TextView? = itemView.findViewById(R.id.txtSubtituloBotao)
        private val btnTocarAudio: ImageButton? = itemView.findViewById(R.id.btnTocarAudio)
        private val btnEditar: ImageButton = itemView.findViewById(R.id.btnEditarBotao)
        private val btnExcluir: ImageButton = itemView.findViewById(R.id.btnExcluirBotao)

        fun bind(item: CategoriaItem, position: Int) {
            val isPredefined = CategoriaRepository.isAcaoPredefinida(item)
            val itemExibir = if (isPredefined) CategoriaRepository.blindarAcaoPredefinida(item) else item

            val rotuloFinal = itemExibir.rotulo.ifBlank { itemExibir.title.ifBlank { "Botão" } }
            txtTitulo.text = rotuloFinal
            txtBadgeSlot?.text = "Botão ${position + 1}"

            val generoEfetivo = MidiaLocalHelper.obterGeneroBotao(itemView.context, itemExibir.id, itemExibir.defaultGender)
            if (txtSubtitulo != null) {
                val labelGenero = if (generoEfetivo == GeneroAudio.MALE) "masculina" else "feminina"
                txtSubtitulo.text = "Ordem ${itemExibir.ordem + 1} • Voz $labelGenero"
            }

            // 1. Renderização Prioritária com Glide: Arquivo Local Permanente -> URL -> Desenho Padrão
            val localFile = if (!isPredefined) {
                MidiaLocalHelper.obterArquivoLocal(itemView.context, itemExibir.id)
                    ?: (!itemExibir.localPath.isNullOrEmpty()).let {
                        if (it) File(itemExibir.localPath!!).takeIf { f -> f.exists() && f.length() > 0 } else null
                    }
            } else null

            val imageSource: Any = when {
                !isPredefined && localFile != null && localFile.exists() -> localFile
                !itemExibir.imageUrl.isNullOrEmpty() && !isPredefined -> itemExibir.imageUrl!!
                !itemExibir.imageUri.isNullOrEmpty() && !isPredefined -> itemExibir.imageUri!!
                !itemExibir.imagem.isNullOrEmpty() && !isPredefined && File(itemExibir.imagem!!).exists() -> File(itemExibir.imagem!!)
                DesenhoBotao.obterDrawable(itemExibir.rotulo, itemExibir.significado) != null -> DesenhoBotao.obterDrawable(itemExibir.rotulo, itemExibir.significado)!!
                !itemExibir.imageUrl.isNullOrEmpty() -> itemExibir.imageUrl!!
                !itemExibir.imageUri.isNullOrEmpty() -> itemExibir.imageUri!!
                !itemExibir.imagem.isNullOrEmpty() -> itemExibir.imagem!!
                else -> R.drawable.ic_default_button
            }

            imgIconeBotao.visibility = View.VISIBLE
            imgIconeBotao.clearColorFilter()
            imgIconeBotao.imageTintList = null

            val glideReq = Glide.with(itemView.context)
                .load(imageSource)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_default_button)
                .error(R.drawable.ic_default_button)

            if (imageSource is File) {
                glideReq.signature(ObjectKey("${imageSource.absolutePath}_${imageSource.lastModified()}"))
            }
            glideReq.into(imgIconeBotao)

            // 2. Reprodução de Áudio ao clicar no ícone de som com emissão de audio cue
            btnTocarAudio?.visibility = View.VISIBLE
            btnTocarAudio?.setOnClickListener { view ->
                view.isSoundEffectsEnabled = true
                view.playSoundEffect(SoundEffectConstants.CLICK)
                SoundManager.dispararAudioCue(view)
                onPlayAudio(itemExibir)
            }

            // 3. Clique no item todo aciona o áudio
            itemView.setOnClickListener { view ->
                view.isSoundEffectsEnabled = true
                view.playSoundEffect(SoundEffectConstants.CLICK)
                SoundManager.dispararAudioCue(view)
                onPlayAudio(itemExibir)
                onItemClick(itemExibir)
            }

            // 4. Ícone de editar (lápis):
            // Se for ação personalizada (!isPredefined): o ícone de lápis DEVE aparecer e funcionar normalmente.
            // Se for ação pré-definida (isPredefined): oculta apenas o ícone de lápis nesse item específico.
            btnEditar.visibility = if (isPredefined) View.GONE else View.VISIBLE
            btnEditar.isEnabled = !isPredefined
            btnEditar.setOnClickListener { view ->
                view.isSoundEffectsEnabled = true
                view.playSoundEffect(SoundEffectConstants.CLICK)
                SoundManager.dispararAudioCue(view)
                onEditarClick(itemExibir)
            }

            // 5. Ícone de excluir (lixeira): sempre visível e habilitado para permitir substituição
            btnExcluir.visibility = View.VISIBLE
            btnExcluir.isEnabled = true
            btnExcluir.setOnClickListener { view ->
                view.isSoundEffectsEnabled = true
                view.playSoundEffect(SoundEffectConstants.CLICK)
                SoundManager.dispararAudioCue(view)
                onExcluirClick(itemExibir)
            }
        }
    }
}
