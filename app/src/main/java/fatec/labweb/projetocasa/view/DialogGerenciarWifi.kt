package fatec.labweb.projetocasa.view

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.DadosDispositivo
import fatec.labweb.projetocasa.model.RedeWifiItem
import fatec.labweb.projetocasa.service.DispositivoService
import fatec.labweb.projetocasa.util.WifiCryptoHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modal para gerenciar redes Wi-Fi (Principal + até 4 Reservas) do hardware ESP32.
 * Compatível com o firmware v11 e regras de criptografia AES-256-GCM.
 */
class DialogGerenciarWifi(
    private val activity: Activity,
    private val macDispositivo: String,
    private val onAtualizado: (() -> Unit)? = null
) {
    private val dispositivoService = DispositivoService()
    private val redesEmEdicao = mutableListOf<RedeWifiItem>()
    private var chaveWifiAtual: String? = null
    private var dispositivoCarregado: DadosDispositivo? = null

    fun mostrar() {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_gerenciar_redes_wifi, null)
        val btnFechar = dialogView.findViewById<ImageView>(R.id.btnFecharGerenciarWifi)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarWifi)
        val txtSubtitulo = dialogView.findViewById<TextView>(R.id.txtSubtituloWifi)
        val txtStatusRedeAtual = dialogView.findViewById<TextView>(R.id.txtStatusRedeAtual)
        val txtVersaoWifiStatus = dialogView.findViewById<TextView>(R.id.txtVersaoWifiStatus)
        val containerLista = dialogView.findViewById<LinearLayout>(R.id.containerListaRedesWifi)

        val etNovoSsid = dialogView.findViewById<TextInputEditText>(R.id.etNovoSsidReserva)
        val etNovaSenha = dialogView.findViewById<TextInputEditText>(R.id.etNovaSenhaReserva)
        val btnAdicionarRede = dialogView.findViewById<MaterialButton>(R.id.btnAdicionarRedeALista)

        val btnSalvar = dialogView.findViewById<MaterialButton>(R.id.btnSalvarRedesHardware)
        val progressSalvar = dialogView.findViewById<ProgressBar>(R.id.progressSalvarWifi)
        val txtFeedback = dialogView.findViewById<TextView>(R.id.txtStatusFeedbackWifi)

        txtSubtitulo.text = "MAC: $macDispositivo"

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnFechar?.setOnClickListener { dialog.dismiss() }
        btnCancelar?.setOnClickListener { dialog.dismiss() }

        fun atualizarRenderizacaoLista() {
            containerLista.removeAllViews()

            if (redesEmEdicao.isEmpty()) {
                val tvVazio = TextView(activity).apply {
                    text = "Nenhuma rede reserva cadastrada.\nAdicione até 4 redes secundárias abaixo."
                    setTextColor(activity.getColor(R.color.text_muted))
                    textSize = 12f
                    setPadding(16, 16, 16, 16)
                    gravity = android.view.Gravity.CENTER
                }
                containerLista.addView(tvVazio)
                return
            }

            redesEmEdicao.forEachIndexed { index, rede ->
                val itemView = LayoutInflater.from(activity).inflate(R.layout.item_rede_wifi_configurada, containerLista, false)
                val txtSsid = itemView.findViewById<TextView>(R.id.txtSsidRede)
                val txtTipo = itemView.findViewById<TextView>(R.id.txtTipoRede)
                val btnRemover = itemView.findViewById<ImageView>(R.id.btnRemoverRede)

                txtSsid.text = rede.ssid
                txtTipo.text = "Rede Reserva #${index + 1} (Ordem de contingência)"

                btnRemover.setOnClickListener {
                    redesEmEdicao.removeAt(index)
                    atualizarRenderizacaoLista()
                }

                containerLista.addView(itemView)
            }
        }

        btnAdicionarRede.setOnClickListener {
            val ssid = etNovoSsid.text?.toString()?.trim() ?: ""
            val senha = etNovaSenha.text?.toString() ?: ""

            if (ssid.isEmpty()) {
                etNovoSsid.error = "Informe o SSID da rede."
                return@setOnClickListener
            }

            if (ssid.toByteArray(Charsets.UTF_8).size > 32) {
                etNovoSsid.error = "SSID deve ter no máximo 32 bytes."
                return@setOnClickListener
            }

            if (senha.isNotEmpty() && (senha.length < 8 || senha.length > 63)) {
                etNovaSenha.error = "A senha deve ter entre 8 e 63 caracteres (ou em branco se aberta)."
                return@setOnClickListener
            }

            if (redesEmEdicao.size >= 4) {
                Toast.makeText(activity, "O dispositivo suporta no máximo 4 redes reservas.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (redesEmEdicao.any { it.ssid.equals(ssid, ignoreCase = true) }) {
                etNovoSsid.error = "Esta rede já está na lista."
                return@setOnClickListener
            }

            redesEmEdicao.add(RedeWifiItem(ssid = ssid, senha = senha))
            etNovoSsid.text?.clear()
            etNovaSenha.text?.clear()
            etNovoSsid.error = null
            etNovaSenha.error = null
            atualizarRenderizacaoLista()
        }

        btnSalvar.setOnClickListener {
            val chave = chaveWifiAtual ?: WifiCryptoHelper.gerarChaveAes256().also { chaveWifiAtual = it }

            btnSalvar.isEnabled = false
            progressSalvar.visibility = View.VISIBLE
            txtFeedback.visibility = View.GONE

            CoroutineScope(Dispatchers.IO).launch {
                val resultado = dispositivoService.atualizarRedesWifi(
                    mac = macDispositivo,
                    redes = redesEmEdicao,
                    chaveWifiB64 = chave
                )

                withContext(Dispatchers.Main) {
                    btnSalvar.isEnabled = true
                    progressSalvar.visibility = View.GONE
                    txtFeedback.visibility = View.VISIBLE

                    if (resultado.isSuccess) {
                        txtFeedback.setTextColor(activity.getColor(R.color.status_green))
                        txtFeedback.text = "✓ Redes enviadas! O dispositivo sincronizará automaticamente."
                        onAtualizado?.invoke()
                    } else {
                        txtFeedback.setTextColor(activity.getColor(R.color.danger_red))
                        txtFeedback.text = "Erro: ${resultado.exceptionOrNull()?.message}"
                    }
                }
            }
        }

        // Carrega dados iniciais do dispositivo
        CoroutineScope(Dispatchers.IO).launch {
            val disp = dispositivoService.obterDispositivoPorMac(macDispositivo)
            withContext(Dispatchers.Main) {
                if (disp != null) {
                    dispositivoCarregado = disp
                    chaveWifiAtual = disp.chave_wifi

                    val statusOnlineTexto = if (disp.isOnline) "Online" else "Offline"
                    txtStatusRedeAtual.text = "Conectado a: ${disp.ssid ?: "Desconhecido"} ($statusOnlineTexto)"
                    txtVersaoWifiStatus.text = "Versão Wi-Fi: v${disp.wifi_versao} (Aplicada: v${disp.wifi_versao_aplicada}, Status: ${disp.wifi_status})"

                    redesEmEdicao.clear()
                    disp.wifi_redes.forEach { map ->
                        val ssid = map["ssid"] as? String ?: ""
                        val senhaCifrada = map["senha_cifrada"] as? String ?: ""
                        if (ssid.isNotBlank()) {
                            redesEmEdicao.add(RedeWifiItem(ssid = ssid, senhaCifrada = senhaCifrada))
                        }
                    }
                    atualizarRenderizacaoLista()
                } else {
                    txtStatusRedeAtual.text = "Não foi possível carregar o status do hardware."
                }
            }
        }

        dialog.show()
    }
}
