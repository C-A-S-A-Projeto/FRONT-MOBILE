package fatec.labweb.projetocasa.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.service.BleDispositivoInfo

class BleDeviceAdapter(
    private val onDeviceClick: (BleDispositivoInfo) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    private val itens = mutableListOf<BleDispositivoInfo>()

    fun atualizarLista(novaLista: List<BleDispositivoInfo>) {
        itens.clear()
        itens.addAll(novaLista)
        notifyDataSetChanged()
    }

    fun adicionarOuAtualizar(disp: BleDispositivoInfo) {
        val idx = itens.indexOfFirst { it.enderecoMac.equals(disp.enderecoMac, ignoreCase = true) }
        if (idx >= 0) {
            itens[idx] = disp
            notifyItemChanged(idx)
        } else {
            itens.add(disp)
            notifyItemInserted(itens.size - 1)
        }
    }

    fun limpar() {
        itens.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dispositivo_ble, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(itens[position])
    }

    override fun getItemCount(): Int = itens.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtNome: TextView = itemView.findViewById(R.id.txtNomeBleItem)
        private val txtSubtitulo: TextView = itemView.findViewById(R.id.txtSubtituloBleItem)
        private val txtBadgeSinal: TextView = itemView.findViewById(R.id.txtBadgeSinalBle)

        fun bind(item: BleDispositivoInfo) {
            txtNome.text = item.nome

            val subtitulo = if (item.isPareadoSistema) {
                "${item.enderecoMac} • Pareado no Android"
            } else {
                "${item.enderecoMac} • BLE Detectado"
            }
            txtSubtitulo.text = subtitulo

            if (item.isPareadoSistema) {
                txtBadgeSinal.text = "Pareado"
            } else if (item.rssi != 0) {
                txtBadgeSinal.text = "${item.rssi} dBm"
            } else {
                txtBadgeSinal.text = "Pronto"
            }

            itemView.setOnClickListener {
                onDeviceClick(item)
            }
        }
    }
}
