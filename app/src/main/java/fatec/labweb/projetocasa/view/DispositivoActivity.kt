package fatec.labweb.projetocasa.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.DadosDispositivo
import fatec.labweb.projetocasa.service.DispositivoService
import fatec.labweb.projetocasa.util.SoundManager
import kotlinx.coroutines.launch

class DispositivoActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int = R.layout.activity_dispositivo
    override fun getCurrentMenuItemId(): Int = R.id.menuDispositivo

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val dispositivoService = DispositivoService()

    private lateinit var btnAbrirPareamento: MaterialButton
    private lateinit var progressBarDispositivos: ProgressBar
    private lateinit var layoutSemDispositivos: View
    private lateinit var containerDispositivos: LinearLayout

    private var listenerDispositivos: ListenerRegistration? = null
    private val listaDispositivos = mutableListOf<DadosDispositivo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initViews()
        setupListeners()
        ouvirDispositivosEmTempoReal()
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerDispositivos?.remove()
    }

    private fun initViews() {
        btnAbrirPareamento = findViewById(R.id.btnAbrirPareamento)
        progressBarDispositivos = findViewById(R.id.progressBarDispositivos)
        layoutSemDispositivos = findViewById(R.id.layoutSemDispositivos)
        containerDispositivos = findViewById(R.id.containerDispositivos)
    }

    private fun setupListeners() {
        btnAbrirPareamento.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            exibirModalPareamento()
        }
    }

    private fun ouvirDispositivosEmTempoReal() {
        val uid = auth.currentUser?.uid ?: return
        progressBarDispositivos.visibility = View.VISIBLE

        listenerDispositivos = firestore.collection("dispositivos")
            .whereEqualTo("owner_uid", uid)
            .addSnapshotListener { snapshot, error ->
                progressBarDispositivos.visibility = View.GONE
                if (error != null) {
                    Toast.makeText(this, "Erro ao carregar dispositivos: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                listaDispositivos.clear()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        val disp = doc.toObject(DadosDispositivo::class.java)
                        if (disp != null) {
                            listaDispositivos.add(disp)
                        }
                    }
                }

                renderizarDispositivos()
            }
    }

    private fun renderizarDispositivos() {
        containerDispositivos.removeAllViews()

        if (listaDispositivos.isEmpty()) {
            layoutSemDispositivos.visibility = View.VISIBLE
            return
        }

        layoutSemDispositivos.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        for (disp in listaDispositivos) {
            val cardView = inflater.inflate(R.layout.item_dispositivo_card, containerDispositivos, false)

            val txtNome = cardView.findViewById<TextView>(R.id.txtNomeDispositivo)
            val txtMac = cardView.findViewById<TextView>(R.id.txtMacDispositivo)
            val dotStatus = cardView.findViewById<View>(R.id.dotStatus)
            val txtStatus = cardView.findViewById<TextView>(R.id.txtStatusDispositivo)
            val txtWifiSinal = cardView.findViewById<TextView>(R.id.txtWifiSinal)
            val txtIp = cardView.findViewById<TextView>(R.id.txtIp)
            val txtBateria = cardView.findViewById<TextView>(R.id.txtBateria)
            val txtFirmware = cardView.findViewById<TextView>(R.id.txtFirmware)

            val txtVolumeValor = cardView.findViewById<TextView>(R.id.txtVolumeValor)
            val sliderVolume = cardView.findViewById<Slider>(R.id.sliderVolume)
            val btnSalvarVolume = cardView.findViewById<MaterialButton>(R.id.btnSalvarVolume)
            val btnSincronizar = cardView.findViewById<MaterialButton>(R.id.btnSincronizar)
            val btnDesvincular = cardView.findViewById<MaterialButton>(R.id.btnDesvincular)

            // Dados Principais
            txtNome.text = disp.nome.ifBlank { "ESP32 - C.A.S.A." }
            txtMac.text = "MAC: ${disp.mac}"

            // Status Online / Offline
            val isOnline = disp.statusOnline || disp.ativo
            if (isOnline) {
                dotStatus.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_green)
                txtStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                txtStatus.text = "Online"
            } else {
                dotStatus.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_muted)
                txtStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
                txtStatus.text = "Offline"
            }

            // Wi-Fi e IP
            val ssid = disp.ssid ?: "Não conectada"
            val sinal = disp.sinal_wifi?.let { "$it%" } ?: "OK"
            txtWifiSinal.text = "$ssid ($sinal)"
            txtIp.text = disp.ip ?: "Obtendo..."

            // Bateria e Firmware
            val bat = disp.bateriaEfetiva
            txtBateria.text = "$bat% (Alimentado)"
            val fw = disp.firmware ?: "v8"
            txtFirmware.text = "$fw (Prancha v${disp.config_versao})"

            // Volume
            val volAtual = (disp.volume ?: 70).coerceIn(0, 100)
            sliderVolume.value = volAtual.toFloat()
            txtVolumeValor.text = "$volAtual%"

            sliderVolume.addOnChangeListener { _, value, _ ->
                txtVolumeValor.text = "${value.toInt()}%"
            }

            btnSalvarVolume.setOnClickListener { v ->
                SoundManager.dispararAudioCue(v)
                val novoVol = sliderVolume.value.toInt()
                lifecycleScope.launch {
                    val resultado = dispositivoService.atualizarDispositivo(disp.mac, disp.nome, novoVol)
                    if (resultado.isSuccess) {
                        Toast.makeText(this@DispositivoActivity, "Volume do hardware salvo: $novoVol%", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@DispositivoActivity, "Falha ao salvar volume", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            btnSincronizar.setOnClickListener { v ->
                SoundManager.dispararAudioCue(v)
                val uid = auth.currentUser?.uid ?: return@setOnClickListener
                lifecycleScope.launch {
                    val res = dispositivoService.sincronizarDispositivos(uid)
                    if (res.isSuccess) {
                        Toast.makeText(this@DispositivoActivity, "Sincronização enviada ao hardware (config_versao atualizado).", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@DispositivoActivity, "Erro ao sincronizar hardware.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            btnDesvincular.setOnClickListener { v ->
                SoundManager.dispararAudioCue(v)
                confirmarDesvinculo(disp)
            }

            containerDispositivos.addView(cardView)
        }
    }

    private fun confirmarDesvinculo(disp: DadosDispositivo) {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setTitle("Desvincular Dispositivo")
            .setMessage("Deseja realmente desconectar o aparelho '${disp.nome}' (${disp.mac}) da sua conta? Ele não responderá mais às sincronizações desta prancha.")
            .setPositiveButton("Sim, Desvincular") { dialog, _ ->
                dialog.dismiss()
                lifecycleScope.launch {
                    val res = dispositivoService.removerDispositivo(disp.mac, uid)
                    if (res.isSuccess) {
                        Toast.makeText(this@DispositivoActivity, "Dispositivo desvinculado com sucesso.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@DispositivoActivity, "Falha ao desvincular aparelho.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun exibirModalPareamento() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_parear_dispositivo, null)
        val etCodigo = dialogView.findViewById<TextInputEditText>(R.id.etCodigoPareamento)
        val etNome = dialogView.findViewById<TextInputEditText>(R.id.etNomeAparelho)
        val txtStatus = dialogView.findViewById<TextView>(R.id.txtStatusPareamento)
        val progress = dialogView.findViewById<ProgressBar>(R.id.progressPareando)
        val btnConfirmar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarPareamento)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarPareamento)
        val btnFechar = dialogView.findViewById<ImageView>(R.id.btnFecharModalPareamento)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnFechar.setOnClickListener { dialog.dismiss() }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnConfirmar.setOnClickListener {
            val codigo = etCodigo.text?.toString()?.trim() ?: ""
            val nome = etNome.text?.toString()?.trim() ?: "ESP32 - C.A.S.A."
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            if (codigo.length != 6) {
                txtStatus.visibility = View.VISIBLE
                txtStatus.text = "Digite os 6 dígitos numéricos exibidos no OLED."
                return@setOnClickListener
            }

            txtStatus.visibility = View.GONE
            progress.visibility = View.VISIBLE
            btnConfirmar.isEnabled = false

            lifecycleScope.launch {
                val resultado = dispositivoService.parearPorCodigo(codigo, nome, uid)
                progress.visibility = View.GONE
                btnConfirmar.isEnabled = true

                if (resultado.isSuccess) {
                    dialog.dismiss()
                    Toast.makeText(this@DispositivoActivity, "Dispositivo '$nome' conectado com sucesso!", Toast.LENGTH_LONG).show()
                } else {
                    txtStatus.visibility = View.VISIBLE
                    txtStatus.text = resultado.exceptionOrNull()?.message ?: "Falha ao parear dispositivo."
                }
            }
        }

        dialog.show()
    }
}
