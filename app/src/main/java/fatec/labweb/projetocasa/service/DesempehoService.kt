package fatec.labweb.projetocasa.service

import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.model.Acionamento
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PontoGrafico(
    val dataLabel: String,
    val valor: Float
)

data class MetricasDesempenho(
    val totalAcionamentos: Int,
    val mediaDiaria: Double,
    val pontosGrafico: List<PontoGrafico>,
    val maisAcionado: String = "",
    val menosAcionado: String = ""
)

class DesempehoService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val formatoDiaMes = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    private val formatoChave = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val formatoIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun carregarMetricas(userId: String, diasHistorico: Int = 7): MetricasDesempenho {
        return try {
            val snap = firestore.collection("usuarios")
                .document(userId)
                .collection("acionamentos")
                .get()
                .await()

            val acionamentos = if (!snap.isEmpty) {
                snap.documents.mapNotNull { it.toObject(Acionamento::class.java) }
            } else {
                // Tenta consultar acionamentos na coleção raiz
                val snapRoot = firestore.collection("acionamentos")
                    .whereEqualTo("owner_uid", userId)
                    .get()
                    .await()
                snapRoot.documents.mapNotNull { it.toObject(Acionamento::class.java) }
            }

            processarAcionamentos(acionamentos, diasHistorico)
        } catch (e: Exception) {
            processarAcionamentos(emptyList(), diasHistorico)
        }
    }

    private fun processarAcionamentos(
        acionamentos: List<Acionamento>,
        diasHistorico: Int
    ): MetricasDesempenho {
        // Gera as datas dos últimos dias
        val mapaContagemPorData = linkedMapOf<String, Pair<String, Int>>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(diasHistorico - 1))

        for (i in 0 until diasHistorico) {
            val chave = formatoChave.format(cal.time)
            val label = formatoDiaMes.format(cal.time)
            mapaContagemPorData[chave] = Pair(label, 0)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val contagemBotoes = mutableMapOf<String, Int>()

        for (ac in acionamentos) {
            val dataObj = parseData(ac.acionado_em)
            if (dataObj != null) {
                val chave = formatoChave.format(dataObj)
                if (mapaContagemPorData.containsKey(chave)) {
                    val par = mapaContagemPorData[chave]!!
                    mapaContagemPorData[chave] = Pair(par.first, par.second + 1)
                }
            }
            if (ac.botao_nome.isNotBlank()) {
                contagemBotoes[ac.botao_nome] = contagemBotoes.getOrDefault(ac.botao_nome, 0) + 1
            }
        }

        // Dados 100% reais do Firestore sem injeção de métricas fictícias
        val totalReal = acionamentos.size
        val pontosGrafico = mapaContagemPorData.values.map { par ->
            PontoGrafico(dataLabel = par.first, valor = par.second.toFloat())
        }

        val media = if (totalReal > 0) totalReal.toDouble() / diasHistorico.coerceAtLeast(1) else 0.0

        val mais = if (contagemBotoes.isNotEmpty()) contagemBotoes.maxByOrNull { it.value }?.key ?: "Nenhum" else "Nenhum"
        val menos = if (contagemBotoes.isNotEmpty()) contagemBotoes.minByOrNull { it.value }?.key ?: "Nenhum" else "Nenhum"

        return MetricasDesempenho(
            totalAcionamentos = totalReal,
            mediaDiaria = media,
            pontosGrafico = pontosGrafico,
            maisAcionado = mais,
            menosAcionado = menos
        )
    }

    private fun parseData(dataStr: String): Date? {
        if (dataStr.isBlank()) return null
        return try {
            formatoIso.parse(dataStr)
        } catch (e: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(dataStr)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
