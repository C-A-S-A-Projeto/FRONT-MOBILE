package fatec.labweb.projetocasa.view

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
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
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import fatec.labweb.projetocasa.util.SoundManager
import java.io.File
import java.util.UUID

/**
 * ModalEdicaoBotao: Modal/Dialog dedicado para criação e edição de botões de comunicação.
 * Suporta seleção dual de imagem (Galeria + Câmera), persistência física local de fotos e áudios,
 * conformidade com as regras v2 do Cloud Firestore e refresh visual instantâneo no Adapter.
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
    private var isGravando: Boolean = false

    var botaoIdAtivo: String = ""
    var arquivoFotoCamera: File? = null
    var uriImagemSelecionada: Uri? = null
    var uriAudioSelecionado: Uri? = null
    var arquivoAudioGravado: File? = null
    var desenhoSelecionado: DesenhoBotao? = null

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

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.item_categoria_card, null)
        val txtModalTitle = dialogView.findViewById<TextView>(R.id.txtModalTitle)
        val txtModalSubtitle = dialogView.findViewById<TextView>(R.id.txtModalSubtitle)
        val etTitulo = dialogView.findViewById<EditText>(R.id.etTituloBotao)
        imgPreview = dialogView.findViewById<ImageView>(R.id.imgPreviewBotao)
            ?: dialogView.findViewById<ImageView>(R.id.imgPreview)

        val btnGaleria = dialogView.findViewById<View>(R.id.btnEscolherGaleria)
        val btnTirarFoto = dialogView.findViewById<View>(R.id.btnTirarFoto)
        val btnDesenhosDrive = dialogView.findViewById<View>(R.id.btnDesenhosDrive)
        val btnPadrao = dialogView.findViewById<View>(R.id.btnPadrao)

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

            val audioAtual = itemExistente.audioUrl?.takeIf { it.isNotBlank() }
                ?: itemExistente.audioPath?.takeIf { it.isNotBlank() }
                ?: itemExistente.audio?.takeIf { it.isNotBlank() }

            if (!audioAtual.isNullOrBlank()) {
                txtStatusAudio?.text = "Áudio vinculado disponível."
                btnPrevia?.visibility = View.VISIBLE
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        val idParaAudio = itemExistente?.id?.takeIf { it.isNotBlank() }
            ?: ("btn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
        botaoIdAtivo = idParaAudio
        arquivoFotoCamera = null

        // Configuração do Seletor de Slot / Posição (Botão 1..5)
        val spnSlotBotao = dialogView.findViewById<Spinner>(R.id.spnSlotBotao)
        val listaBotoesExistentes = if (categoriaId.isNotBlank()) {
            MidiaLocalHelper.carregarBotoesCategoriaLocalmente(activity, categoriaId)
        } else {
            emptyList()
        }
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

        val desenhosDisponiveis = DesenhoBotao.values().toList()
        val nomesDesenhos = desenhosDisponiveis.map { "${it.titulo} (${it.categoriaPadrao.uppercase()})" }
        val adapterDesenhos = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, nomesDesenhos)
        spnAcaoPredefinida?.adapter = adapterDesenhos

        if (itemExistente != null) {
            val idxD = desenhosDisponiveis.indexOfFirst {
                it.id == itemExistente.imagem || it.titulo.equals(itemExistente.rotulo, true)
            }
            if (idxD != -1) {
                spnAcaoPredefinida?.setSelection(idxD)
                toggleModoAcao?.check(R.id.btnModoAcaoPredefinida)
                containerAcaoPredefinida?.visibility = View.VISIBLE
            } else {
                toggleModoAcao?.check(R.id.btnModoAcaoPersonalizada)
                containerAcaoPredefinida?.visibility = View.GONE
            }
        } else {
            toggleModoAcao?.check(R.id.btnModoAcaoPredefinida)
            containerAcaoPredefinida?.visibility = View.VISIBLE
            if (desenhosDisponiveis.isNotEmpty()) {
                val d = desenhosDisponiveis[0]
                desenhoSelecionado = d
                etTitulo?.setText(d.titulo.split("/").first().trim())
                imgPreview?.setImageResource(d.resId)
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
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        toggleModoAcao?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnModoAcaoPredefinida) {
                    containerAcaoPredefinida?.visibility = View.VISIBLE
                    val pos = spnAcaoPredefinida?.selectedItemPosition ?: 0
                    if (pos in desenhosDisponiveis.indices) {
                        val d = desenhosDisponiveis[pos]
                        desenhoSelecionado = d
                        etTitulo?.setText(d.titulo.split("/").first().trim())
                        imgPreview?.setImageResource(d.resId)
                    }
                } else {
                    containerAcaoPredefinida?.visibility = View.GONE
                    desenhoSelecionado = null
                }
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
            DialogSeletorDesenhos(activity) { desenho ->
                desenhoSelecionado = desenho
                uriImagemSelecionada = null
                imgPreview?.clearColorFilter()
                imgPreview?.imageTintList = null
                imgPreview?.setImageResource(desenho.resId)
            }.exibir()
        }

        btnPadrao?.setOnClickListener {
            uriImagemSelecionada = null
            desenhoSelecionado = null
            itemExistente?.localPath = null
            imgPreview?.setImageResource(R.drawable.ic_default_button)
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

        btnPrevia?.setOnClickListener {
            reproduzirPrevia(itemExistente?.audioUrl, idParaAudio)
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
            val rotulo = etTitulo?.text?.toString()?.trim() ?: ""
            if (rotulo.isBlank()) {
                etTitulo?.error = "Informe o título do botão"
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

        dialog?.setOnDismissListener {
            liberarRecursos()
        }

        dialog?.show()
    }

    /**
     * Aplica preview instantâneo na ImageView do Modal assim que uma imagem for escolhida na galeria ou capturada na câmera.
     * Salva a referência física local caso venha de arquivo direto da câmera (FileProvider).
     */
    fun definirImagemPreview(uri: Uri, localFile: File? = null) {
        uriImagemSelecionada = uri
        desenhoSelecionado = null
        if (localFile != null && localFile.exists()) {
            arquivoFotoCamera = localFile
        }
        imgPreview?.let { iv ->
            iv.clearColorFilter()
            iv.imageTintList = null
            iv.visibility = View.VISIBLE
            val imageSource: Any = if (localFile != null && localFile.exists()) localFile else uri
            Glide.with(activity)
                .load(imageSource)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .centerCrop()
                .placeholder(R.drawable.ic_default_button)
                .error(R.drawable.ic_default_button)
                .into(iv)
        }
    }

    /**
     * Sobrecarga de compatibilidade para receber apenas a Uri da galeria.
     */
    fun definirImagemPreview(uri: Uri) {
        definirImagemPreview(uri, null)
    }

    /**
     * Registra o áudio selecionado da memória do aparelho no modal.
     */
    fun definirAudioSelecionado(uri: Uri) {
        uriAudioSelecionado = uri
        arquivoAudioGravado = null
        txtStatusAudio?.text = "Áudio local selecionado do dispositivo."
        btnPrevia?.visibility = View.VISIBLE
    }

    /**
     * Persistência física 100% local no dispositivo e sincronização em nuvem tolerante a regras v2 do Firestore.
     */
    private fun salvarComUploadEstrito(
        botaoId: String,
        categoriaId: String,
        rotulo: String,
        ordem: Int,
        imageUrlExistente: String?,
        audioUrlExistente: String?,
        onSucesso: (CategoriaItem) -> Unit
    ) {
        // 1. Salvamento Local Obrigatório no diretório getExternalFilesDir("botoes_imagens")
        val localFile = when {
            arquivoFotoCamera != null && arquivoFotoCamera!!.exists() && arquivoFotoCamera!!.length() > 0 -> {
                arquivoFotoCamera
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

        // Salvamento do Áudio no getExternalFilesDir("botoes_audios")
        val localAudioFile = when {
            arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                MidiaLocalHelper.salvarAudioLocalmente(activity, botaoId, arquivoAudioGravado!!)
            }
            uriAudioSelecionado != null -> {
                MidiaLocalHelper.salvarAudioLocalmente(activity, botaoId, uriAudioSelecionado!!)
            }
            else -> MidiaLocalHelper.obterAudioLocal(activity, botaoId)
        }

        // 2. Conformidade com Firestore v2: Persistência em SharedPreferences para funcionamento offline resiliente
        val prefs = activity.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("botao_${botaoId}_rotulo", rotulo)
            putString("botao_${botaoId}_imagem_path", localPath ?: "")
            putString("botao_${botaoId}_audio_path", localAudioFile?.absolutePath ?: "")
            putString("botao_${botaoId}_categoria_id", categoriaId)
            apply()
        }

        val finalImg = localPath ?: imageUrlExistente
        val finalAudio = localAudioFile?.absolutePath ?: audioUrlExistente

        val itemFinal = CategoriaItem(
            id = botaoId,
            rotulo = rotulo,
            significado = rotulo,
            imagem = finalImg?.ifBlank { null },
            imagem_id = finalImg?.ifBlank { null },
            imageUrl = finalImg?.ifBlank { null },
            audio = finalAudio?.ifBlank { null },
            audio_id = finalAudio?.ifBlank { null },
            audioUrl = finalAudio?.ifBlank { null },
            localPath = localPath,
            ordem = ordem,
            defaultGender = GeneroAudio.FEMALE
        )

        // 3. Persistência Física Local na Lista da Categoria em formato JSON (filesDir e SharedPreferences)
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

        // 4. Notificação imediata para renderização instantânea no Adapter
        Toast.makeText(activity, "Botão salvo no dispositivo!", Toast.LENGTH_SHORT).show()
        onSucesso(itemFinal)

        // 4. Sincronização em nuvem resiliente em background (sem falhar a operação local caso bloqueado pelas regras v2)
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
                "defaultGender" to GeneroAudio.FEMALE.name,
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
                                        .addOnFailureListener { /* Regras v2 de leitura: ignorado com sucesso */ }
                                }
                            }.addOnFailureListener { /* Regras v2: ignorado com sucesso */ }
                    }
                }
                .addOnFailureListener { /* Regras v2 de permissão: ignorado com sucesso */ }
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

    private fun reproduzirPrevia(audioExistenteUrl: String?, botaoId: String = "") {
        try {
            val audioLocal = if (botaoId.isNotBlank()) MidiaLocalHelper.obterAudioLocal(activity, botaoId) else null
            previewPlayer?.release()
            previewPlayer = MediaPlayer().apply {
                when {
                    arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                        setDataSource(arquivoAudioGravado!!.absolutePath)
                    }
                    uriAudioSelecionado != null -> {
                        setDataSource(activity, uriAudioSelecionado!!)
                    }
                    audioLocal != null && audioLocal.exists() -> {
                        setDataSource(audioLocal.absolutePath)
                    }
                    !audioExistenteUrl.isNullOrBlank() -> {
                        setDataSource(audioExistenteUrl)
                    }
                    else -> return
                }
                prepare()
                start()
                setOnCompletionListener {
                    txtStatusAudio?.text = "Prévia concluída."
                }
            }
            txtStatusAudio?.text = "Reproduzindo prévia..."
        } catch (e: Exception) {
            Toast.makeText(activity, "Erro ao reproduzir prévia: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun liberarRecursos() {
        if (isGravando) {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
        }
        mediaRecorder?.release()
        mediaRecorder = null
        isGravando = false

        previewPlayer?.release()
        previewPlayer = null
    }

    fun fechar() {
        liberarRecursos()
        dialog?.dismiss()
        dialog = null
    }

    companion object {
        /**
         * Cria e retorna a referência para o arquivo físico em getExternalFilesDir("botoes_imagens")/${botaoId}.jpg
         */
        fun criarArquivoFotoCamera(context: Context, botaoId: String): File {
            val pasta = context.getExternalFilesDir("botoes_imagens") ?: File(context.filesDir, "botoes_imagens")
            if (!pasta.exists()) pasta.mkdirs()
            return File(pasta, "$botaoId.jpg")
        }

        /**
         * Retorna a Uri FileProvider vinculada diretamente ao arquivo físico local para o TakePicture.
         */
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
