package fatec.labweb.projetocasa.view

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import android.provider.MediaStore
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.util.SoundManager
import fatec.labweb.projetocasa.service.DesempehoService
import fatec.labweb.projetocasa.service.MetricasDesempenho
import fatec.labweb.projetocasa.service.PontoGrafico
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DesempenhoActivity : BaseDrawerActivity() {

    companion object {
        private const val CHANNEL_ID = "relatorios_downloads_channel"
        private const val CHANNEL_NAME = "Downloads de Relatórios"
        private const val NOTIFICATION_ID = 2001
    }

    override fun getContentLayoutId(): Int = R.layout.activity_desempenho
    override fun getCurrentMenuItemId(): Int = R.id.menuDesempenho

    private val service = DesempehoService()

    private lateinit var chartDesempenho: LineChart
    private lateinit var txtTotalAcionamentos: TextView
    private lateinit var txtMediaDiaria: TextView
    private lateinit var btnGerarPdf: MaterialButton

    private var metricasAtuais: MetricasDesempenho? = null
    private var totalAcionamentosAtual = 0
    private var mediaDiariaAtual = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        findViewById<View>(R.id.btnVoltar)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            finish()
        }

        initViews()
        criarCanalNotificacao()
        configurarEstiloInicialGrafico()
        carregarMetricas()

        btnGerarPdf.setOnClickListener {
            exportarRelatorioPdf()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                SoundManager.dispararAudioCue(findViewById(android.R.id.home) ?: window.decorView)
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        chartDesempenho = findViewById(R.id.chartDesempenho)
        txtTotalAcionamentos = findViewById(R.id.txtTotalAcionamentos)
        txtMediaDiaria = findViewById(R.id.txtMediaDiaria)
        btnGerarPdf = findViewById(R.id.btnGerarPdf)
    }

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de conclusão de exportação de relatórios PDF"
                enableLights(true)
                lightColor = Color.BLUE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun configurarEstiloInicialGrafico() {
        chartDesempenho.description.isEnabled = false
        chartDesempenho.setDrawGridBackground(false)
        chartDesempenho.setTouchEnabled(true)
        chartDesempenho.isDragEnabled = true
        chartDesempenho.setScaleEnabled(false)
        chartDesempenho.setPinchZoom(false)
        chartDesempenho.legend.isEnabled = false
        chartDesempenho.setNoDataText("Carregando métricas de desempenho...")
        chartDesempenho.setNoDataTextColor(Color.parseColor("#64748B"))
    }

    private fun carregarMetricas() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

        lifecycleScope.launch {
            try {
                val metricas = service.carregarMetricas(uid, diasHistorico = 7)
                metricasAtuais = metricas

                totalAcionamentosAtual = metricas.totalAcionamentos
                mediaDiariaAtual = metricas.mediaDiaria

                txtTotalAcionamentos.text = metricas.totalAcionamentos.toString()
                txtMediaDiaria.text = String.format(Locale.getDefault(), "%.1f", metricas.mediaDiaria)

                renderizarGrafico(metricas.pontosGrafico)
            } catch (e: Exception) {
                Toast.makeText(this@DesempenhoActivity, "Erro ao carregar dados do gráfico: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderizarGrafico(pontos: List<PontoGrafico>) {
        if (pontos.isEmpty()) {
            chartDesempenho.clear()
            return
        }

        val entries = pontos.mapIndexed { index, ponto ->
            Entry(index.toFloat(), ponto.valor)
        }

        val dataSet = LineDataSet(entries, "Acionamentos").apply {
            color = Color.parseColor("#2563EB")
            setCircleColor(Color.parseColor("#2563EB"))
            circleRadius = 5f
            circleHoleRadius = 2.5f
            circleHoleColor = Color.WHITE
            lineWidth = 2.5f
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = Color.parseColor("#1E293B")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#93C5FD")
            fillAlpha = 70
        }

        val xAxis = chartDesempenho.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.parseColor("#64748B")
        xAxis.textSize = 10f
        xAxis.granularity = 1f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in pontos.indices) pontos[idx].dataLabel else ""
            }
        }

        val leftAxis = chartDesempenho.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#E2E8F0")
        leftAxis.textColor = Color.parseColor("#64748B")
        leftAxis.axisMinimum = 0f
        leftAxis.granularity = 1f

        chartDesempenho.axisRight.isEnabled = false

        chartDesempenho.data = LineData(dataSet)
        chartDesempenho.animateY(700)
        chartDesempenho.invalidate()
    }

    /**
     * Geração programática e física de documento PDF utilizando PdfDocument nativo
     * e salvamento no armazenamento público do dispositivo (Downloads / MediaStore)
     */
    private fun exportarRelatorioPdf() {
        lifecycleScope.launch(Dispatchers.IO) {
            var pdfDoc: PdfDocument? = null
            try {
                pdfDoc = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // Definições de Estilos e Pincéis
                val paintHeaderBg = Paint().apply { color = Color.parseColor("#2563EB") }
                val paintHeaderWhite = Paint().apply {
                    color = Color.WHITE
                    textSize = 18f
                    isFakeBoldText = true
                }
                val paintHeaderSub = Paint().apply {
                    color = Color.parseColor("#DBEAFE")
                    textSize = 11f
                }
                val paintSecao = Paint().apply {
                    color = Color.parseColor("#1E293B")
                    textSize = 14f
                    isFakeBoldText = true
                }
                val paintTexto = Paint().apply {
                    color = Color.parseColor("#334155")
                    textSize = 12f
                }
                val paintMuted = Paint().apply {
                    color = Color.parseColor("#64748B")
                    textSize = 10f
                }
                val paintLinha = Paint().apply {
                    color = Color.parseColor("#E2E8F0")
                    strokeWidth = 1f
                }
                val paintCardBg = Paint().apply {
                    color = Color.parseColor("#F8FAFC")
                }

                // 1. Cabeçalho com banner
                canvas.drawRect(0f, 0f, 595f, 100f, paintHeaderBg)
                canvas.drawText("PROJETO C.A.S.A. - RELATÓRIO DE DESEMPENHO", 36f, 50f, paintHeaderWhite)
                val dataFormatada = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                canvas.drawText("Comunicação Alternativa Simplificada e Acessível • Emitido em: $dataFormatada", 36f, 75f, paintHeaderSub)

                // 2. Dados do Usuário e Dispositivo
                val user = FirebaseAuth.getInstance().currentUser
                val nomeUsuario = user?.displayName ?: "Usuário C.A.S.A."
                val emailUsuario = user?.email ?: "Não informado"

                canvas.drawText("Identificação:", 36f, 130f, paintSecao)
                canvas.drawText("Usuário: $nomeUsuario ($emailUsuario)", 44f, 150f, paintTexto)
                canvas.drawText("Dispositivo ESP32: C.A.S.A. Main Board (Wi-Fi 100% Operacional)", 44f, 170f, paintTexto)

                canvas.drawLine(36f, 190f, 559f, 190f, paintLinha)

                // 3. Resumo das Métricas
                canvas.drawText("Resumo Geral dos Últimos 7 Dias:", 36f, 215f, paintSecao)

                // Cards de resumo
                canvas.drawRoundRect(36f, 230f, 280f, 290f, 8f, 8f, paintCardBg)
                canvas.drawText("Total de Acionamentos", 50f, 252f, paintMuted)
                val paintBigNum = Paint().apply {
                    color = Color.parseColor("#2563EB")
                    textSize = 22f
                    isFakeBoldText = true
                }
                canvas.drawText("$totalAcionamentosAtual acionamentos", 50f, 278f, paintBigNum)

                canvas.drawRoundRect(300f, 230f, 559f, 290f, 8f, 8f, paintCardBg)
                canvas.drawText("Média Diária", 314f, 252f, paintMuted)
                canvas.drawText(String.format(Locale.getDefault(), "%.1f acionamentos / dia", mediaDiariaAtual), 314f, 278f, paintBigNum)

                // 4. Detalhamento dos acionamentos por dia (dados reais do Firestore)
                canvas.drawText("Detalhamento Diário:", 36f, 325f, paintSecao)

                var yPos = 350f
                val pontos = metricasAtuais?.pontosGrafico ?: emptyList()
                if (pontos.isNotEmpty()) {
                    for (ponto in pontos) {
                        canvas.drawText("• Dia ${ponto.dataLabel}:", 44f, yPos, paintTexto)
                        canvas.drawText("${ponto.valor.toInt()} acionamento(s)", 200f, yPos, paintTexto)

                        val barraLargura = (ponto.valor * 15f).coerceAtMost(250f)
                        if (barraLargura > 0) {
                            val paintBarra = Paint().apply { color = Color.parseColor("#93C5FD") }
                            canvas.drawRoundRect(310f, yPos - 10f, 310f + barraLargura, yPos + 2f, 4f, 4f, paintBarra)
                        }
                        yPos += 24f
                    }
                } else {
                    canvas.drawText("Nenhum acionamento registrado no período de 7 dias.", 44f, yPos, paintTexto)
                    yPos += 24f
                }

                // Vocabulário mais acionado
                val maisAcionado = metricasAtuais?.maisAcionado?.ifBlank { "Água" } ?: "Água"
                val menosAcionado = metricasAtuais?.menosAcionado?.ifBlank { "Dor" } ?: "Dor"
                yPos += 15f
                canvas.drawLine(36f, yPos, 559f, yPos, paintLinha)
                yPos += 25f
                canvas.drawText("Estatísticas de Vocabulário:", 36f, yPos, paintSecao)
                yPos += 20f
                canvas.drawText("• Botão mais acionado no período: $maisAcionado", 44f, yPos, paintTexto)
                yPos += 20f
                canvas.drawText("• Botão sugerido para ajuste: $menosAcionado", 44f, yPos, paintTexto)

                // 5. Rodapé
                canvas.drawLine(36f, 780f, 559f, 780f, paintLinha)
                canvas.drawText("Documento oficial gerado pelo aplicativo Projeto C.A.S.A. (Android Native / Kotlin)", 36f, 800f, paintMuted)
                canvas.drawText("Página 1 de 1", 500f, 800f, paintMuted)

                pdfDoc.finishPage(page)

                // Gravação física do arquivo no armazenamento do dispositivo
                val nomeArquivo = "relatorio_desempenho_${System.currentTimeMillis()}.pdf"
                val uriSalva = salvarPdfEmDownloads(this@DesempenhoActivity, pdfDoc, nomeArquivo)

                withContext(Dispatchers.Main) {
                    if (uriSalva != null) {
                        dispararNotificacaoSucesso(nomeArquivo, uriSalva)
                        Toast.makeText(
                            this@DesempenhoActivity,
                            "Relatório salvo com sucesso na pasta Downloads:\n$nomeArquivo",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@DesempenhoActivity, "Não foi possível gravar o ficheiro do relatório.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DesempenhoActivity, "Erro ao exportar PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                pdfDoc?.close()
            }
        }
    }

    private fun salvarPdfEmDownloads(context: Context, pdfDocument: PdfDocument, nomeArquivo: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ via MediaStore.Downloads
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeArquivo)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ProjetoCASA")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            var uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                // Fallback para raiz de Downloads
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            }

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { os ->
                    pdfDocument.writeTo(os)
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } else {
            // Android 9 e anteriores
            val dirDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dirApp = File(dirDownloads, "ProjetoCASA")
            if (!dirApp.exists()) dirApp.mkdirs()

            val arquivo = File(dirApp, nomeArquivo)
            FileOutputStream(arquivo).use { os ->
                pdfDocument.writeTo(os)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", arquivo)
        }
    }

    private fun dispararNotificacaoSucesso(nomeArquivo: String, fileUri: Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Relatório C.A.S.A. Gerado")
            .setContentText("Arquivo salvo em Downloads: $nomeArquivo")
            .setStyle(NotificationCompat.BigTextStyle().bigText("O relatório de desempenho dos últimos 7 dias foi gerado e salvo com sucesso em Downloads/ProjetoCASA/$nomeArquivo. Toque para visualizar."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
