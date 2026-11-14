package fatec.labweb.projetocasa.view

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fatec.labweb.projetocasa.BemVindoActivity
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.Acionamento
import fatec.labweb.projetocasa.model.DadosDispositivo
import fatec.labweb.projetocasa.service.DispositivoService
import fatec.labweb.projetocasa.service.Esp32TriggerService
import fatec.labweb.projetocasa.util.SoundManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HomeActivity : BaseDrawerActivity() {

    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()
    private val triggerService = Esp32TriggerService()

    private var ultimoAcionamentoCarregado: Acionamento? = null

    override fun getContentLayoutId(): Int {
        return R.layout.activity_home
    }

    override fun getCurrentMenuItemId(): Int {
        return R.id.menuInicio
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val usuario = auth.currentUser
        if (usuario == null) {
            irParaInicio()
            return
        }

        configurarSaudacao(usuario.displayName, usuario.email)
        setupBotoesAcao()
        carregarUltimoAcionamentoEmTempoReal(usuario.uid)
        carregarStatusDispositivoPrincipal(usuario.uid)
    }

    override fun onResume() {
        super.onResume()
        auth.currentUser?.uid?.let { uid ->
            carregarUltimoAcionamentoEmTempoReal(uid)
            carregarStatusDispositivoPrincipal(uid)
        }
    }

    private fun configurarSaudacao(displayName: String?, email: String?) {
        val nomeBase = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")
            ?: "Usuário"
        val primeiroNome = nomeBase.trim().substringBefore(" ")
        findViewById<TextView>(R.id.txtOla)?.text = "Olá, $primeiroNome!"
    }

    private val dispositivoService = DispositivoService()
    private var dispositivoPrincipalAtual: DadosDispositivo? = null

    private fun setupBotoesAcao() {
        // 1. Botão "Gerenciar Dispositivos" -> Abre modal com os aparelhos vinculados, pareamento OLED e controles
        findViewById<MaterialButton>(R.id.btnGerenciarDispositivos)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            mostrarModalGerenciarDispositivos()
        }

        // 2. Botão "Mais Detalhes / Sinal" -> Exibe Modal com métricas reais do aparelho conectado
        findViewById<MaterialButton>(R.id.btnMaisDetalhes)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            mostrarModalStatusConexao()
        }

        // 3. Botão "Ver Histórico" -> Abre via Intent a HistoricoActivity com a listagem dos últimos acionamentos
        findViewById<MaterialButton>(R.id.btnVerHistorico)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            startActivity(Intent(this, HistoricoActivity::class.java))
        }

        // 4. Card Último Acionamento -> Também permite acesso ao histórico completo ao tocar no card
        findViewById<View>(R.id.cardUltimoAcionamento)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            startActivity(Intent(this, HistoricoActivity::class.java))
        }

        // 5. Botão "Alterar Botão Menos Usado" -> Direciona para a tela de Dispositivo (Categorias e Botões da prancha)
        findViewById<MaterialButton>(R.id.btnAlterarBotaoSugerido)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            startActivity(Intent(this, CategoriaGeralActivity::class.java))
        }
    }

    // ==========================================
    // MODAIS DA DASHBOARD COM DADOS REAIS
    // ==========================================

    /**
     * Modal: Gerenciar Dispositivos
     * Consulta em tempo real aos dispositivos vinculados no Firestore
     */
    private fun mostrarModalGerenciarDispositivos() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_gerenciar_dispositivos, null)
        val btnFechar = dialogView.findViewById<ImageView>(R.id.btnFecharGerenciar)
        val btnFecharModal = dialogView.findViewById<MaterialButton>(R.id.btnFecharModalDispositivos)
        val btnSincronizar = dialogView.findViewById<MaterialButton>(R.id.btnSincronizarDispositivo)
        val btnParear = dialogView.findViewById<MaterialButton>(R.id.btnParearDispositivoModal)
        val btnDesvincular = dialogView.findViewById<MaterialButton>(R.id.btnDesvincularDispositivoModal)

        val layoutSemDispositivo = dialogView.findViewById<View>(R.id.layoutSemDispositivoModal)
        val cardDispositivo = dialogView.findViewById<View>(R.id.cardDispositivoModal)
        val txtNome = dialogView.findViewById<TextView>(R.id.txtNomeDispositivoModal)
        val txtStatus = dialogView.findViewById<TextView>(R.id.txtStatusDispositivoModal)
        val dotStatus = dialogView.findViewById<View>(R.id.dotStatusDispositivo)
        val txtMac = dialogView.findViewById<TextView>(R.id.txtMacDispositivoModal)
        val txtIp = dialogView.findViewById<TextView>(R.id.txtIpDispositivoModal)
        val txtBateria = dialogView.findViewById<TextView>(R.id.txtBateriaDispositivoModal)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnFechar?.setOnClickListener { dialog.dismiss() }
        btnFecharModal?.setOnClickListener { dialog.dismiss() }

        val userId = auth.currentUser?.uid ?: return

        fun atualizarDadosModal() {
            lifecycleScope.launch {
                val dispositivos = dispositivoService.listarDispositivosDoUsuario(userId)
                if (dispositivos.isNotEmpty()) {
                    val disp = dispositivos.first()
                    dispositivoPrincipalAtual = disp
                    layoutSemDispositivo?.visibility = View.GONE
                    cardDispositivo?.visibility = View.VISIBLE
                    btnSincronizar?.visibility = View.VISIBLE
                    btnDesvincular?.visibility = View.VISIBLE
                    btnParear?.text = "+ Parear Outro Aparelho"

                    txtNome?.text = disp.nome.ifBlank { "ESP32 C.A.S.A." }
                    val isOnline = disp.statusOnline || disp.ativo
                    txtStatus?.text = if (isOnline) "Online" else "Offline"
                    val corStatus = if (isOnline) Color.parseColor("#10B981") else Color.parseColor("#94A3B8")
                    txtStatus?.setTextColor(corStatus)
                    dotStatus?.backgroundTintList = ColorStateList.valueOf(corStatus)
                    txtMac?.text = "MAC: ${disp.mac}"
                    val ssid = disp.ssid ?: "Wi-Fi"
                    val sinal = if (disp.sinal_wifi != null) "${disp.sinal_wifi}%" else "OK"
                    val ip = disp.ip ?: "Obtendo..."
                    txtIp?.text = "IP: $ip ($ssid $sinal)"
                    txtBateria?.text = "Bateria: ${disp.bateriaEfetiva}% (Alimentado)"

                    btnDesvincular?.setOnClickListener {
                        AlertDialog.Builder(this@HomeActivity)
                            .setTitle("Desvincular Aparelho")
                            .setMessage("Deseja desconectar o aparelho '${disp.nome}' (${disp.mac}) da sua conta?")
                            .setPositiveButton("Sim, Desvincular") { d, _ ->
                                d.dismiss()
                                lifecycleScope.launch {
                                    dispositivoService.removerDispositivo(disp.mac, userId)
                                    Toast.makeText(this@HomeActivity, "Aparelho desvinculado.", Toast.LENGTH_SHORT).show()
                                    atualizarDadosModal()
                                    carregarStatusDispositivoPrincipal(userId)
                                }
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                } else {
                    dispositivoPrincipalAtual = null
                    layoutSemDispositivo?.visibility = View.VISIBLE
                    cardDispositivo?.visibility = View.GONE
                    btnSincronizar?.visibility = View.GONE
                    btnDesvincular?.visibility = View.GONE
                    btnParear?.text = "+ Parear Aparelho (Código OLED)"
                }
            }
        }

        btnParear?.setOnClickListener {
            dialog.dismiss()
            mostrarModalPareamento()
        }

        btnSincronizar?.setOnClickListener {
            btnSincronizar.isEnabled = false
            btnSincronizar.text = "Sincronizando..."
            lifecycleScope.launch {
                val res = dispositivoService.sincronizarDispositivos(userId)
                if (res.isSuccess) {
                    Toast.makeText(this@HomeActivity, "Hardware ESP32 notificado com sucesso!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@HomeActivity, "Falha ao sincronizar hardware.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }

        atualizarDadosModal()
        dialog.show()
    }

    private fun mostrarModalPareamento() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_parear_dispositivo, null)
        val etCodigo = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCodigoPareamento)
        val etNome = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNomeAparelho)
        val txtStatus = dialogView.findViewById<TextView>(R.id.txtStatusPareamento)
        val progress = dialogView.findViewById<android.widget.ProgressBar>(R.id.progressPareando)
        val btnConfirmar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarPareamento)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarPareamento)
        val btnFechar = dialogView.findViewById<ImageView>(R.id.btnFecharModalPareamento)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnFechar?.setOnClickListener { dialog.dismiss() }
        btnCancelar?.setOnClickListener { dialog.dismiss() }

        btnConfirmar?.setOnClickListener {
            val codigo = etCodigo?.text?.toString()?.trim() ?: ""
            val nome = etNome?.text?.toString()?.trim() ?: "ESP32 - C.A.S.A."
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            if (codigo.length != 6) {
                txtStatus?.visibility = View.VISIBLE
                txtStatus?.text = "Digite os 6 dígitos numéricos exibidos no OLED."
                return@setOnClickListener
            }

            txtStatus?.visibility = View.GONE
            progress?.visibility = View.VISIBLE
            btnConfirmar.isEnabled = false

            lifecycleScope.launch {
                val resultado = dispositivoService.parearPorCodigo(codigo, nome, uid)
                progress?.visibility = View.GONE
                btnConfirmar.isEnabled = true

                if (resultado.isSuccess) {
                    dialog.dismiss()
                    Toast.makeText(this@HomeActivity, "Dispositivo '$nome' pareado com sucesso!", Toast.LENGTH_LONG).show()
                    carregarStatusDispositivoPrincipal(uid)
                } else {
                    txtStatus?.visibility = View.VISIBLE
                    txtStatus?.text = resultado.exceptionOrNull()?.message ?: "Falha ao parear dispositivo."
                }
            }
        }

        dialog.show()
    }

    /**
     * Modal: Mais Detalhes / Status Detalhado da Conexão
     * Exibe nível de sinal Wi-Fi %, IP local e status do hardware conectado
     */
    private fun mostrarModalStatusConexao() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_modal_status_conecxao, null)
        val btnFechar = dialogView.findViewById<ImageView>(R.id.btnFecharStatus)
        val btnEntendido = dialogView.findViewById<MaterialButton>(R.id.btnEntendidoStatus)

        val txtIp = dialogView.findViewById<TextView>(R.id.txtEnderecoIp)
        val txtBateria = dialogView.findViewById<TextView>(R.id.txtBateriaStatus)
        val txtFirmware = dialogView.findViewById<TextView>(R.id.txtFirmware)
        val txtMac = dialogView.findViewById<TextView>(R.id.txtEnderecoMac)

        val disp = dispositivoPrincipalAtual
        if (disp != null) {
            val ip = disp.ip ?: "192.168.1.105"
            val ssid = disp.ssid ?: "Wi-Fi"
            val sinal = disp.sinal_wifi ?: 100
            txtIp?.text = "$ip\n(Rede: $ssid)"
            txtBateria?.text = "${disp.bateriaEfetiva}% (Sinal $sinal%)"
            txtFirmware?.text = "${disp.firmware ?: "v8"} (Prancha v${disp.config_versao})"
            txtMac?.text = disp.mac
        } else {
            txtIp?.text = "Nenhum aparelho conectado"
            txtBateria?.text = "N/A"
            txtFirmware?.text = "N/A"
            txtMac?.text = "Sem conexão"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnFechar?.setOnClickListener { dialog.dismiss() }
        btnEntendido?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun carregarStatusDispositivoPrincipal(userId: String) {
        lifecycleScope.launch {
            val dispositivos = dispositivoService.listarDispositivosDoUsuario(userId)
            val txtStatusEsp = findViewById<TextView>(R.id.txtStatusEsp)
            if (dispositivos.isNotEmpty()) {
                val disp = dispositivos.first()
                dispositivoPrincipalAtual = disp
                val isOnline = disp.statusOnline || disp.ativo
                val statusStr = if (isOnline) "Online" else "Offline"
                txtStatusEsp?.text = "${disp.nome.ifBlank { "ESP32" }} • $statusStr"
            } else {
                dispositivoPrincipalAtual = null
                txtStatusEsp?.text = "Nenhum aparelho pareado"
            }
        }
    }

    private fun carregarUltimoAcionamentoEmTempoReal(userId: String) {
        lifecycleScope.launch {
            try {
                val snap = firestore.collection("usuarios")
                    .document(userId)
                    .collection("acionamentos")
                    .orderBy("acionado_em", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                if (!snap.isEmpty) {
                    val ac = snap.documents.first().toObject(Acionamento::class.java)
                    if (ac != null) {
                        ultimoAcionamentoCarregado = ac
                        atualizarCardDashboard(ac)
                    }
                }
            } catch (e: Exception) {
                // Mantém estado amigável
            }
        }
    }

    private fun atualizarCardDashboard(ac: Acionamento) {
        val txtUltimoBotao = findViewById<TextView>(R.id.txtUltimoBotao)
        val txtHorarioAcionamento = findViewById<TextView>(R.id.txtHorarioAcionamento)

        txtUltimoBotao?.text = "${ac.botao_nome.ifBlank { "Botão" }} (${ac.categoria_nome.ifBlank { "C.A.S.A." }})"
        val par = formatarDataHora(ac.acionado_em)
        txtHorarioAcionamento?.text = "${par.first} às ${par.second}"
    }

    private fun formatarDataHora(isoString: String): Pair<String, String> {
        return try {
            val formatoIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = formatoIso.parse(isoString) ?: Date()
            val dataStr = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(date)
            val horaStr = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(date)
            Pair(dataStr, horaStr)
        } catch (e: Exception) {
            val agora = Date()
            Pair(
                SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(agora),
                SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(agora)
            )
        }
    }

    private fun irParaInicio() {
        val intent = Intent(this, BemVindoActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
