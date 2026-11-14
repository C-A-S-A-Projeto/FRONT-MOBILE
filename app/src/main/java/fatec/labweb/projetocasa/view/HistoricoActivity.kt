package fatec.labweb.projetocasa.view

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.adapter.HistoricoAdapter
import fatec.labweb.projetocasa.model.Acionamento
import fatec.labweb.projetocasa.service.HistoricoService
import fatec.labweb.projetocasa.util.SoundManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistoricoActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int = R.layout.activity_historico
    override fun getCurrentMenuItemId(): Int = R.id.menuHistorico

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val historicoService = HistoricoService()

    private lateinit var rvHistorico: RecyclerView
    private lateinit var adapter: HistoricoAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutSemHistorico: View
    private lateinit var etBuscarHistorico: TextInputEditText
    private lateinit var btnLimparTodoHistorico: MaterialButton

    private lateinit var btnFiltroTodos: MaterialButton
    private lateinit var btnFiltroHoje: MaterialButton
    private lateinit var btnFiltroSemana: MaterialButton
    private lateinit var btnFiltroMes: MaterialButton

    private var todosAcionamentos = listOf<Acionamento>()
    private var periodoSelecionado = PeriodoFiltro.TODOS
    private var termoBuscaAtual = ""

    enum class PeriodoFiltro {
        TODOS, HOJE, SEMANA, MES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initViews()
        setupRecyclerView()
        setupListeners()
        carregarHistoricoReal()
    }

    private fun initViews() {
        rvHistorico = findViewById(R.id.rvHistorico)
        progressBar = findViewById(R.id.progressBarHistorico)
        layoutSemHistorico = findViewById(R.id.layoutSemHistorico)
        etBuscarHistorico = findViewById(R.id.etBuscarHistorico)
        btnLimparTodoHistorico = findViewById(R.id.btnLimparTodoHistorico)

        btnFiltroTodos = findViewById(R.id.btnFiltroTodos)
        btnFiltroHoje = findViewById(R.id.btnFiltroHoje)
        btnFiltroSemana = findViewById(R.id.btnFiltroSemana)
        btnFiltroMes = findViewById(R.id.btnFiltroMes)
    }

    private fun setupRecyclerView() {
        adapter = HistoricoAdapter(
            itens = emptyList(),
            onDeleteClick = { acionamento ->
                confirmarExclusaoItem(acionamento)
            }
        )
        rvHistorico.layoutManager = LinearLayoutManager(this)
        rvHistorico.adapter = adapter
    }

    private fun setupListeners() {
        etBuscarHistorico.doAfterTextChanged { editable ->
            termoBuscaAtual = editable?.toString()?.trim()?.lowercase() ?: ""
            aplicarFiltrosEExibir()
        }

        btnFiltroTodos.setOnClickListener {
            selecionarPeriodo(PeriodoFiltro.TODOS)
        }
        btnFiltroHoje.setOnClickListener {
            selecionarPeriodo(PeriodoFiltro.HOJE)
        }
        btnFiltroSemana.setOnClickListener {
            selecionarPeriodo(PeriodoFiltro.SEMANA)
        }
        btnFiltroMes.setOnClickListener {
            selecionarPeriodo(PeriodoFiltro.MES)
        }

        btnLimparTodoHistorico.setOnClickListener { v ->
            SoundManager.dispararAudioCue(v)
            confirmarLimpezaTotal()
        }
    }

    private fun selecionarPeriodo(periodo: PeriodoFiltro) {
        periodoSelecionado = periodo
        atualizarBotoesFiltro()
        aplicarFiltrosEExibir()
    }

    private fun atualizarBotoesFiltro() {
        val botoes = listOf(
            btnFiltroTodos to PeriodoFiltro.TODOS,
            btnFiltroHoje to PeriodoFiltro.HOJE,
            btnFiltroSemana to PeriodoFiltro.SEMANA,
            btnFiltroMes to PeriodoFiltro.MES
        )

        for ((btn, p) in botoes) {
            if (p == periodoSelecionado) {
                btn.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_primary))
                btn.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                btn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                btn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun carregarHistoricoReal() {
        val uid = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        layoutSemHistorico.visibility = View.GONE

        lifecycleScope.launch {
            try {
                todosAcionamentos = historicoService.listarAcionamentos(uid, limite = 200)
                progressBar.visibility = View.GONE
                aplicarFiltrosEExibir()
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@HistoricoActivity, "Erro ao carregar histórico: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun aplicarFiltrosEExibir() {
        val agora = Calendar.getInstance()

        val filtradosPorPeriodo = todosAcionamentos.filter { item ->
            when (periodoSelecionado) {
                PeriodoFiltro.TODOS -> true
                PeriodoFiltro.HOJE -> ehMesmoDia(item.acionado_em, agora)
                PeriodoFiltro.SEMANA -> ehMesmaSemana(item.acionado_em, agora)
                PeriodoFiltro.MES -> ehMesmoMes(item.acionado_em, agora)
            }
        }

        val resultadoFinal = if (termoBuscaAtual.isBlank()) {
            filtradosPorPeriodo
        } else {
            filtradosPorPeriodo.filter {
                it.botao_nome.lowercase().contains(termoBuscaAtual) ||
                it.categoria_nome.lowercase().contains(termoBuscaAtual)
            }
        }

        adapter.atualizarItens(resultadoFinal)

        if (resultadoFinal.isEmpty()) {
            layoutSemHistorico.visibility = View.VISIBLE
            rvHistorico.visibility = View.GONE
        } else {
            layoutSemHistorico.visibility = View.GONE
            rvHistorico.visibility = View.VISIBLE
        }
    }

    private fun ehMesmoDia(dataIso: String, agora: Calendar): Boolean {
        val calItem = converterParaCalendar(dataIso) ?: return false
        return calItem.get(Calendar.YEAR) == agora.get(Calendar.YEAR) &&
               calItem.get(Calendar.DAY_OF_YEAR) == agora.get(Calendar.DAY_OF_YEAR)
    }

    private fun ehMesmaSemana(dataIso: String, agora: Calendar): Boolean {
        val calItem = converterParaCalendar(dataIso) ?: return false
        val diffDias = (agora.timeInMillis - calItem.timeInMillis) / (1000 * 60 * 60 * 24)
        return diffDias in 0..7
    }

    private fun ehMesmoMes(dataIso: String, agora: Calendar): Boolean {
        val calItem = converterParaCalendar(dataIso) ?: return false
        return calItem.get(Calendar.YEAR) == agora.get(Calendar.YEAR) &&
               calItem.get(Calendar.MONTH) == agora.get(Calendar.MONTH)
    }

    private fun converterParaCalendar(dataIso: String): Calendar? {
        if (dataIso.isBlank()) return null
        return try {
            val formatoIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dataLimpa = if (dataIso.contains(".")) dataIso.substringBefore(".") else dataIso.replace("Z", "")
            val date = formatoIso.parse(dataLimpa) ?: return null
            Calendar.getInstance().apply { time = date }
        } catch (_: Exception) {
            null
        }
    }

    private fun confirmarExclusaoItem(acionamento: Acionamento) {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setTitle("Excluir Registro")
            .setMessage("Deseja remover este acionamento do histórico?")
            .setPositiveButton("Excluir") { dialog, _ ->
                dialog.dismiss()
                lifecycleScope.launch {
                    val res = historicoService.removerAcionamento(uid, acionamento.id)
                    if (res.isSuccess) {
                        todosAcionamentos = todosAcionamentos.filter { it.id != acionamento.id }
                        aplicarFiltrosEExibir()
                        Toast.makeText(this@HistoricoActivity, "Registro excluído", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@HistoricoActivity, "Falha ao excluir registro", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarLimpezaTotal() {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this)
            .setTitle("Limpar Histórico Completo")
            .setMessage("Tem certeza que deseja apagar TODOS os registros de acionamentos? Esta ação não pode ser desfeita.")
            .setPositiveButton("Sim, Limpar Tudo") { dialog, _ ->
                dialog.dismiss()
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val res = historicoService.limparTodoHistorico(uid)
                    progressBar.visibility = View.GONE
                    if (res.isSuccess) {
                        todosAcionamentos = emptyList()
                        aplicarFiltrosEExibir()
                        Toast.makeText(this@HistoricoActivity, "Histórico limpo com sucesso (${res.getOrNull()} registros)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@HistoricoActivity, "Falha ao limpar histórico", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
