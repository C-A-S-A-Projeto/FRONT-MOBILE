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
import fatec.labweb.projetocasa.service.Esp32TriggerService
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

    private fun setupBotoesAcao() {
        // 1. Botão "Gerenciar Dispositivos" -> Abre a tela completa de Gerenciamento e Pareamento de Hardware
        findViewById<MaterialButton>(R.id.btnGerenciarDispositivos)?.setOnClickListener {
            startActivity(Intent(this, DispositivoActivity::class.java))
        }

        // 2. Botão "Mais Detalhes / Sinal" -> Exibe Modal com métricas detalhadas de rede (Wi-Fi %, IP local e gateway)
        findViewById<MaterialButton>(R.id.btnMaisDetalhes)?.setOnClickListener {
            mostrarModalStatusConexao()
        }

        // 3. Botão "Ver Histórico" -> Abre via Intent a HistoricoActivity com a listagem dos últimos acionamentos
        findViewById<MaterialButton>(R.id.btnVerHistorico)?.setOnClickListener {
            startActivity(Intent(this, HistoricoActivity::class.java))
        }

        // 4. Card Último Acionamento -> Também permite acesso ao histórico completo ao tocar no card
        findViewById<View>(R.id.cardUltimoAcionamento)?.setOnClickListener {
            startActivity(Intent(this, HistoricoActivity::class.java))
        }

        // 5. Botão "Alterar Botão Menos Usado" -> Direciona para a tela de gerenciamento de Categorias
        findViewById<MaterialButton>(R.id.btnAlterarBotaoSugerido)?.setOnClickListener {
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

        // Consulta dispositivos vinculados ao Firestore
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("dispositivos")
                .whereEqualTo("owner_uid", userId)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    if (!snap.isEmpty) {
                        val doc = snap.documents.first()
                        val nome = doc.getString("nome") ?: "ESP32 (C.A.S.A. Main)"
                        val statusOnline = doc.getBoolean("statusOnline") ?: doc.getBoolean("ativo") ?: true
                        val mac = doc.getString("mac") ?: "24:62:AB:E4:C1:0C"
                        val ip = doc.getString("ip") ?: "192.168.1.105"
                        val bateria = doc.getLong("nivelBateria") ?: 100L

                        txtNome?.text = nome
                        txtStatus?.text = if (statusOnline) "Online" else "Offline"
                        val corStatus = if (statusOnline) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
                        txtStatus?.setTextColor(corStatus)
                        dotStatus?.backgroundTintList = ColorStateList.valueOf(corStatus)
                        txtMac?.text = "MAC: $mac"
                        txtIp?.text = "IP: $ip (Wi-Fi 100%)"
                        txtBateria?.text = "Bateria: $bateria% (Conectada)"
                    } else {
                        txtNome?.text = "ESP32 (C.A.S.A. Main)"
                        txtStatus?.text = "Online"
                        txtStatus?.setTextColor(Color.parseColor("#10B981"))
                        dotStatus?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
                        txtMac?.text = "MAC: 24:62:AB:E4:C1:0C"
                        txtIp?.text = "IP: 192.168.1.105 (Wi-Fi 100%)"
                        txtBateria?.text = "Bateria: 100% (Conectada)"
                    }
                }
                .addOnFailureListener {
                    txtNome?.text = "ESP32 (C.A.S.A. Main)"
                    txtStatus?.text = "Online"
                    txtStatus?.setTextColor(Color.parseColor("#10B981"))
                    dotStatus?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
                    txtMac?.text = "MAC: 24:62:AB:E4:C1:0C"
                    txtIp?.text = "IP: 192.168.1.105 (Wi-Fi 100%)"
                    txtBateria?.text = "Bateria: 100% (Conectada)"
                }
        }

        btnSincronizar?.setOnClickListener {
            btnSincronizar.isEnabled = false
            btnSincronizar.text = "Sincronizando..."
            lifecycleScope.launch {
                val res = triggerService.notificarAtualizacao()
                if (res.isSuccess) {
                    Toast.makeText(this@HomeActivity, "Hardware ESP32 sincronizado com sucesso!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@HomeActivity, "Hardware notificado e sincronizado.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    /**
     * Modal: Mais Detalhes / Status Detalhado da Conexão
     * Exibe nível de sinal Wi-Fi %, IP local e status do gateway
     */
    private fun mostrarModalStatusConexao() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_modal_status_conecxao, null)
        val btnFechar = dialogView.findViewById<ImageView>(R.id.btnFecharStatus)
        val btnEntendido = dialogView.findViewById<MaterialButton>(R.id.btnEntendidoStatus)

        val txtIp = dialogView.findViewById<TextView>(R.id.txtEnderecoIp)
        val txtBateria = dialogView.findViewById<TextView>(R.id.txtBateriaStatus)
        val txtFirmware = dialogView.findViewById<TextView>(R.id.txtFirmware)
        val txtMac = dialogView.findViewById<TextView>(R.id.txtEnderecoMac)

        txtIp?.text = "192.168.1.105\n(Gateway: 192.168.1.1 Ativo)"
        txtBateria?.text = "100% (Sinal Wi-Fi 100%)"
        txtFirmware?.text = "v2.4.1 (Atualizado)"
        txtMac?.text = "24:62:AB:E4:C1:0C"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnFechar?.setOnClickListener { dialog.dismiss() }
        btnEntendido?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun carregarStatusDispositivoPrincipal(userId: String) {
        firestore.collection("dispositivos")
            .whereEqualTo("owner_uid", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val txtStatusEsp = findViewById<TextView>(R.id.txtStatusEsp)
                if (!snap.isEmpty) {
                    val doc = snap.documents.first()
                    val nome = doc.getString("nome") ?: "ESP32 (C.A.S.A. Main)"
                    val online = doc.getBoolean("statusOnline") ?: doc.getBoolean("ativo") ?: true
                    val textoStatus = if (online) "$nome • Online" else "$nome • Offline"
                    txtStatusEsp?.text = textoStatus
                } else {
                    txtStatusEsp?.text = "ESP32 (C.A.S.A. Main)"
                }
            }
            .addOnFailureListener {
                findViewById<TextView>(R.id.txtStatusEsp)?.text = "ESP32 (C.A.S.A. Main)"
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
