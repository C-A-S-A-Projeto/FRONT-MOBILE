package fatec.labweb.projetocasa.view

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.adapter.NotificacoesAdapter
import fatec.labweb.projetocasa.model.DadosDispositivo
import fatec.labweb.projetocasa.model.Notificacao
import fatec.labweb.projetocasa.util.SoundManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * NotificacoesActivity: Tela de Notificações em Tempo Real com SnapshotListeners do Firestore.
 * Escuta continuamente /usuarios/{uid}/notificacoes, /dispositivos e /usuarios/{uid}/acionamentos,
 * exibindo alertas visuais claros para status do dispositivo, bateria e novas interações.
 */
class NotificacoesActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int = R.layout.activity_notifications
    override fun getCurrentMenuItemId(): Int = -1

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private lateinit var rvNotificacoes: RecyclerView
    private lateinit var adapter: NotificacoesAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutSemNotificacoes: View
    private lateinit var btnFechar: MaterialButton
    private lateinit var btnLimpar: MaterialButton

    // Views do Card de Status do Dispositivo
    private lateinit var cardStatusDispositivo: MaterialCardView
    private lateinit var dotStatusDispositivo: View
    private lateinit var txtStatusDispositivoTitulo: TextView
    private lateinit var txtStatusDispositivoDetalhe: TextView
    private lateinit var imgIconeBateria: ImageView

    // Registros de Listeners para limpeza de memória
    private var listenerNotificacoes: ListenerRegistration? = null
    private var listenerDispositivos: ListenerRegistration? = null
    private var listenerAcionamentos: ListenerRegistration? = null

    // Cache local de listas em tempo real
    private var notificacoesFirestore = mutableListOf<Notificacao>()
    private var notificacoesAcionamentos = mutableListOf<Notificacao>()
    private var notificacoesDispositivo = mutableListOf<Notificacao>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initViews()
        setupRecyclerView()
        setupListeners()
        iniciarObservadoresEmTempoReal()
    }

    override fun onDestroy() {
        super.onDestroy()
        removerListeners()
    }

    private fun initViews() {
        rvNotificacoes = findViewById(R.id.rvNotificacoes)
        progressBar = findViewById(R.id.progressBarNotificacoes)
        layoutSemNotificacoes = findViewById(R.id.layoutSemNotificacoes)
        btnFechar = findViewById(R.id.btnFecharNotificacoes)
        btnLimpar = findViewById(R.id.btnLimparNotificacoes)

        cardStatusDispositivo = findViewById(R.id.cardStatusDispositivo)
        dotStatusDispositivo = findViewById(R.id.dotStatusDispositivo)
        txtStatusDispositivoTitulo = findViewById(R.id.txtStatusDispositivoTitulo)
        txtStatusDispositivoDetalhe = findViewById(R.id.txtStatusDispositivoDetalhe)
        imgIconeBateria = findViewById(R.id.imgIconeBateria)
    }

    private fun setupRecyclerView() {
        adapter = NotificacoesAdapter(
            itens = emptyList(),
            onItemClick = { notif ->
                marcarNotificacaoComoLida(notif)
            },
            onExcluirClick = { notif ->
                excluirNotificacao(notif)
            }
        )
        rvNotificacoes.layoutManager = LinearLayoutManager(this)
        rvNotificacoes.adapter = adapter
    }

    private fun setupListeners() {
        btnFechar.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            finish()
        }

        btnLimpar.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            limparTodasNotificacoes()
        }
    }

    /**
     * Inicia ouvintes em tempo real (addSnapshotListener) para a coleção de notificações,
     * status do hardware conectado e acionamentos de botões.
     */
    private fun iniciarObservadoresEmTempoReal() {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            carregarNotificacoesDemonstracao()
            return
        }

        progressBar.visibility = View.VISIBLE

        // 1. Escuta em Tempo Real: /usuarios/{uid}/notificacoes
        listenerNotificacoes = db.collection("usuarios").document(userId)
            .collection("notificacoes")
            .addSnapshotListener { snapshots, error ->
                progressBar.visibility = View.GONE
                if (error != null) {
                    return@addSnapshotListener
                }

                val lista = mutableListOf<Notificacao>()
                if (snapshots != null) {
                    for (doc in snapshots) {
                        try {
                            val notif = doc.toObject(Notificacao::class.java).apply {
                                id = doc.id
                            }
                            lista.add(notif)
                        } catch (_: Exception) {}
                    }
                }
                notificacoesFirestore = lista
                consolidarEAtualizarLista()
            }

        // 2. Escuta em Tempo Real: /dispositivos (onde owner_uid == userId)
        listenerDispositivos = db.collection("dispositivos")
            .whereEqualTo("owner_uid", userId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                val novosAlertasDispositivo = mutableListOf<Notificacao>()
                if (!snapshots.isEmpty) {
                    val doc = snapshots.documents[0]
                    val disp = doc.toObject(DadosDispositivo::class.java)
                    if (disp != null) {
                        atualizarCardDispositivo(disp)

                        // Alerta visual de dispositivo online / conectado
                        val statusTxt = if (disp.statusOnline || disp.ativo) "conectado" else "desconectado"
                        val nivelBateria = disp.nivelBateria.coerceIn(0, 100)

                        novosAlertasDispositivo.add(
                            Notificacao(
                                id = "disp_${disp.id.ifBlank { disp.mac }}",
                                titulo = "Dispositivo ${disp.nome.ifBlank { "C.A.S.A." }} $statusTxt",
                                mensagem = "Status: ${if (disp.statusOnline || disp.ativo) "Operacional (Online)" else "Aguardando Conexão"} • Bateria: $nivelBateria%",
                                data = if (disp.ultimoClique > 0L) disp.ultimoClique else System.currentTimeMillis(),
                                tipo = if (nivelBateria <= 20) "bateria" else "dispositivo",
                                dispositivoId = disp.mac,
                                nivelBateria = nivelBateria,
                                statusOnline = disp.statusOnline || disp.ativo
                            )
                        )
                    }
                } else {
                    cardStatusDispositivo.visibility = View.GONE
                }
                notificacoesDispositivo = novosAlertasDispositivo
                consolidarEAtualizarLista()
            }

        // 3. Escuta em Tempo Real: /usuarios/{uid}/acionamentos (interações em tempo real)
        listenerAcionamentos = db.collection("usuarios").document(userId)
            .collection("acionamentos")
            .orderBy("acionado_em", Query.Direction.DESCENDING)
            .limit(15)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                val novosAcionamentos = mutableListOf<Notificacao>()
                for (doc in snapshots) {
                    val botaoNome = doc.getString("botao_nome") ?: "Botão"
                    val categoriaNome = doc.getString("categoria_nome") ?: "Geral"
                    val acionadoEm = doc.getString("acionado_em") ?: ""
                    val origem = doc.getString("origem") ?: "dispositivo"
                    val dispMac = doc.getString("dispositivo") ?: ""

                    val dataMillis = parseIsoParaMillis(acionadoEm)

                    novosAcionamentos.add(
                        Notificacao(
                            id = "acionamento_${doc.id}",
                            titulo = "Novo acionamento: $botaoNome",
                            mensagem = "Categoria: $categoriaNome • Origem: ${if (origem == "app") "App" else "Dispositivo C.A.S.A."}",
                            data = dataMillis,
                            tipo = "interacao",
                            dispositivoId = dispMac
                        )
                    )
                }
                notificacoesAcionamentos = novosAcionamentos
                consolidarEAtualizarLista()
            }
    }

    /**
     * Mescla e consolida as notificações recebidas em tempo real das três fontes,
     * ordenando da mais recente para a mais antiga.
     */
    private fun consolidarEAtualizarLista() {
        val mapaConsolidado = LinkedHashMap<String, Notificacao>()

        // 1. Notificações do Firestore
        for (item in notificacoesFirestore) {
            mapaConsolidado[item.id] = item
        }

        // 2. Alertas de Dispositivo
        for (item in notificacoesDispositivo) {
            mapaConsolidado[item.id] = item
        }

        // 3. Interações em tempo real (Acionamentos)
        for (item in notificacoesAcionamentos) {
            mapaConsolidado[item.id] = item
        }

        val listaFinal = mapaConsolidado.values.sortedByDescending { it.data }

        adapter.atualizarLista(listaFinal)

        val vazia = listaFinal.isEmpty()
        layoutSemNotificacoes.visibility = if (vazia) View.VISIBLE else View.GONE
        rvNotificacoes.visibility = if (vazia) View.GONE else View.VISIBLE
        btnLimpar.visibility = if (vazia) View.GONE else View.VISIBLE
    }

    private fun atualizarCardDispositivo(disp: DadosDispositivo) {
        cardStatusDispositivo.visibility = View.VISIBLE
        val isOnline = disp.statusOnline || disp.ativo
        val bateria = disp.nivelBateria.coerceIn(0, 100)

        if (isOnline) {
            txtStatusDispositivoTitulo.text = "Dispositivo Conectado"
            dotStatusDispositivo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            cardStatusDispositivo.setCardBackgroundColor(Color.parseColor("#1E3A8A"))
        } else {
            txtStatusDispositivoTitulo.text = "Dispositivo Desconectado"
            dotStatusDispositivo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            cardStatusDispositivo.setCardBackgroundColor(Color.parseColor("#374151"))
        }

        val nomeDisp = disp.nome.ifBlank { "ESP32 C.A.S.A." }
        txtStatusDispositivoDetalhe.text = "$nomeDisp • Bateria: $bateria% • ${if (isOnline) "Online" else "Offline"}"

        val corBateria = when {
            bateria > 50 -> Color.parseColor("#10B981")
            bateria > 20 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        imgIconeBateria.imageTintList = ColorStateList.valueOf(corBateria)
    }

    private fun marcarNotificacaoComoLida(notificacao: Notificacao) {
        val userId = auth.currentUser?.uid ?: return
        if (notificacao.id.startsWith("acionamento_") || notificacao.id.startsWith("disp_")) return

        db.collection("usuarios").document(userId)
            .collection("notificacoes").document(notificacao.id)
            .update("lida", true)
            .addOnFailureListener { /* Regras Firestore v2 */ }
    }

    private fun excluirNotificacao(notificacao: Notificacao) {
        adapter.removerNotificacao(notificacao.id)
        val userId = auth.currentUser?.uid ?: return

        if (!notificacao.id.startsWith("acionamento_") && !notificacao.id.startsWith("disp_")) {
            db.collection("usuarios").document(userId)
                .collection("notificacoes").document(notificacao.id)
                .delete()
                .addOnFailureListener { /* Regras Firestore v2 */ }
        }
        Toast.makeText(this, "Notificação removida", Toast.LENGTH_SHORT).show()
    }

    private fun limparTodasNotificacoes() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            for (notif in notificacoesFirestore) {
                db.collection("usuarios").document(userId)
                    .collection("notificacoes").document(notif.id)
                    .delete()
            }
        }
        notificacoesFirestore.clear()
        notificacoesAcionamentos.clear()
        consolidarEAtualizarLista()
        Toast.makeText(this, "Todas as notificações foram limpas", Toast.LENGTH_SHORT).show()
    }

    private fun removerListeners() {
        listenerNotificacoes?.remove()
        listenerNotificacoes = null
        listenerDispositivos?.remove()
        listenerDispositivos = null
        listenerAcionamentos?.remove()
        listenerAcionamentos = null
    }

    private fun parseIsoParaMillis(isoString: String): Long {
        if (isoString.isBlank()) return System.currentTimeMillis()
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun carregarNotificacoesDemonstracao() {
        progressBar.visibility = View.GONE
        val listaDemo = listOf(
            Notificacao(
                id = "demo_1",
                titulo = "Dispositivo Conectado",
                mensagem = "ESP32 C.A.S.A. pareado e sincronizado com sucesso.",
                data = System.currentTimeMillis() - 60_000,
                lida = false,
                tipo = "dispositivo",
                nivelBateria = 95
            ),
            Notificacao(
                id = "demo_2",
                titulo = "Novo acionamento: Água",
                mensagem = "Categoria: Geral • Origem: Dispositivo C.A.S.A.",
                data = System.currentTimeMillis() - 300_000,
                lida = true,
                tipo = "interacao"
            ),
            Notificacao(
                id = "demo_3",
                titulo = "Sincronização de Mídias",
                mensagem = "Imagens e áudios locais sincronizados com o armazenamento interno.",
                data = System.currentTimeMillis() - 3_600_000,
                lida = true,
                tipo = "info"
            )
        )
        adapter.atualizarLista(listaDemo)
        layoutSemNotificacoes.visibility = View.GONE
        rvNotificacoes.visibility = View.VISIBLE
    }
}
