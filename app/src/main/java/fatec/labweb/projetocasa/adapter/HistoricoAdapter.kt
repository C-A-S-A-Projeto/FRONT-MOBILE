package fatec.labweb.projetocasa.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.Acionamento
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistoricoAdapter(
    private var itens: List<Acionamento>,
    private val onDeleteClick: (Acionamento) -> Unit
) : RecyclerView.Adapter<HistoricoAdapter.HistoricoViewHolder>() {

    private val formatoIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val formatoLegivel = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())

    fun atualizarItens(novosItens: List<Acionamento>) {
        this.itens = novosItens
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoricoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_historico_real, parent, false)
        return HistoricoViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoricoViewHolder, position: Int) {
        val item = itens[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = itens.size

    inner class HistoricoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtNome = itemView.findViewById<TextView>(R.id.txtNomeBotaoHistorico)
        private val txtCategoria = itemView.findViewById<TextView>(R.id.txtCategoriaHistorico)
        private val txtDataHora = itemView.findViewById<TextView>(R.id.txtDataHoraHistorico)
        private val badgeOrigem = itemView.findViewById<TextView>(R.id.badgeOrigemHistorico)
        private val btnExcluir = itemView.findViewById<ImageView>(R.id.btnExcluirItemHistorico)

        fun bind(item: Acionamento) {
            txtNome.text = item.botao_nome.ifBlank { "Botão" }
            txtCategoria.text = "Categoria: ${item.categoria_nome.ifBlank { "Geral" }}"

            // Formatação amigável de data/hora
            txtDataHora.text = formatarDataHora(item.acionado_em)

            // Badge de Origem
            if (item.origem.equals("dispositivo", ignoreCase = true) || item.origem.equals("hardware", ignoreCase = true)) {
                badgeOrigem.text = "Hardware ESP32"
                badgeOrigem.setTextColor(ContextCompat.getColor(itemView.context, R.color.brand_primary))
                badgeOrigem.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.brand_primary_light)
            } else {
                badgeOrigem.text = "App Mobile"
                badgeOrigem.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_green))
                badgeOrigem.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.card_surface_secondary)
            }

            btnExcluir.setOnClickListener {
                onDeleteClick(item)
            }
        }

        private fun formatarDataHora(dataIso: String): String {
            if (dataIso.isBlank()) return "Data indisponível"
            return try {
                val dataLimpa = if (dataIso.contains(".")) dataIso.substringBefore(".") else dataIso.replace("Z", "")
                val data = formatoIso.parse(dataLimpa)
                if (data != null) formatoLegivel.format(data) else dataIso
            } catch (e: Exception) {
                dataIso
            }
        }
    }
}
