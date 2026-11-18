package fatec.labweb.projetocasa.view

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.CasaApplication
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.repository.CategoriaRepository
import fatec.labweb.projetocasa.util.AudioDspHelper
import fatec.labweb.projetocasa.util.AudioPredefinidoHelper
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import fatec.labweb.projetocasa.util.SoundManager
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * ModalEdicaoBotao: Modal/Dialog dedicado para criação e edição de botões de comunicação.
 * Suporta filtragem de ações predefinidas já adicionadas, exclusão de opções redundantes,
 * áudio padrão masculino/feminino conforme o ecossistema C.A.S.A., imagem padrão e síntese por voz.
 */
class ModalEdicaoBotao(
    private val activity: Activity,
    private val onAbrirGaleria: (() -> Unit)? = null,
    private val onTirarFoto: (() -> Unit)? = null,
    private val onAbrirAudioLocal: (() -> Unit)? = null,
    private val onTirarFotoComId: ((botaoId: String) -> Unit)? = null
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var dialog: AlertDialog? = null
    private var mediaRecorder: MediaRecorder? = null
    private var previewPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isGravando: Boolean = false

    var botaoIdAtivo: String = ""
    var arquivoFotoCamera: File? = null
    var uriImagemSelecionada: Uri? = null
    var uriAudioSelecionado: Uri? = null
    var arquivoAudioGravado: File? = null
    var desenhoSelecionado: DesenhoBotao? = null
    var generoSelecionado: GeneroAudio = GeneroAudio.FEMALE

    // Views do modal ativo
    private var imgPreview: ImageView? = null
    private var txtStatusAudio: TextView? = null
    private var btnGravar: MaterialButton? = null
    private var btnParar: MaterialButton? = null
    private var btnPrevia: MaterialButton? = null
    private var progressBar: ProgressBar? = null
    private var btnSalvar: MaterialButton? = null

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

    fun mostrar(
        itemExistente: CategoriaItem?,
        categoriaId: String,
        categoriaNome: String = "",
        ordemPadrao: Int = 0,
        onSalvo: (itemAtualizado: CategoriaItem) -> Unit
    ) {
        uriImagemSelecionada = null
        uriAudioSelecionado = null
        arquivoAudioGravado = null
        desenhoSelecionado = null
        generoSelecionado = itemExistente?.defaultGender ?: GeneroAudio.FEMALE

        textToSpeech = TextToSpeech(activity) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("pt", "BR")
            }
        }

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.item_categoria_card, null)
        val txtModalTitle = dialogView.findViewById<TextView>(R.id.txtModalTitle)
        val txtModalSubtitle = dialogView.findViewById<TextView>(R.id.txtModalSubtitle)
        val etTitulo = dialogView.findViewById<EditText>(R.id.etTituloBotao)
        val tilTituloBotao = dialogView.findViewById<View>(R.id.tilTituloBotao)
        imgPreview = dialogView.findViewById<ImageView>(R.id.imgPreviewBotao)
            ?: dialogView.findViewById<ImageView>(R.id.imgPreview)

        val btnGaleria = dialogView.findViewById<View>(R.id.btnEscolherGaleria)
        val btnTirarFoto = dialogView.findViewById<View>(R.id.btnTirarFoto)
        val btnDesenhosDrive = dialogView.findViewById<View>(R.id.btnDesenhosDrive)
        val btnPadrao = dialogView.findViewById<View>(R.id.btnPadrao)

        val toggleGeneroAudio = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleGeneroAudio)
        val btnAudioDispositivo = dialogView.findViewById<View>(R.id.btnSelecionarAudio)
        btnGravar = dialogView.findViewById<MaterialButton>(R.id.btnGravarAudio)
        btnParar = dialogView.findViewById<MaterialButton>(R.id.btnPararGravacao)
        btnPrevia = dialogView.findViewById<MaterialButton>(R.id.btnOuvirPrevia)
        txtStatusAudio = dialogView.findViewById<TextView>(R.id.txtStatusAudio)
        progressBar = dialogView.findViewById<ProgressBar>(R.id.progressUploadModal)
        val btnCancelar = dialogView.findViewById<View>(R.id.btnCancelarModal)
        btnSalvar = dialogView.findViewById<MaterialButton>(R.id.btnSalvarModal)

        val isEdicao = itemExistente != null
        txtModalTitle?.text = if (isEdicao) "Editar Botão" else "Adicionar Botão"
        txtModalSubtitle?.text = if (categoriaNome.isNotBlank()) "Categoria: $categoriaNome" else "Personalização de Botão"

        // Lista de botões já adicionados nesta categoria para gerenciamento de slots
        val listaBotoesExistentes = if (categoriaId.isNotBlank()) {
            MidiaLocalHelper.carregarBotoesCategoriaLocalmente(activity, categoriaId)
        } else {
            emptyList()
        }

        val idParaAudio = itemExistente?.id?.takeIf { it.isNotBlank() }
            ?: ("btn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
        botaoIdAtivo = idParaAudio
        arquivoFotoCamera = null

        val ignorarBotaoId = if (isEdicao) (itemExistente?.id ?: "") else ""

        // 1. Unicidade Global (cross-category): Filtrar opções predefinidas para NÃO mostrar
        // botões já alocados nesta ou em qualquer outra categoria existente
        val desenhosDisponiveis = MidiaLocalHelper.obterDesenhosDisponiveisGlobalmente(
            context = activity,
            categoriaIdAtual = categoriaId,
            categoriaNomeAtual = categoriaNome,
            ignorarBotaoId = ignorarBotaoId
        )
        val mapaDesenhosEmUsoGlobal = MidiaLocalHelper.obterMapaDesenhosEmUsoGlobal(
            context = activity,
            categoriaIdAtual = categoriaId,
            categoriaNomeAtual = categoriaNome,
            ignorarBotaoId = ignorarBotaoId
        )

        // Configuração do Seletor de Slot / Posição (Botão 1..5)
        val spnSlotBotao = dialogView.findViewById<Spinner>(R.id.spnSlotBotao)
        val slotAtual = (itemExistente?.ordem ?: ordemPadrao).coerceIn(0, 4)
        val slotOptions = (0..4).map { slot ->
            val ocupante = listaBotoesExistentes.find { it.id != itemExistente?.id && it.ordem == slot }
            val isAtual = slotAtual == slot
            when {
                isAtual -> "Botão ${slot + 1} (Posição Atual)"
                ocupante != null -> "Botão ${slot + 1} — Inverter com \"${ocupante.rotulo}\""
                else -> "Botão ${slot + 1} — (Livre)"
            }
        }
        val slotAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, slotOptions)
        spnSlotBotao?.adapter = slotAdapter
        spnSlotBotao?.setSelection(slotAtual)

        // Configuração do Toggle Predefinida vs Personalizada
        val toggleModoAcao = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleModoAcao)
        val containerAcaoPredefinida = dialogView.findViewById<View>(R.id.containerAcaoPredefinida)
        val spnAcaoPredefinida = dialogView.findViewById<Spinner>(R.id.spnAcaoPredefinida)

        val nomesDesenhos = desenhosDisponiveis.map { "${it.titulo} (${it.categoriaPadrao.uppercase()})" }
        val adapterDesenhos = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, nomesDesenhos)
        spnAcaoPredefinida?.adapter = adapterDesenhos

        fun atualizarVisibilidadeModo(isPredefinido: Boolean) {
            if (isPredefinido) {
                containerAcaoPredefinida?.visibility = View.VISIBLE
                tilTituloBotao?.visibility = View.GONE
                btnDesenhosDrive?.visibility = View.GONE
                btnPadrao?.visibility = View.VISIBLE
            } else {
                containerAcaoPredefinida?.visibility = View.GONE
                tilTituloBotao?.visibility = View.VISIBLE
                btnDesenhosDrive?.visibility = View.VISIBLE
                btnPadrao?.visibility = View.GONE
            }
        }

        fun atualizarTextoStatusAudio() {
            val rotuloAtual = if (toggleModoAcao?.checkedButtonId == R.id.btnModoAcaoPredefinida && desenhoSelecionado != null) {
                desenhoSelecionado!!.titulo.split("/").first().trim()
            } else {
                etTitulo?.text?.toString()?.trim() ?: ""
            }
            val genNome = if (generoSelecionado == GeneroAudio.MALE) "Masculino" else "Feminino"

            when {
                arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                    txtStatusAudio?.text = "Voz gravada no dispositivo (${arquivoAudioGravado!!.name})"
                    btnPrevia?.visibility = View.VISIBLE
                }
                uriAudioSelecionado != null -> {
                    txtStatusAudio?.text = "Áudio local selecionado do dispositivo."
                    btnPrevia?.visibility = View.VISIBLE
                }
                else -> {
                    val rotuloExibir = rotuloAtual.ifBlank { "ação" }
                    txtStatusAudio?.text = "Áudio Padrão ($genNome) para \"$rotuloExibir\"."
                    btnPrevia?.visibility = View.VISIBLE
                }
            }
        }

        fun tocarAudioAtual() {
            when {
                arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                    reproduzirArquivo(arquivoAudioGravado!!)
                }
                uriAudioSelecionado != null -> {
                    reproduzirUri(uriAudioSelecionado!!)
                }
                else -> {
                    val rotuloAtual = if (toggleModoAcao?.checkedButtonId == R.id.btnModoAcaoPredefinida && desenhoSelecionado != null) {
                        desenhoSelecionado!!.titulo.split("/").first().trim()
                    } else {
                        etTitulo?.text?.toString()?.trim() ?: ""
                    }

                    if (rotuloAtual.isNotBlank()) {
                        AudioPredefinidoHelper.obterAudioVozNeural(activity, rotuloAtual, generoSelecionado) { audioNeural ->
                            activity.runOnUiThread {
                                if (audioNeural != null && audioNeural.exists()) {
                                    reproduzirArquivo(audioNeural)
                                } else {
                                    AudioPredefinidoHelper.falarPrevia(textToSpeech, rotuloAtual, generoSelecionado)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isEdicao && itemExistente != null) {
            etTitulo?.setText(itemExistente.rotulo)
            val localExistente = MidiaLocalHelper.obterArquivoLocal(activity, itemExistente.id)
            val imgAtual: Any = when {
                localExistente != null -> localExistente
                !itemExistente.localPath.isNullOrEmpty() && File(itemExistente.localPath!!).exists() -> File(itemExistente.localPath!!)
                !itemExistente.imageUrl.isNullOrEmpty() -> itemExistente.imageUrl!!
                !itemExistente.imageUri.isNullOrEmpty() -> itemExistente.imageUri!!
                !itemExistente.imagem.isNullOrEmpty() -> itemExistente.imagem!!
                DesenhoBotao.obterDrawable(itemExistente.rotulo, itemExistente.significado) != null -> DesenhoBotao.obterDrawable(itemExistente.rotulo, itemExistente.significado)!!
                else -> R.drawable.ic_default_button
            }

            imgPreview?.let { iv ->
                iv.clearColorFilter()
                iv.imageTintList = null
                Glide.with(activity)
                    .load(imgAtual)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
                    .into(iv)
            }

            val desenhoItemExistente = CategoriaRepository.identificarDesenhoPredefinido(itemExistente)
            val idxD = desenhosDisponiveis.indexOfFirst { d ->
                d == desenhoItemExistente ||
                d.id.equals(itemExistente.imagem, ignoreCase = true) ||
                d.id.equals(itemExistente.imagem_id, ignoreCase = true) ||
                d.titulo.equals(itemExistente.rotulo, ignoreCase = true) ||
                AudioPredefinidoHelper.normalizarTexto(d.titulo.split("/").first()) ==
                    AudioPredefinidoHelper.normalizarTexto(itemExistente.rotulo)
            }
            if (idxD != -1) {
                spnAcaoPredefinida?.setSelection(idxD)
                toggleModoAcao?.check(R.id.btnModoAcaoPredefinida)
                desenhoSelecionado = desenhosDisponiveis[idxD]
                atualizarVisibilidadeModo(true)
            } else {
                if (desenhosDisponiveis.isEmpty()) {
                    dialogView.findViewById<MaterialButton>(R.id.btnModoAcaoPredefinida)?.isEnabled = false
                }
                toggleModoAcao?.check(R.id.btnModoAcaoPersonalizada)
                atualizarVisibilidadeModo(false)
            }
        } else {
            if (desenhosDisponiveis.isNotEmpty()) {
                toggleModoAcao?.check(R.id.btnModoAcaoPredefinida)
                val d = desenhosDisponiveis[0]
                desenhoSelecionado = d
                etTitulo?.setText(d.titulo.split("/").first().trim())
                imgPreview?.setImageResource(d.resId)
                atualizarVisibilidadeModo(true)
            } else {
                toggleModoAcao?.check(R.id.btnModoAcaoPersonalizada)
                dialogView.findViewById<MaterialButton>(R.id.btnModoAcaoPredefinida)?.isEnabled = false
                atualizarVisibilidadeModo(false)
            }
        }

        // Configura toggle de gênero de áudio
        if (generoSelecionado == GeneroAudio.MALE) {
            toggleGeneroAudio?.check(R.id.btnAudioMasculino)
        } else {
            toggleGeneroAudio?.check(R.id.btnAudioFeminino)
        }

        toggleGeneroAudio?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                generoSelecionado = if (checkedId == R.id.btnAudioMasculino) GeneroAudio.MALE else GeneroAudio.FEMALE
                atualizarTextoStatusAudio()
                tocarAudioAtual()
            }
        }

        spnAcaoPredefinida?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (toggleModoAcao?.checkedButtonId == R.id.btnModoAcaoPredefinida && pos in desenhosDisponiveis.indices) {
                    val d = desenhosDisponiveis[pos]
                    desenhoSelecionado = d
                    etTitulo?.setText(d.titulo.split("/").first().trim())
                    imgPreview?.setImageResource(d.resId)
                    uriImagemSelecionada = null
                    arquivoFotoCamera = null
                    atualizarTextoStatusAudio()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        toggleModoAcao?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val isPre = (checkedId == R.id.btnModoAcaoPredefinida)
                atualizarVisibilidadeModo(isPre)
                if (isPre) {
                    val pos = spnAcaoPredefinida?.selectedItemPosition ?: 0
                    if (pos in desenhosDisponiveis.indices) {
                        val d = desenhosDisponiveis[pos]
                        desenhoSelecionado = d
                        etTitulo?.setText(d.titulo.split("/").first().trim())
                        imgPreview?.setImageResource(d.resId)
                    }
                } else {
                    desenhoSelecionado = null
                    if (!isEdicao) {
                        etTitulo?.setText("")
                        imgPreview?.setImageResource(R.drawable.ic_default_button)
                    }
                }
                atualizarTextoStatusAudio()
            }
        }

        btnPadrao?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            uriImagemSelecionada = null
            arquivoFotoCamera = null
            if (desenhoSelecionado != null) {
                imgPreview?.clearColorFilter()
                imgPreview?.imageTintList = null
                imgPreview?.setImageResource(desenhoSelecionado!!.resId)
                Toast.makeText(activity, "Foto padrão da ação restaurada!", Toast.LENGTH_SHORT).show()
            } else {
                imgPreview?.setImageResource(R.drawable.ic_default_button)
            }
        }

        btnGaleria?.setOnClickListener {
            onAbrirGaleria?.invoke()
        }

        btnTirarFoto?.setOnClickListener {
            if (onTirarFotoComId != null) {
                onTirarFotoComId.invoke(idParaAudio)
            } else {
                onTirarFoto?.invoke()
            }
        }

        btnDesenhosDrive?.setOnClickListener {
            DialogSeletorDesenhos(
                context = activity,
                desenhosEmUso = mapaDesenhosEmUsoGlobal
            ) { desenho ->
                desenhoSelecionado = desenho
                uriImagemSelecionada = null
                imgPreview?.clearColorFilter()
                imgPreview?.imageTintList = null
                imgPreview?.setImageResource(desenho.resId)
                if (etTitulo?.text.isNullOrBlank()) {
                    etTitulo?.setText(desenho.titulo.split("/").first().trim())
                }
            }.exibir()
        }

        btnAudioDispositivo?.setOnClickListener {
            onAbrirAudioLocal?.invoke()
        }

        btnGravar?.setOnClickListener {
            ModalGravadorAudio(activity, idParaAudio) { arquivoLocal ->
                arquivoAudioGravado = arquivoLocal
                uriAudioSelecionado = null
                txtStatusAudio?.text = "Voz gravada no dispositivo (${arquivoLocal.name})"
                btnPrevia?.visibility = View.VISIBLE
            }.iniciar()
        }

        btnParar?.setOnClickListener {
            pararGravacao()
        }

        btnPrevia?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            tocarAudioAtual()
        }

        dialogView.findViewById<View>(R.id.btnVoltarModal)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            fechar()
        }

        btnCancelar?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            fechar()
        }

        btnSalvar?.setOnClickListener {
            if (!isEdicao && listaBotoesExistentes.size >= CategoriaRepository.MAX_BOTOES_POR_CATEGORIA) {
                Toast.makeText(activity, CategoriaRepository.MSG_MAX_ACOES_CATEGORIA, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val isModoPredefinido = toggleModoAcao?.checkedButtonId == R.id.btnModoAcaoPredefinida
            if (isModoPredefinido && desenhoSelecionado == null) {
                Toast.makeText(
                    activity,
                    "Nenhum botão predefinido disponível. Todos os botões predefinidos já estão em uso nas categorias.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val rotulo = if (isModoPredefinido && desenhoSelecionado != null) {
                desenhoSelecionado!!.titulo.split("/").first().trim()
            } else {
                etTitulo?.text?.toString()?.trim() ?: ""
            }

            if (rotulo.isBlank()) {
                etTitulo?.error = "Informe o título do botão"
                return@setOnClickListener
            }

            // Validação de unicidade global (cross-category) na adição/seleção e edição de botões
            val candidatoValidacao = CategoriaItem(
                id = idParaAudio,
                rotulo = rotulo,
                significado = rotulo,
                imagem = desenhoSelecionado?.id ?: itemExistente?.imagem,
                imagem_id = desenhoSelecionado?.id ?: itemExistente?.imagem_id,
                imageUrl = desenhoSelecionado?.driveUrl ?: itemExistente?.imageUrl
            )

            val conflitoGlobal = MidiaLocalHelper.verificarConflitoGlobalBotao(
                context = activity,
                candidato = candidatoValidacao,
                categoriaIdAtual = categoriaId,
                categoriaNomeAtual = categoriaNome,
                ignorarBotaoId = ignorarBotaoId
            )
            if (conflitoGlobal != null) {
                val mesmaCat = categoriaId.isNotBlank() && (
                    conflitoGlobal.categoriaId.equals(categoriaId, ignoreCase = true) ||
                    conflitoGlobal.categoriaId.equals("cat_$categoriaId", ignoreCase = true) ||
                    (categoriaId.startsWith("cat_") && conflitoGlobal.categoriaId.equals(categoriaId.removePrefix("cat_"), ignoreCase = true))
                )
                val msgErro = if (mesmaCat) {
                    "O botão \"$rotulo\" já foi adicionado a esta categoria! Não é permitido botões repetidos."
                } else {
                    "O botão \"$rotulo\" já está vinculado à categoria \"${conflitoGlobal.categoriaNome}\"! Não é permitido repetir botões entre categorias."
                }
                Toast.makeText(activity, msgErro, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (isGravando) {
                pararGravacao()
            }

            btnSalvar?.isEnabled = false
            progressBar?.visibility = View.VISIBLE

            val botaoId = idParaAudio
            val slotFinal = spnSlotBotao?.selectedItemPosition ?: (itemExistente?.ordem ?: ordemPadrao)

            // Se o slot foi alterado e havia outro botão ocupando, inverte a ordem
            if (categoriaId.isNotBlank()) {
                val listaLocal = MidiaLocalHelper.carregarBotoesCategoriaLocalmente(activity, categoriaId).toMutableList()
                val ocupante = listaLocal.find { it.id != botaoId && it.ordem == slotFinal }
                if (ocupante != null) {
                    val ordemAnterior = itemExistente?.ordem ?: listaLocal.size.coerceAtLeast(0)
                    ocupante.ordem = ordemAnterior
                    val idxOcupante = listaLocal.indexOfFirst { it.id == ocupante.id }
                    if (idxOcupante != -1) {
                        listaLocal[idxOcupante] = ocupante
                        MidiaLocalHelper.salvarBotoesCategoriaLocalmente(activity, categoriaId, listaLocal)
                    }
                }
            }

            fun persistirEConcluir() {
                salvarComUploadEstrito(
                    botaoId = botaoId,
                    categoriaId = categoriaId,
                    rotulo = rotulo,
                    ordem = slotFinal,
                    imageUrlExistente = itemExistente?.imageUrl ?: itemExistente?.imageUri,
                    audioUrlExistente = itemExistente?.audioUrl ?: itemExistente?.audioPath,
                    onSucesso = { itemSalvo ->
                        onSalvo(itemSalvo)
                        fechar()
                    }
                )
            }

            // Prepara o áudio padrão com voz neural oficial caso o usuário não tenha selecionado arquivo nem gravado voz
            if (arquivoAudioGravado == null && uriAudioSelecionado == null) {
                val dir = activity.getExternalFilesDir("botoes_audios") ?: activity.filesDir
                val destMp3 = File(dir, "${botaoId}_${generoSelecionado.name.lowercase()}.mp3")
                AudioPredefinidoHelper.sintetizarVozOficialParaArquivo(
                    activity,
                    textToSpeech,
                    rotulo,
                    generoSelecionado,
                    destMp3
                ) { gerado ->
                    activity.runOnUiThread {
                        if (gerado != null) {
                            arquivoAudioGravado = gerado
                        }
                        persistirEConcluir()
                    }
                }
            } else {
                persistirEConcluir()
            }
        }

        atualizarTextoStatusAudio()

        dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog?.setOnDismissListener {
            liberarRecursos()
        }

        dialog?.show()
    }

    private fun reproduzirArquivo(arquivo: File) {
        pararPrevia()
        try {
            previewPlayer = MediaPlayer().apply {
                AudioDspHelper.configurarMediaPlayerAltoFalante(activity, this)
                setDataSource(arquivo.absolutePath)
                prepare()
                AudioDspHelper.configurarMediaPlayerAltoFalante(activity, this)
                start()
                setOnCompletionListener {
                    pararPrevia()
                }
            }
        } catch (_: Exception) {}
    }

    private fun reproduzirUri(uri: Uri) {
        pararPrevia()
        try {
            previewPlayer = MediaPlayer().apply {
                AudioDspHelper.configurarMediaPlayerAltoFalante(activity, this)
                setDataSource(activity, uri)
                prepare()
                AudioDspHelper.configurarMediaPlayerAltoFalante(activity, this)
                start()
                setOnCompletionListener {
                    pararPrevia()
                }
            }
        } catch (_: Exception) {}
    }

    private fun pararPrevia() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
    }

    fun definirImagemPreview(uri: Uri, localFile: File? = null) {
        desenhoSelecionado = null
        val arquivoPersistido = when {
            localFile != null && localFile.exists() && localFile.length() > 0 -> localFile
            botaoIdAtivo.isNotBlank() -> MidiaLocalHelper.salvarImagemLocalmente(activity, botaoIdAtivo, uri)
            else -> MidiaLocalHelper.copiarUriParaArmazenamentoPermanente(activity, uri, "acao")
        }
        if (arquivoPersistido != null && arquivoPersistido.exists()) {
            arquivoFotoCamera = arquivoPersistido
            uriImagemSelecionada = Uri.fromFile(arquivoPersistido)
        } else {
            uriImagemSelecionada = uri
        }
        imgPreview?.let { iv ->
            iv.clearColorFilter()
            iv.imageTintList = null
            iv.visibility = View.VISIBLE
            val imageSource: Any = if (arquivoPersistido != null && arquivoPersistido.exists()) arquivoPersistido else uri
            val sigKey = ObjectKey("${arquivoPersistido?.absolutePath ?: uri}_${System.currentTimeMillis()}")
            Glide.with(activity)
                .load(imageSource)
                .signature(sigKey)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .centerCrop()
                .placeholder(R.drawable.ic_default_button)
                .error(R.drawable.ic_default_button)
                .into(iv)
        }
    }

    fun definirImagemPreview(uri: Uri) {
        definirImagemPreview(uri, null)
    }

    fun definirAudioSelecionado(uri: Uri) {
        uriAudioSelecionado = uri
        arquivoAudioGravado = null
        txtStatusAudio?.text = "Áudio local selecionado do dispositivo."
        btnPrevia?.visibility = View.VISIBLE
    }

    private fun salvarComUploadEstrito(
        botaoId: String,
        categoriaId: String,
        rotulo: String,
        ordem: Int,
        imageUrlExistente: String?,
        audioUrlExistente: String?,
        onSucesso: (CategoriaItem) -> Unit
    ) {
        val localFile = when {
            arquivoFotoCamera != null && arquivoFotoCamera!!.exists() && arquivoFotoCamera!!.length() > 0 -> {
                MidiaLocalHelper.salvarImagemLocalmente(activity, botaoId, arquivoFotoCamera!!)
                    ?: arquivoFotoCamera
            }
            uriImagemSelecionada != null -> {
                MidiaLocalHelper.salvarImagemLocalmente(activity, botaoId, uriImagemSelecionada!!)
            }
            desenhoSelecionado != null -> {
                MidiaLocalHelper.salvarImagemLocalmente(activity, botaoId, desenhoSelecionado!!.resId)
            }
            else -> {
                MidiaLocalHelper.obterArquivoLocal(activity, botaoId)
            }
        }
        val localPath = localFile?.absolutePath

        val localAudioFile = when {
            arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                MidiaLocalHelper.salvarAudioLocalmente(activity, botaoId, arquivoAudioGravado!!)
            }
            uriAudioSelecionado != null -> {
                MidiaLocalHelper.salvarAudioLocalmente(activity, botaoId, uriAudioSelecionado!!)
            }
            else -> MidiaLocalHelper.obterAudioLocal(activity, botaoId)
        }

        val prefs = activity.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("botao_${botaoId}_rotulo", rotulo)
            putString("botao_${botaoId}_imagem_path", localPath ?: "")
            putString("botao_${botaoId}_audio_path", localAudioFile?.absolutePath ?: "")
            putString("botao_${botaoId}_categoria_id", categoriaId)
            putString("botao_${botaoId}_genero", generoSelecionado.name)
            apply()
        }

        val baseImg = localPath ?: imageUrlExistente
        val finalImg = if (!baseImg.isNullOrBlank()) {
            MidiaLocalHelper.anexarTimestampCacheBusting(baseImg)
        } else null
        val finalAudio = localAudioFile?.absolutePath ?: audioUrlExistente

        val itemFinal = CategoriaItem(
            id = botaoId,
            rotulo = rotulo,
            significado = rotulo,
            imagem = finalImg?.ifBlank { null },
            imagem_id = (desenhoSelecionado?.id ?: finalImg)?.ifBlank { null },
            imageUrl = finalImg?.ifBlank { null },
            audio = finalAudio?.ifBlank { null },
            audio_id = finalAudio?.ifBlank { null },
            audioUrl = finalAudio?.ifBlank { null },
            tipo_audio = if (generoSelecionado == GeneroAudio.MALE) "masculino" else "feminino",
            localPath = localPath,
            ordem = ordem,
            defaultGender = generoSelecionado
        )

        if (categoriaId.isNotBlank()) {
            val listaAtual = MidiaLocalHelper.carregarBotoesCategoriaLocalmente(activity, categoriaId)
            val idx = listaAtual.indexOfFirst { it.id == botaoId }
            if (idx != -1) {
                listaAtual[idx] = itemFinal
            } else {
                listaAtual.add(itemFinal)
            }
            MidiaLocalHelper.salvarBotoesCategoriaLocalmente(activity, categoriaId, listaAtual)
        }

        Toast.makeText(activity, "Botão salvo no dispositivo!", Toast.LENGTH_SHORT).show()
        onSucesso(itemFinal)

        val storageRefImg = storage.reference.child("categorias/imagens/$botaoId.jpg")
        val storageRefAudio = storage.reference.child("categorias/audios/$botaoId.m4a")

        fun sincronizarNoFirestore(urlImgRemota: String, urlAudioRemota: String) {
            val dadosAtualizados = hashMapOf<String, Any>(
                "id" to botaoId,
                "rotulo" to rotulo,
                "nome" to rotulo,
                "significado" to rotulo,
                "categoria_id" to categoriaId,
                "owner_uid" to currentUserId,
                "ordem" to ordem,
                "defaultGender" to generoSelecionado.name,
                "tipo_audio" to (if (generoSelecionado == GeneroAudio.MALE) "masculino" else "feminino"),
                "imageUrl" to urlImgRemota,
                "imageUri" to urlImgRemota,
                "imagem" to urlImgRemota,
                "imagem_id" to urlImgRemota,
                "localPath" to (localPath ?: ""),
                "audioUrl" to urlAudioRemota,
                "audioPath" to urlAudioRemota,
                "audio" to urlAudioRemota,
                "audio_id" to urlAudioRemota
            )

            firestore.collection("categoria_botao").document(botaoId).set(dadosAtualizados)
                .addOnSuccessListener {
                    if (categoriaId.isNotBlank()) {
                        firestore.collection("usuarios").document(currentUserId)
                            .collection("categorias").document(categoriaId)
                            .get().addOnSuccessListener { docSnap ->
                                val cat = docSnap.toObject(Categoria::class.java)
                                if (cat != null) {
                                    val idx = cat.botoes.indexOfFirst { it.id == botaoId }
                                    if (idx >= 0) {
                                        cat.botoes[idx] = itemFinal
                                    } else {
                                        cat.botoes.add(itemFinal)
                                    }
                                    firestore.collection("usuarios").document(currentUserId)
                                        .collection("categorias").document(categoriaId).set(cat)
                                        .addOnFailureListener { /* Tolerante */ }
                                }
                            }.addOnFailureListener { /* Tolerante */ }
                    }
                }
                .addOnFailureListener { /* Tolerante */ }
        }

        fun uploadAudioNuvem(urlImg: String) {
            val uriAudioLocal: Uri? = when {
                arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> Uri.fromFile(arquivoAudioGravado)
                uriAudioSelecionado != null -> uriAudioSelecionado
                localAudioFile != null && localAudioFile.exists() -> Uri.fromFile(localAudioFile)
                else -> null
            }

            if (uriAudioLocal != null) {
                storageRefAudio.putFile(uriAudioLocal).continueWithTask { audioTask ->
                    if (!audioTask.isSuccessful) audioTask.exception?.let { throw it }
                    storageRefAudio.downloadUrl
                }.addOnSuccessListener { newAudioUrl ->
                    sincronizarNoFirestore(urlImg, newAudioUrl.toString())
                }.addOnFailureListener {
                    sincronizarNoFirestore(urlImg, audioUrlExistente ?: "")
                }
            } else {
                sincronizarNoFirestore(urlImg, audioUrlExistente ?: "")
            }
        }

        val uriImagemLocal: Uri? = when {
            uriImagemSelecionada != null -> uriImagemSelecionada
            localFile != null && localFile.exists() -> Uri.fromFile(localFile)
            else -> null
        }

        if (uriImagemLocal != null) {
            storageRefImg.putFile(uriImagemLocal).continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                storageRefImg.downloadUrl
            }.addOnSuccessListener { newImageUrl ->
                uploadAudioNuvem(newImageUrl.toString())
            }.addOnFailureListener {
                uploadAudioNuvem(desenhoSelecionado?.driveUrl ?: imageUrlExistente ?: "")
            }
        } else {
            uploadAudioNuvem(desenhoSelecionado?.driveUrl ?: imageUrlExistente ?: "")
        }
    }

    private fun pararGravacao() {
        if (!isGravando) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isGravando = false
            txtStatusAudio?.text = "Voz gravada com sucesso!"
            btnGravar?.visibility = View.VISIBLE
            btnParar?.visibility = View.GONE
            btnPrevia?.visibility = View.VISIBLE
            Toast.makeText(activity, "Gravação finalizada!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            isGravando = false
            mediaRecorder?.release()
            mediaRecorder = null
            txtStatusAudio?.text = "Erro ao parar gravação."
        }
    }

    private fun liberarRecursos() {
        if (isGravando) {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
        }
        mediaRecorder?.release()
        mediaRecorder = null
        isGravando = false

        pararPrevia()

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
    }

    fun fechar() {
        liberarRecursos()
        dialog?.dismiss()
        dialog = null
    }

    companion object {
        fun criarArquivoFotoCamera(context: Context, botaoId: String): File {
            val pasta = context.getExternalFilesDir("botoes_imagens") ?: File(context.filesDir, "botoes_imagens")
            if (!pasta.exists()) pasta.mkdirs()
            return File(pasta, "$botaoId.jpg")
        }

        fun criarUriFotoCamera(context: Context, botaoId: String): Uri {
            val photoFile = criarArquivoFotoCamera(context, botaoId)
            return androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                photoFile
            )
        }
    }
}
