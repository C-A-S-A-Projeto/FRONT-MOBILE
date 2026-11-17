package fatec.labweb.projetocasa.view

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.DesenhoBotao

class DialogSeletorDesenhos(
    private val context: Context,
    private val desenhosEmUso: Map<DesenhoBotao, String> = emptyMap(),
    private val onDesenhoSelecionado: (DesenhoBotao) -> Unit
) {
    constructor(
        context: Context,
        onDesenhoSelecionado: (DesenhoBotao) -> Unit
    ) : this(context, emptyMap(), onDesenhoSelecionado)

    fun exibir() {
        val lista = DesenhoBotao.values().toList()

        val recyclerView = RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = GridLayoutManager(context, 3)
            setPadding(8, 8, 8, 8)
            clipToPadding = false
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Desenhos Oficiais C.A.S.A. (Google Drive)")
            .setView(recyclerView)
            .setNegativeButton("Cancelar", null)
            .create()

        recyclerView.adapter = DesenhoAdapter(lista, desenhosEmUso) { desenho ->
            onDesenhoSelecionado(desenho)
            dialog.dismiss()
        }

        dialog.show()
    }

    private class DesenhoAdapter(
        private val itens: List<DesenhoBotao>,
        private val desenhosEmUso: Map<DesenhoBotao, String>,
        private val onClick: (DesenhoBotao) -> Unit
    ) : RecyclerView.Adapter<DesenhoAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.imgDesenho)
            val txt: TextView = view.findViewById(R.id.tvTituloDesenho)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desenho_seletor, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = itens[position]
            val categoriaAlocada = desenhosEmUso[item]
            val jaEmUso = !categoriaAlocada.isNullOrBlank()

            holder.img.setImageResource(item.resId)

            if (jaEmUso) {
                holder.itemView.alpha = 0.38f
                holder.txt.text = "${item.titulo}\n(Em uso: $categoriaAlocada)"
                holder.itemView.setOnClickListener {
                    val rotuloCurto = item.titulo.split("/").first().trim()
                    Toast.makeText(
                        holder.itemView.context,
                        "O botão \"$rotuloCurto\" já está vinculado à categoria \"$categoriaAlocada\"! Não é permitido repetir botões entre categorias.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                holder.itemView.alpha = 1.0f
                holder.txt.text = item.titulo
                holder.itemView.setOnClickListener { onClick(item) }
            }
        }

        override fun getItemCount(): Int = itens.size
    }
}
