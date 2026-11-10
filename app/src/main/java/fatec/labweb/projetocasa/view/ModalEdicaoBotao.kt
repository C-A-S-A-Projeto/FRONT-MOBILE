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
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import java.io.File
import java.util.UUID

/**
 * ModalEdicaoBotao: Modal/Dialog dedicado para criação e edição de botões de comunicação.
 * Implementa preview instantâneo de imagem, gravação in-app de áudio e encadeamento
 * assíncrono estrito: Storage Upload -> DownloadUrl -> Firestore Sobregravação -> Adapter Refresh.
 */
class ModalEdicaoBotao(
    private val activity: Activity,
    private val onAbrirGaleria: (() -> Unit)? = null,
    private val onAbrirAudioLocal: (() -> Unit)? = null
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var dialog: AlertDialog? = null
    private var mediaRecorder: MediaRecorder? = null
    private var previewPlayer: MediaPlayer? = null
    private var isGravando: Boolean = false

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
        val btnPadrao = dialogView.findViewById<View>(R.id.btnTirarFoto)
        val btnDesenhosDrive = dialogView.findViewById<View>(R.id.btnDesenhosDrive)
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
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
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

        btnGaleria?.setOnClickListener {
            onAbrirGaleria?.invoke()
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

        val idParaAudio = itemExistente?.id?.takeIf { it.isNotBlank() }
            ?: ("btn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))

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

        btnCancelar?.setOnClickListener {
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
            val ordem = itemExistente?.ordem ?: ordemPadrao

            salvarComUploadEstrito(
                botaoId = botaoId,
                categoriaId = categoriaId,
                rotulo = rotulo,
                ordem = ordem,
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
     * Aplica preview instantâneo na ImageView do Modal assim que uma imagem for escolhida na galeria.
     */
    fun definirImagemPreview(uri: Uri) {
        uriImagemSelecionada = uri
        imgPreview?.let { iv ->
            iv.clearColorFilter()
            iv.imageTintList = null
            iv.visibility = View.VISIBLE
            iv.setImageURI(uri) // Preview instantâneo antes de salvar
            Glide.with(activity)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .centerCrop()
                .placeholder(R.drawable.ic_default_button)
                .error(R.drawable.ic_default_button)
                .into(iv)
        }
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
     * Fluxo estrito de upload: Imagem -> Áudio -> Cloud Firestore
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
        val storageRefImg = FirebaseStorage.getInstance().reference.child("categorias/imagens/$botaoId.jpg")
        val storageRefAudio = FirebaseStorage.getInstance().reference.child("categorias/audios/$botaoId.m4a")

        // 1. Salvamento Local Obrigatório (filesDir / getExternalFilesDir)
        val localFile = when {
            uriImagemSelecionada != null -> MidiaLocalHelper.salvarImagemLocalmente(activity, botaoId, uriImagemSelecionada!!)
            desenhoSelecionado != null -> MidiaLocalHelper.salvarImagemLocalmente(activity, botaoId, desenhoSelecionado!!.resId)
            else -> MidiaLocalHelper.obterArquivoLocal(activity, botaoId)
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

        fun gravarNoFirestore(finalImageUrl: String, finalAudioUrl: String) {
            val dadosAtualizados = hashMapOf<String, Any>(
                "id" to botaoId,
                "rotulo" to rotulo,
                "nome" to rotulo,
                "significado" to rotulo,
                "categoria_id" to categoriaId,
                "owner_uid" to currentUserId,
                "ordem" to ordem,
                "defaultGender" to GeneroAudio.FEMALE.name,
                "imageUrl" to finalImageUrl,
                "imageUri" to finalImageUrl,
                "imagem" to finalImageUrl,
                "imagem_id" to finalImageUrl,
                "localPath" to (localPath ?: ""),
                "audioUrl" to finalAudioUrl,
                "audioPath" to finalAudioUrl,
                "audio" to finalAudioUrl,
                "audio_id" to finalAudioUrl
            )

            val itemFinal = CategoriaItem(
                id = botaoId,
                rotulo = rotulo,
                significado = rotulo,
                imagem = finalImageUrl.ifBlank { null },
                imagem_id = finalImageUrl.ifBlank { null },
                imageUrl = finalImageUrl.ifBlank { null },
                audio = finalAudioUrl.ifBlank { null },
                audio_id = finalAudioUrl.ifBlank { null },
                audioUrl = finalAudioUrl.ifBlank { null },
                localPath = localPath,
                ordem = ordem,
                defaultGender = GeneroAudio.FEMALE
            )

            // Grava na coleção 'categoria_botao'
            firestore.collection("categoria_botao").document(botaoId).set(dadosAtualizados)
                .addOnSuccessListener {
                    // Sincroniza também no espelho da categoria do usuário se categoriaId presente
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
                                }
                            }
                    }

                    Toast.makeText(activity, "Botão salvo e sincronizado!", Toast.LENGTH_SHORT).show()
                    onSucesso(itemFinal)
                }
                .addOnFailureListener { erro ->
                    btnSalvar?.isEnabled = true
                    progressBar?.visibility = View.GONE
                    Toast.makeText(activity, "Erro ao gravar no Firestore: ${erro.message}", Toast.LENGTH_LONG).show()
                }
        }

        fun uploadAudio(finalImageUrl: String) {
            val audioUriLocal: Uri? = when {
                arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> Uri.fromFile(arquivoAudioGravado)
                uriAudioSelecionado != null -> uriAudioSelecionado
                localAudioFile != null && localAudioFile.exists() -> Uri.fromFile(localAudioFile)
                else -> null
            }

            if (audioUriLocal != null) {
                storageRefAudio.putFile(audioUriLocal).continueWithTask { audioTask ->
                    if (!audioTask.isSuccessful) audioTask.exception?.let { throw it }
                    storageRefAudio.downloadUrl
                }.addOnSuccessListener { newAudioUrl ->
                    gravarNoFirestore(finalImageUrl, newAudioUrl.toString())
                }.addOnFailureListener {
                    // Se o upload falhar, preserva URL existente
                    gravarNoFirestore(finalImageUrl, audioUrlExistente ?: "")
                }
            } else {
                gravarNoFirestore(finalImageUrl, audioUrlExistente ?: "")
            }
        }

        val uriImagemLocal = when {
            uriImagemSelecionada != null -> uriImagemSelecionada
            localFile != null && localFile.exists() -> Uri.fromFile(localFile)
            else -> null
        }

        if (uriImagemLocal != null) {
            storageRefImg.putFile(uriImagemLocal).continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                storageRefImg.downloadUrl
            }.addOnSuccessListener { newImageUrl ->
                uploadAudio(newImageUrl.toString())
            }.addOnFailureListener {
                uploadAudio(desenhoSelecionado?.driveUrl ?: imageUrlExistente ?: "")
            }
        } else {
            uploadAudio(desenhoSelecionado?.driveUrl ?: imageUrlExistente ?: "")
        }
    }

    private fun iniciarGravacao() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        try {
            val arquivoTemp = File(activity.cacheDir, "gravacao_modal_${System.currentTimeMillis()}.m4a")
            arquivoAudioGravado = arquivoTemp
            uriAudioSelecionado = null

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(activity)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(arquivoTemp.absolutePath)
                prepare()
                start()
            }

            isGravando = true
            txtStatusAudio?.text = "Gravando voz..."
            btnGravar?.visibility = View.GONE
            btnParar?.visibility = View.VISIBLE
            btnPrevia?.visibility = View.GONE
            Toast.makeText(activity, "Gravação iniciada...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            isGravando = false
            txtStatusAudio?.text = "Falha ao gravar áudio."
            Toast.makeText(activity, "Erro no microfone: ${e.message}", Toast.LENGTH_SHORT).show()
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
}
