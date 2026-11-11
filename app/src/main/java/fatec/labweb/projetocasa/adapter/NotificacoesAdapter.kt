package fatec.labweb.projetocasa.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.Notificacao
import fatec.labweb.projetocasa.util.SoundManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NotificacoesAdapter: Adaptador de RecyclerView para exibição de eventos, acionamentos
 * e telemetria de dispositivos em tempo real com alertas visuais destacados.
 */
class NotificacoesAdapter(
    private var itens: List<Notificacao> = emptyList(),
    private val onItemClick: ((Notificacao) -> Unit)? = null,
    private val onExcluirClick: ((Notificacao) -> Unit)? = null
) : RecyclerView.Adapter<NotificacoesAdapter.NotificacaoViewHolder>() {

    fun atualizarLista(novaLista: List<Notificacao>) {
        this.itens = novaLista
        notifyDataSetChanged()
    }

    fun removerNotificacao(notificacaoId: String) {
        val index = itens.indexOfFirst { it.id == notificacaoId }
        if (index != -1) {
            val lista = itens.toMutableList()
            lista.removeAt(index)
            this.itens = lista
            notifyItemRemoved(index)
        }
    }

    fun obterLista(): List<Notificacao> = itens

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificacaoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notificacao, parent, false)
        return NotificacaoViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificacaoViewHolder, position: Int) {
        holder.itemView.isSoundEffectsEnabled = true
        holder.bind(itens[position])
    }

    override fun getItemCount(): Int = itens.size

    inner class NotificacaoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardNotificacao: MaterialCardView = itemView.findViewById(R.id.cardNotificacao)
        private val flIcone: FrameLayout = itemView.findViewById(R.id.flIconeNotificacao)
        private val imgIcone: ImageView = itemView.findViewById(R.id.imgIconeNotificacao)
        private val txtTitulo: TextView = itemView.findViewById(R.id.txtTituloNotificacao)
        private val txtMensagem: TextView = itemView.findViewById(R.id.txtMensagemNotificacao)
        private val txtTagTipo: TextView = itemView.findViewById(R.id.txtTagTipo)
        private val txtData: TextView = itemView.findViewById(R.id.txtDataNotificacao)
        private val dotNaoLida: View = itemView.findViewById(R.id.dotNaoLida)
        private val btnExcluir: ImageButton = itemView.findViewById(R.id.btnExcluirNotificacao)

        fun bind(item: Notificacao) {
            txtTitulo.text = item.titulo.ifBlank { "Notificação" }
            txtMensagem.text = item.mensagem.ifBlank { "Sem detalhes adicionais." }
            txtData.text = formatarTimestamp(item.timestampMillis)
            dotNaoLida.visibility = if (!item.lida) View.VISIBLE else View.GONE

            // Alertas visuais claros por tipo de evento
            when (item.tipo.lowercase(Locale.ROOT)) {
                "dispositivo", "pareamento", "conexao" -> {
                    // Destaque Verde/Azul para hardware conectado
                    cardNotificacao.strokeColor = Color.parseColor("#10B981")
                    cardNotificacao.strokeWidth = 2
                    flIcone.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
                    imgIcone.setImageResource(android.R.drawable.stat_sys_data_bluetooth)
                    imgIcone.imageTintList = ColorStateList.valueOf(Color.parseColor("#059669"))

                    txtTagTipo.text = if (item.nivelBateria != null) "DISPOSITIVO • ${item.nivelBateria}%" else "DISPOSITIVO"
                    txtTagTipo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
                    txtTagTipo.setTextColor(Color.parseColor("#065F46"))
                }
                "interacao", "clique", "acionamento" -> {
                    // Destaque Âmbar/Laranja para acionamentos em tempo real
                    cardNotificacao.strokeColor = Color.parseColor("#F59E0B")
                    cardNotificacao.strokeWidth = 2
                    flIcone.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                    imgIcone.setImageResource(android.R.drawable.ic_dialog_info)
                    imgIcone.imageTintList = ColorStateList.valueOf(Color.parseColor("#D97706"))

                    txtTagTipo.text = "ACIONAMENTO"
                    txtTagTipo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                    txtTagTipo.setTextColor(Color.parseColor("#92400E"))
                }
                "bateria", "alerta", "aviso" -> {
                    // Destaque Vermelho para alertas críticos
                    cardNotificacao.strokeColor = Color.parseColor("#EF4444")
                    cardNotificacao.strokeWidth = 2
                    flIcone.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    imgIcone.setImageResource(android.R.drawable.stat_notify_error)
                    imgIcone.imageTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))

                    txtTagTipo.text = "ALERTA"
                    txtTagTipo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    txtTagTipo.setTextColor(Color.parseColor("#991B1B"))
                }
                else -> {
                    // Padrão Sistema / Info
                    cardNotificacao.strokeColor = Color.parseColor("#E2E8F0")
                    cardNotificacao.strokeWidth = 1
                    flIcone.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEF2FF"))
                    imgIcone.setImageResource(android.R.drawable.ic_popup_reminder)
                    imgIcone.imageTintList = ColorStateList.valueOf(Color.parseColor("#2563EB"))

                    txtTagTipo.text = "SISTEMA"
                    txtTagTipo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DBEAFE"))
                    txtTagTipo.setTextColor(Color.parseColor("#1E40AF"))
                }
            }

            itemView.setOnClickListener { view ->
                view.playSoundEffect(SoundEffectConstants.CLICK)
                SoundManager.dispararAudioCue(view)
                onItemClick?.invoke(item)
            }

            btnExcluir.setOnClickListener { view ->
                view.playSoundEffect(SoundEffectConstants.CLICK)
                SoundManager.dispararAudioCue(view)
                onExcluirClick?.invoke(item)
            }
        }

        private fun formatarTimestamp(timestamp: Long): String {
            if (timestamp <= 0L) return "Recentemente"
            val agora = System.currentTimeMillis()
            val diferenca = agora - timestamp

            return when {
                diferenca < 60_000L -> "Agora mesmo"
                diferenca < 3_600_000L -> "Há ${diferenca / 60_000L} min"
                diferenca < 86_400_000L -> "Há ${diferenca / 3_600_000L} h"
                else -> {
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }
}
