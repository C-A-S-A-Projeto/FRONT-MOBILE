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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fatec.labweb.projetocasa.BemVindoActivity
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.adapter.BleDeviceAdapter
import fatec.labweb.projetocasa.model.Acionamento
import fatec.labweb.projetocasa.model.DadosDispositivo
import fatec.labweb.projetocasa.service.DispositivoService
import fatec.labweb.projetocasa.service.Esp32BleManager
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

    private lateinit var esp32BleManager: Esp32BleManager
    private var onBlePermissionsGranted: (() -> Unit)? = null
    private val requestBlePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (esp32BleManager.temPermissoesNecessarias()) {
            onBlePermissionsGranted?.invoke()
        } else {
            Toast.makeText(this, "Permissões de Bluetooth necessárias para localizar o ESP32.", Toast.LENGTH_LONG).show()
        }
    }

    override fun getContentLayoutId(): Int {
        return R.layout.activity_home
    }

    override fun getCurrentMenuItemId(): Int {
        return R.id.menuInicio
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        esp32BleManager = Esp32BleManager(this)

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

    override fun onDestroy() {
        super.onDestroy()
        if (::esp32BleManager.isInitialized) {
            esp32BleManager.pararScan()
            esp32BleManager.desconectar()
        }
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
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvModalPareamentoTitulo)
        val btnFechar = dialogView.findViewById<ImageView>(R.id.btnFecharModalPareamento)

        // Containers de Etapas
        val containerEtapa1 = dialogView.findViewById<View>(R.id.containerEtapa1Scan)
        val containerEtapa2 = dialogView.findViewById<View>(R.id.containerEtapa2Credenciais)
        val containerEtapa3 = dialogView.findViewById<View>(R.id.containerEtapa3Aguardando)
        val containerManual = dialogView.findViewById<View>(R.id.containerModoManualCodigo)

        // Elementos Etapa 1
        val rvBle = dialogView.findViewById<RecyclerView>(R.id.rvDispositivosBle)
        val progressScanBle = dialogView.findViewById<View>(R.id.progressScanBle)
        val txtStatusScanBle = dialogView.findViewById<TextView>(R.id.txtStatusScanBle)
        val txtNenhumDispositivoBle = dialogView.findViewById<TextView>(R.id.txtNenhumDispositivoBle)
        val btnBuscarBleNovamente = dialogView.findViewById<MaterialButton>(R.id.btnBuscarBleNovamente)
        val btnOpcaoCodigoManual = dialogView.findViewById<MaterialButton>(R.id.btnOpcaoCodigoManual)

        // Elementos Etapa 2
        val txtDispositivoConectadoNome = dialogView.findViewById<TextView>(R.id.txtDispositivoConectadoNome)
        val txtDispositivoConectadoMac = dialogView.findViewById<TextView>(R.id.txtDispositivoConectadoMac)
        val etSsidWifi = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSsidWifi)
        val etSenhaWifi = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSenhaWifi)
        val etTokenOled = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTokenOled)
        val etNomeAparelhoEtapa2 = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNomeAparelhoEtapa2)
        val txtStatusEtapa2 = dialogView.findViewById<TextView>(R.id.txtStatusEtapa2)
        val btnVoltarParaScan = dialogView.findViewById<MaterialButton>(R.id.btnVoltarParaScan)
        val btnEnviarCredenciaisBle = dialogView.findViewById<MaterialButton>(R.id.btnEnviarCredenciaisBle)

        // Elementos Etapa 3
        val progressAguardandoWifi = dialogView.findViewById<View>(R.id.progressAguardandoWifi)
        val imgSucessoPareamento = dialogView.findViewById<ImageView>(R.id.imgSucessoPareamento)
        val txtEtapa3Titulo = dialogView.findViewById<TextView>(R.id.txtEtapa3Titulo)
        val txtStatusPasso1 = dialogView.findViewById<TextView>(R.id.txtStatusPasso1)
        val txtStatusPasso2 = dialogView.findViewById<TextView>(R.id.txtStatusPasso2)
        val txtStatusPasso3 = dialogView.findViewById<TextView>(R.id.txtStatusPasso3)
        val txtMensagemErroFinal = dialogView.findViewById<TextView>(R.id.txtMensagemErroFinal)
        val btnConcluirModal = dialogView.findViewById<MaterialButton>(R.id.btnConcluirModal)

        // Elementos Manual
        val etCodigoManual = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCodigoPareamentoManual)
        val etNomeManual = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNomeAparelhoManual)
        val txtStatusManual = dialogView.findViewById<TextView>(R.id.txtStatusManual)
        val progressManual = dialogView.findViewById<View>(R.id.progressManual)
        val btnVoltarParaScanDeManual = dialogView.findViewById<MaterialButton>(R.id.btnVoltarParaScanDeManual)
        val btnConfirmarManual = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarManual)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        var macSelecionado = ""
        var nomeSelecionado = ""

        fun mudarEtapa(etapa: Int) {
            containerEtapa1.visibility = if (etapa == 1) View.VISIBLE else View.GONE
            containerEtapa2.visibility = if (etapa == 2) View.VISIBLE else View.GONE
            containerEtapa3.visibility = if (etapa == 3) View.VISIBLE else View.GONE
            containerManual.visibility = if (etapa == 4) View.VISIBLE else View.GONE

            when (etapa) {
                1 -> tvTitulo.text = "Localizar ESP32"
                2 -> tvTitulo.text = "Configuração Wi-Fi"
                3 -> tvTitulo.text = "Conectando ao C.A.S.A."
                4 -> tvTitulo.text = "Pareamento Manual"
            }
        }

        val adapterBle = BleDeviceAdapter { dispositivoBle ->
            SoundManager.dispararAudioCue(dialogView)
            esp32BleManager.pararScan()
            progressScanBle.visibility = View.VISIBLE
            txtStatusScanBle.text = "Conectando ao ${dispositivoBle.nome}..."

            esp32BleManager.conectarDispositivo(
                dispositivoBle.device,
                onConectadoComSucesso = { macLido ->
                    runOnUiThread {
                        macSelecionado = macLido
                        nomeSelecionado = dispositivoBle.nome
                        txtDispositivoConectadoNome.text = dispositivoBle.nome
                        txtDispositivoConectadoMac.text = "MAC: $macLido"
                        progressScanBle.visibility = View.GONE
                        txtStatusEtapa2.visibility = View.GONE
                        mudarEtapa(2)
                    }
                },
                onError = { erroGatt ->
                    runOnUiThread {
                        progressScanBle.visibility = View.GONE
                        Toast.makeText(this@HomeActivity, "Erro ao conectar BLE: $erroGatt", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        rvBle.layoutManager = LinearLayoutManager(this)
        rvBle.adapter = adapterBle

        fun iniciarVarreduraBle() {
            if (!esp32BleManager.temPermissoesNecessarias()) {
                onBlePermissionsGranted = { iniciarVarreduraBle() }
                requestBlePermissionsLauncher.launch(esp32BleManager.obterPermissoesObrigatorias())
                return
            }

            adapterBle.limpar()
            val pareados = esp32BleManager.obterDispositivosPareadosDoSistema()
            for (p in pareados) {
                adapterBle.adicionarOuAtualizar(p)
            }
            txtNenhumDispositivoBle.visibility = if (adapterBle.itemCount == 0) View.VISIBLE else View.GONE

            progressScanBle.visibility = View.VISIBLE
            txtStatusScanBle.text = "Buscando placas CASA via BLE..."

            esp32BleManager.iniciarScan(
                timeoutMs = 15000,
                onDispositivoEncontrado = { dev ->
                    runOnUiThread {
                        adapterBle.adicionarOuAtualizar(dev)
                        txtNenhumDispositivoBle.visibility = View.GONE
                    }
                },
                onTimeout = {
                    runOnUiThread {
                        progressScanBle.visibility = View.GONE
                        txtStatusScanBle.text = if (adapterBle.itemCount == 0) "Nenhum dispositivo encontrado." else "Busca finalizada."
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        progressScanBle.visibility = View.GONE
                        txtStatusScanBle.text = err
                    }
                }
            )
        }

        btnFechar.setOnClickListener {
            esp32BleManager.pararScan()
            esp32BleManager.desconectar()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            esp32BleManager.pararScan()
            esp32BleManager.desconectar()
        }

        btnBuscarBleNovamente.setOnClickListener {
            SoundManager.dispararAudioCue(it)
            iniciarVarreduraBle()
        }

        btnOpcaoCodigoManual.setOnClickListener {
            SoundManager.dispararAudioCue(it)
            esp32BleManager.pararScan()
            mudarEtapa(4)
        }

        btnVoltarParaScan.setOnClickListener {
            SoundManager.dispararAudioCue(it)
            esp32BleManager.desconectar()
            mudarEtapa(1)
            iniciarVarreduraBle()
        }

        btnVoltarParaScanDeManual.setOnClickListener {
            SoundManager.dispararAudioCue(it)
            mudarEtapa(1)
            iniciarVarreduraBle()
        }

        btnEnviarCredenciaisBle.setOnClickListener {
            val ssid = etSsidWifi.text?.toString()?.trim() ?: ""
            val senha = etSenhaWifi.text?.toString() ?: ""
            val pin = etTokenOled.text?.toString()?.trim() ?: ""
            val nomePersonalizado = etNomeAparelhoEtapa2.text?.toString()?.trim()?.ifBlank { nomeSelecionado } ?: "ESP32 - C.A.S.A."
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            if (ssid.isBlank()) {
                txtStatusEtapa2.visibility = View.VISIBLE
                txtStatusEtapa2.text = "Informe o nome da rede Wi-Fi (SSID)."
                return@setOnClickListener
            }

            if (pin.length != 6) {
                txtStatusEtapa2.visibility = View.VISIBLE
                txtStatusEtapa2.text = "O PIN do visor OLED deve ter exatamente 6 dígitos."
                return@setOnClickListener
            }

            txtStatusEtapa2.visibility = View.VISIBLE
            txtStatusEtapa2.text = "Enviando credenciais para o hardware..."
            btnEnviarCredenciaisBle.isEnabled = false

            lifecycleScope.launch {
                esp32BleManager.enviarCredenciaisWifi(
                    pin = pin,
                    ssid = ssid,
                    pass = senha,
                    onSucesso = {
                        runOnUiThread {
                            esp32BleManager.desconectar()
                            mudarEtapa(3)

                            progressAguardandoWifi.visibility = View.VISIBLE
                            imgSucessoPareamento.visibility = View.GONE
                            txtMensagemErroFinal.visibility = View.GONE
                            txtStatusPasso1.text = "✓ Credenciais enviadas com sucesso ao ESP32"
                            txtStatusPasso2.text = "• Aguardando conexão Wi-Fi do hardware..."
                            txtStatusPasso3.text = "• Vinculando à sua conta C.A.S.A..."
                            btnConcluirModal.isEnabled = false

                            lifecycleScope.launch {
                                val resAguardar = dispositivoService.aguardarEFinalizarPareamento(
                                    pin = pin,
                                    macConhecido = macSelecionado,
                                    nomeAparelho = nomePersonalizado,
                                    userId = uid,
                                    onStatusUpdate = { statusMsg ->
                                        runOnUiThread { txtStatusPasso2.text = "• $statusMsg" }
                                    }
                                )

                                progressAguardandoWifi.visibility = View.GONE
                                btnConcluirModal.isEnabled = true

                                if (resAguardar.isSuccess) {
                                    imgSucessoPareamento.visibility = View.VISIBLE
                                    txtEtapa3Titulo.text = "Dispositivo Conectado!"
                                    txtStatusPasso2.text = "✓ ESP32 conectado ao Wi-Fi e à nuvem"
                                    txtStatusPasso3.text = "✓ Hardware vinculado com sucesso à sua conta!"
                                    btnConcluirModal.text = "Concluir"
                                    carregarStatusDispositivoPrincipal(uid)
                                } else {
                                    txtMensagemErroFinal.visibility = View.VISIBLE
                                    txtMensagemErroFinal.text = resAguardar.exceptionOrNull()?.message ?: "Falha ao vincular dispositivo."
                                    btnConcluirModal.text = "Fechar"
                                }
                            }
                        }
                    },
                    onError = { errEnvio ->
                        runOnUiThread {
                            btnEnviarCredenciaisBle.isEnabled = true
                            txtStatusEtapa2.visibility = View.VISIBLE
                            txtStatusEtapa2.text = "Erro no envio BLE: $errEnvio"
                        }
                    }
                )
            }
        }

        btnConcluirModal.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmarManual.setOnClickListener {
            val codigo = etCodigoManual.text?.toString()?.trim() ?: ""
            val nome = etNomeManual.text?.toString()?.trim()?.ifBlank { "ESP32 - C.A.S.A." } ?: "ESP32 - C.A.S.A."
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            if (codigo.length != 6) {
                txtStatusManual.visibility = View.VISIBLE
                txtStatusManual.text = "Digite os 6 dígitos numéricos exibidos no OLED."
                return@setOnClickListener
            }

            txtStatusManual.visibility = View.GONE
            progressManual.visibility = View.VISIBLE
            btnConfirmarManual.isEnabled = false

            lifecycleScope.launch {
                val resultado = dispositivoService.parearPorCodigo(codigo, nome, uid)
                progressManual.visibility = View.GONE
                btnConfirmarManual.isEnabled = true

                if (resultado.isSuccess) {
                    dialog.dismiss()
                    Toast.makeText(this@HomeActivity, "Dispositivo '$nome' pareado com sucesso!", Toast.LENGTH_LONG).show()
                    carregarStatusDispositivoPrincipal(uid)
                } else {
                    txtStatusManual.visibility = View.VISIBLE
                    txtStatusManual.text = resultado.exceptionOrNull()?.message ?: "Falha ao parear dispositivo."
                }
            }
        }

        mudarEtapa(1)
        dialog.show()
        iniciarVarreduraBle()
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
