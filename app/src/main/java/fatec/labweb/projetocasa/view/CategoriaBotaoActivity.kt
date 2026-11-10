package fatec.labweb.projetocasa.view

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.adapter.ItemBotaoAdapter
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.service.CategoriaService
import fatec.labweb.projetocasa.viewmodel.CategoriaViewModel
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID

open class CategoriaBotaoActivity : BaseDrawerActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_CATEGORIA_ID = "extra_categoria_id"
        const val EXTRA_CATEGORIA_NOME = "extra_categoria_nome"
        const val EXTRA_CATEGORIA_COR = "extra_categoria_cor"
    }

    private val viewModel: CategoriaViewModel by viewModels()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun getContentLayoutId(): Int = R.layout.activity_categoria_botao
    override fun getCurrentMenuItemId(): Int = R.id.menuCategorias

    protected var categoriaId: String = ""
    protected var categoriaNome: String = ""

    private lateinit var rvBotoes: RecyclerView
    private lateinit var adapter: ItemBotaoAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var txtSemBotoes: TextView
    private lateinit var etBuscarBotao: TextInputEditText
    private lateinit var txtTituloCategoria: TextView

    // Players nativos
    private var mediaPlayer: MediaPlayer? = null
    private var previewPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsPronto: Boolean = false

    // Gravação e seleção de mídia
    private var mediaRecorder: MediaRecorder? = null
    private var arquivoAudioGravado: File? = null
    private var uriImagemSelecionada: Uri? = null
    private var uriAudioSelecionado: Uri? = null
    private var isGravando: Boolean = false

    // Referências dinâmicas para views do modal ativo
    private var imgPreviewModalAtivo: ImageView? = null
    private var txtStatusAudioModalAtivo: TextView? = null
    private var btnPararGravacaoAtivo: MaterialButton? = null
    private var btnOuvirPreviaAtivo: MaterialButton? = null
    private var btnGravarAudioAtivo: MaterialButton? = null
    private var progressModalAtivo: ProgressBar? = null

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

    // 1. Seletor de Imagem da Galeria (MIME type "image/*")
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uriImagemSelecionada = uri
            imgPreviewModalAtivo?.let { imgView ->
                Glide.with(this).load(uri).centerCrop().into(imgView)
            }
            Toast.makeText(this, "Foto selecionada da galeria", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Seletor de Áudio Local (MIME type "audio/*")
    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uriAudioSelecionado = uri
            arquivoAudioGravado = null
            txtStatusAudioModalAtivo?.text = "Áudio local selecionado do dispositivo."
            btnOuvirPreviaAtivo?.visibility = View.VISIBLE
            Toast.makeText(this, "Áudio local selecionado", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. Permissão Dinâmica para Gravação de Voz (RECORD_AUDIO)
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            iniciarGravacaoAudioNativa()
        } else {
            Toast.makeText(this, "Permissão de microfone necessária para gravação de voz.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this, this)

        categoriaId = intent.getStringExtra(EXTRA_CATEGORIA_ID) ?: ""
        categoriaNome = intent.getStringExtra(EXTRA_CATEGORIA_NOME) ?: "Categoria"

        initViews()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.carregarBotoes(currentUserId, categoriaId)
    }

    override fun onResume() {
        super.onResume()
        if (categoriaId.isNotBlank()) {
            viewModel.carregarBotoes(currentUserId, categoriaId)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            ttsPronto = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun initViews() {
        txtTituloCategoria = findViewById(R.id.txtTituloCategoria)
        txtTituloCategoria.text = "Botões: $categoriaNome"

        rvBotoes = findViewById(R.id.rvBotoes)
        progressBar = findViewById(R.id.progressBarBotoes)
        txtSemBotoes = findViewById(R.id.txtSemBotoes)
        etBuscarBotao = findViewById(R.id.etBuscarBotao)
    }

    private fun setupRecyclerView() {
        adapter = ItemBotaoAdapter(
            onItemClick = { item ->
                tocarAudioItem(item)
            },
            onPlayAudio = { item ->
                tocarAudioItem(item)
            },
            onEditarClick = { item ->
                mostrarModalEditarBotao(item)
            },
            onExcluirClick = { item ->
                mostrarModalConfirmarExclusao(item)
            }
        )
        rvBotoes.layoutManager = LinearLayoutManager(this)
        rvBotoes.adapter = adapter
    }

    private fun setupListeners() {
        findViewById<MaterialButton>(R.id.btnAdicionarBotao)?.setOnClickListener {
            if (adapter.itemCount >= CategoriaService.MAX_BOTOES_POR_CATEGORIA) {
                Toast.makeText(this, "Limite máximo de ${CategoriaService.MAX_BOTOES_POR_CATEGORIA} botões atingido nesta categoria.", Toast.LENGTH_LONG).show()
            } else {
                mostrarModalCriarBotao()
            }
        }

        etBuscarBotao.doAfterTextChanged { editable ->
            adapter.filtrar(editable?.toString() ?: "")
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading == true) View.VISIBLE else View.GONE
        }

        viewModel.botoes.observe(this) { lista ->
            adapter.atualizarLista(lista)
            val vazia = lista.isNullOrEmpty()
            txtSemBotoes.visibility = if (vazia) View.VISIBLE else View.GONE
            rvBotoes.visibility = if (vazia) View.GONE else View.VISIBLE
        }

        viewModel.errorMessage.observe(this) { erro ->
            if (!erro.isNullOrBlank()) {
                Toast.makeText(this, erro, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.sucessoOperacao.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    protected fun tocarAudioItem(item: CategoriaItem) {
        val audioAlvo = item.audioUrl?.takeIf { it.isNotBlank() }
            ?: item.audio?.takeIf { it.isNotBlank() }
            ?: item.audioPath?.takeIf { it.isNotBlank() }
            ?: item.audio_id?.takeIf { it.isNotBlank() }

        liberarMediaPlayer()

        if (audioAlvo.isNullOrBlank()) {
            if (ttsPronto && textToSpeech != null) {
                textToSpeech?.speak(item.rotulo, TextToSpeech.QUEUE_FLUSH, null, "item_${item.id}")
            }
            Toast.makeText(this, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Reset do MediaPlayer obrigatório para evitar tocar em loop ou manter áudio antigo em cache
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer?.apply {
                if (audioAlvo.startsWith("http://") || audioAlvo.startsWith("https://")) {
                    setDataSource(audioAlvo)
                    setOnPreparedListener {
                        start()
                        Toast.makeText(this@CategoriaBotaoActivity, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                    }
                    setOnCompletionListener { it.reset() }
                    setOnErrorListener { _, _, _ ->
                        liberarMediaPlayer()
                        if (ttsPronto) {
                            textToSpeech?.speak(item.rotulo, TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                        true
                    }
                    prepareAsync()
                } else {
                    val storageRef = if (audioAlvo.contains("/")) {
                        storage.reference.child(audioAlvo)
                    } else {
                        storage.reference.child("audios/$audioAlvo")
                    }

                    Toast.makeText(this@CategoriaBotaoActivity, "Carregando áudio de ${item.rotulo}...", Toast.LENGTH_SHORT).show()
                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                        try {
                            reset()
                            setDataSource(this@CategoriaBotaoActivity, uri)
                            setOnPreparedListener {
                                start()
                                Toast.makeText(this@CategoriaBotaoActivity, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                            }
                            setOnCompletionListener { it.reset() }
                            prepareAsync()
                        } catch (e: Exception) {
                            if (ttsPronto) textToSpeech?.speak(item.rotulo, TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                    }.addOnFailureListener {
                        val localUploads = File(filesDir, "uploads/$audioAlvo")
                        val localDireto = File(filesDir, audioAlvo)
                        val arquivoLocal = when {
                            localUploads.exists() -> localUploads
                            localDireto.exists() -> localDireto
                            else -> null
                        }

                        if (arquivoLocal != null) {
                            try {
                                reset()
                                setDataSource(arquivoLocal.absolutePath)
                                prepare()
                                start()
                                setOnCompletionListener { it.reset() }
                                Toast.makeText(this@CategoriaBotaoActivity, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        } else if (ttsPronto) {
                            textToSpeech?.speak(item.rotulo, TextToSpeech.QUEUE_FLUSH, null, null)
                            Toast.makeText(this@CategoriaBotaoActivity, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            liberarMediaPlayer()
            if (ttsPronto) textToSpeech?.speak(item.rotulo, TextToSpeech.QUEUE_FLUSH, null, null)
            Toast.makeText(this, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun liberarMediaPlayer() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {}
            player.release()
        }
        mediaPlayer = null
    }

    // ==========================================
    // GRAVAÇÃO DE VOZ IN-APP (MEDIARECORDER)
    // ==========================================

    private fun verificarPermissaoEGravarAudio() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            iniciarGravacaoAudioNativa()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun iniciarGravacaoAudioNativa() {
        try {
            val arquivoTemp = File(cacheDir, "gravacao_btn_${System.currentTimeMillis()}.m4a")
            arquivoAudioGravado = arquivoTemp
            uriAudioSelecionado = null

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
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
            txtStatusAudioModalAtivo?.text = "Gravando áudio de voz..."
            btnGravarAudioAtivo?.visibility = View.GONE
            btnPararGravacaoAtivo?.visibility = View.VISIBLE
            btnOuvirPreviaAtivo?.visibility = View.GONE

            Toast.makeText(this, "Gravação iniciada...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            isGravando = false
            txtStatusAudioModalAtivo?.text = "Erro ao iniciar gravação: ${e.message}"
            Toast.makeText(this, "Falha no microfone: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun pararGravacaoAudioNativa() {
        if (!isGravando) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isGravando = false

            txtStatusAudioModalAtivo?.text = "Gravação de voz concluída com sucesso."
            btnGravarAudioAtivo?.visibility = View.VISIBLE
            btnPararGravacaoAtivo?.visibility = View.GONE
            btnOuvirPreviaAtivo?.visibility = View.VISIBLE

            Toast.makeText(this, "Áudio gravado!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            isGravando = false
            mediaRecorder = null
            txtStatusAudioModalAtivo?.text = "Erro ao parar gravação."
        }
    }

    private fun reproduzirPreviaAudio() {
        try {
            previewPlayer?.release()
            previewPlayer = MediaPlayer().apply {
                when {
                    arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                        setDataSource(arquivoAudioGravado!!.absolutePath)
                    }
                    uriAudioSelecionado != null -> {
                        setDataSource(this@CategoriaBotaoActivity, uriAudioSelecionado!!)
                    }
                    else -> return
                }
                prepare()
                start()
                setOnCompletionListener {
                    txtStatusAudioModalAtivo?.text = "Prévia concluída."
                }
            }
            txtStatusAudioModalAtivo?.text = "Reproduzindo prévia..."
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao reproduzir prévia: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun liberarGravadorEPrevia() {
        if (isGravando) {
            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {}
        }
        mediaRecorder?.release()
        mediaRecorder = null
        isGravando = false

        previewPlayer?.release()
        previewPlayer = null
    }

    override fun onStop() {
        super.onStop()
        liberarMediaPlayer()
        liberarGravadorEPrevia()
        textToSpeech?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        liberarMediaPlayer()
        liberarGravadorEPrevia()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    // ==========================================
    // MODAL DE CRIAÇÃO DE BOTÃO COM MÍDIAS
    // ==========================================

    private fun mostrarModalCriarBotao() {
        uriImagemSelecionada = null
        uriAudioSelecionado = null
        arquivoAudioGravado = null

        val dialogView = LayoutInflater.from(this).inflate(R.layout.item_categoria_card, null)
        val txtModalTitle = dialogView.findViewById<TextView>(R.id.txtModalTitle)
        val txtModalSubtitle = dialogView.findViewById<TextView>(R.id.txtModalSubtitle)
        val etTitulo = dialogView.findViewById<EditText>(R.id.etTituloBotao)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.imgPreview)
        val btnGaleria = dialogView.findViewById<View>(R.id.btnEscolherGaleria)
        val btnPadrao = dialogView.findViewById<View>(R.id.btnTirarFoto)
        val btnAudioDispositivo = dialogView.findViewById<View>(R.id.btnSelecionarAudio)
        val btnGravar = dialogView.findViewById<MaterialButton>(R.id.btnGravarAudio)
        val btnParar = dialogView.findViewById<MaterialButton>(R.id.btnPararGravacao)
        val btnPrevia = dialogView.findViewById<MaterialButton>(R.id.btnOuvirPrevia)
        val txtStatusAudio = dialogView.findViewById<TextView>(R.id.txtStatusAudio)
        val progressBarModal = dialogView.findViewById<ProgressBar>(R.id.progressUploadModal)
        val btnCancelar = dialogView.findViewById<View>(R.id.btnCancelarModal)
        val btnSalvar = dialogView.findViewById<MaterialButton>(R.id.btnSalvarModal)

        imgPreviewModalAtivo = imgPreview
        txtStatusAudioModalAtivo = txtStatusAudio
        btnPararGravacaoAtivo = btnParar
        btnOuvirPreviaAtivo = btnPrevia
        btnGravarAudioAtivo = btnGravar
        progressModalAtivo = progressBarModal

        txtModalTitle?.text = "Adicionar Botão"
        txtModalSubtitle?.text = "Categoria: $categoriaNome"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnGaleria?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnPadrao?.setOnClickListener {
            uriImagemSelecionada = null
            imgPreview?.setImageResource(android.R.drawable.ic_menu_camera)
        }

        btnAudioDispositivo?.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        btnGravar?.setOnClickListener {
            verificarPermissaoEGravarAudio()
        }

        btnParar?.setOnClickListener {
            pararGravacaoAudioNativa()
        }

        btnPrevia?.setOnClickListener {
            reproduzirPreviaAudio()
        }

        btnCancelar?.setOnClickListener { dialog.dismiss() }

        btnSalvar?.setOnClickListener {
            val rotulo = etTitulo?.text?.toString()?.trim() ?: ""
            if (rotulo.isBlank()) {
                etTitulo?.error = "Informe o título do botão"
                return@setOnClickListener
            }

            if (isGravando) {
                pararGravacaoAudioNativa()
            }

            btnSalvar.isEnabled = false
            progressBarModal?.visibility = View.VISIBLE

            val botaoId = "btn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)

            fazerUploadMidiasESalvarBotao(
                botaoId = botaoId,
                rotulo = rotulo,
                ordem = adapter.itemCount,
                imagemUrlExistente = null,
                audioUrlExistente = null,
                onComplete = { _, _ ->
                    if (categoriaId.isNotBlank()) {
                        viewModel.carregarBotoes(currentUserId, categoriaId)
                    }
                    adapter.notifyDataSetChanged()
                    dialog.dismiss()
                }
            )
        }

        dialog.setOnDismissListener {
            liberarGravadorEPrevia()
        }

        dialog.show()
    }

    // ==========================================
    // MODAL DE EDIÇÃO DE BOTÃO COM MÍDIAS
    // ==========================================

    private fun mostrarModalEditarBotao(item: CategoriaItem) {
        uriImagemSelecionada = null
        uriAudioSelecionado = null
        arquivoAudioGravado = null

        val dialogView = LayoutInflater.from(this).inflate(R.layout.item_categoria_card, null)
        val txtModalTitle = dialogView.findViewById<TextView>(R.id.txtModalTitle)
        val txtModalSubtitle = dialogView.findViewById<TextView>(R.id.txtModalSubtitle)
        val etTitulo = dialogView.findViewById<EditText>(R.id.etTituloBotao)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.imgPreview)
        val btnGaleria = dialogView.findViewById<View>(R.id.btnEscolherGaleria)
        val btnPadrao = dialogView.findViewById<View>(R.id.btnTirarFoto)
        val btnAudioDispositivo = dialogView.findViewById<View>(R.id.btnSelecionarAudio)
        val btnGravar = dialogView.findViewById<MaterialButton>(R.id.btnGravarAudio)
        val btnParar = dialogView.findViewById<MaterialButton>(R.id.btnPararGravacao)
        val btnPrevia = dialogView.findViewById<MaterialButton>(R.id.btnOuvirPrevia)
        val txtStatusAudio = dialogView.findViewById<TextView>(R.id.txtStatusAudio)
        val progressBarModal = dialogView.findViewById<ProgressBar>(R.id.progressUploadModal)
        val btnCancelar = dialogView.findViewById<View>(R.id.btnCancelarModal)
        val btnSalvar = dialogView.findViewById<MaterialButton>(R.id.btnSalvarModal)

        imgPreviewModalAtivo = imgPreview
        txtStatusAudioModalAtivo = txtStatusAudio
        btnPararGravacaoAtivo = btnParar
        btnOuvirPreviaAtivo = btnPrevia
        btnGravarAudioAtivo = btnGravar
        progressModalAtivo = progressBarModal

        txtModalTitle?.text = "Editar Botão"
        txtModalSubtitle?.text = "Categoria: $categoriaNome"
        etTitulo?.setText(item.rotulo)

        val imgAtual = item.imagem ?: item.imageUri ?: item.imagem_id
        if (!imgAtual.isNullOrBlank()) {
            Glide.with(this).load(imgAtual).placeholder(android.R.drawable.ic_menu_camera).into(imgPreview)
        }

        val audioAtual = item.audio ?: item.audioPath ?: item.audio_id
        if (!audioAtual.isNullOrBlank()) {
            txtStatusAudio?.text = "Áudio vinculado: $audioAtual"
            btnPrevia?.visibility = View.VISIBLE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnGaleria?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnPadrao?.setOnClickListener {
            uriImagemSelecionada = null
            item.imagem = null
            item.imagem_id = null
            imgPreview?.setImageResource(android.R.drawable.ic_menu_camera)
        }

        btnAudioDispositivo?.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        btnGravar?.setOnClickListener {
            verificarPermissaoEGravarAudio()
        }

        btnParar?.setOnClickListener {
            pararGravacaoAudioNativa()
        }

        btnPrevia?.setOnClickListener {
            if (arquivoAudioGravado != null || uriAudioSelecionado != null) {
                reproduzirPreviaAudio()
            } else if (!audioAtual.isNullOrBlank()) {
                tocarAudioItem(item)
            }
        }

        btnCancelar?.setOnClickListener { dialog.dismiss() }

        btnSalvar?.setOnClickListener {
            val novoRotulo = etTitulo?.text?.toString()?.trim() ?: ""
            if (novoRotulo.isBlank()) {
                etTitulo?.error = "Informe o título do botão"
                return@setOnClickListener
            }

            if (isGravando) {
                pararGravacaoAudioNativa()
            }

            btnSalvar.isEnabled = false
            progressBarModal?.visibility = View.VISIBLE

            fazerUploadMidiasESalvarBotao(
                botaoId = item.id,
                rotulo = novoRotulo,
                ordem = item.ordem,
                imagemUrlExistente = item.imageUrl ?: item.imagem ?: item.imageUri,
                audioUrlExistente = item.audioUrl ?: item.audio ?: item.audioPath,
                onComplete = { novaImg, novoAudio ->
                    item.rotulo = novoRotulo
                    if (novaImg.isNotBlank()) {
                        item.imageUrl = novaImg
                        item.imagem = novaImg
                        item.imagem_id = novaImg
                        item.imageUri = novaImg
                    }
                    if (novoAudio.isNotBlank()) {
                        item.audioUrl = novoAudio
                        item.audio = novoAudio
                        item.audio_id = novoAudio
                        item.audioPath = novoAudio
                    }
                    adapter.atualizarItem(item)
                    adapter.notifyDataSetChanged()
                    dialog.dismiss()
                }
            )
        }

        dialog.setOnDismissListener {
            liberarGravadorEPrevia()
        }

        dialog.show()
    }

    // ==========================================
    // UPLOAD PARA FIREBASE STORAGE E FIRESTORE
    // ==========================================

    private fun fazerUploadMidiasESalvarBotao(
        botaoId: String,
        rotulo: String,
        ordem: Int,
        imagemUrlExistente: String?,
        audioUrlExistente: String?,
        onComplete: (novaImageUrl: String, novoAudioUrl: String) -> Unit
    ) {
        var finalImageUrl = imagemUrlExistente ?: ""
        var finalAudioUrl = audioUrlExistente ?: ""

        val storageImgRef = storage.reference.child("imagens/$botaoId.jpg")
        val storageAudioRef = storage.reference.child("audios/$botaoId.m4a")

        fun finalizarPersistencia() {
            val itemAtualizado = CategoriaItem(
                id = botaoId,
                rotulo = rotulo,
                significado = rotulo,
                imagem = finalImageUrl.ifBlank { null },
                imagem_id = finalImageUrl.ifBlank { null },
                imageUrl = finalImageUrl.ifBlank { null },
                audio = finalAudioUrl.ifBlank { null },
                audio_id = finalAudioUrl.ifBlank { null },
                audioUrl = finalAudioUrl.ifBlank { null },
                ordem = ordem,
                defaultGender = GeneroAudio.FEMALE
            )

            viewModel.salvarBotaoComMidia(
                context = this,
                categoriaId = categoriaId,
                item = itemAtualizado,
                imagemUri = null,
                audioUri = null,
                userId = currentUserId
            )

            val updateMap = hashMapOf<String, Any>(
                "id" to botaoId,
                "rotulo" to rotulo,
                "significado" to rotulo,
                "categoria_id" to categoriaId,
                "owner_uid" to currentUserId,
                "ordem" to ordem
            )
            if (finalImageUrl.isNotBlank()) {
                updateMap["imageUrl"] = finalImageUrl
                updateMap["imagem"] = finalImageUrl
                updateMap["imagem_id"] = finalImageUrl
                updateMap["imageUri"] = finalImageUrl
            }
            if (finalAudioUrl.isNotBlank()) {
                updateMap["audioUrl"] = finalAudioUrl
                updateMap["audio"] = finalAudioUrl
                updateMap["audio_id"] = finalAudioUrl
                updateMap["audioPath"] = finalAudioUrl
            }

            firestore.collection("categoria_botao").document(botaoId).set(updateMap)

            Toast.makeText(this, "Botão e mídias salvos com sucesso!", Toast.LENGTH_SHORT).show()
            onComplete(finalImageUrl, finalAudioUrl)
        }

        fun uploadAudio(onAudioPronto: () -> Unit) {
            when {
                // Caso 1: Gravação de voz nativa realizada no app
                arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                    val bytes = arquivoAudioGravado!!.readBytes()
                    storageAudioRef.putBytes(bytes)
                        .addOnSuccessListener {
                            storageAudioRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                                finalAudioUrl = downloadUrl.toString()
                                onAudioPronto()
                            }.addOnFailureListener { onAudioPronto() }
                        }
                        .addOnFailureListener { onAudioPronto() }
                }

                // Caso 2: Arquivo de áudio selecionado da memória do celular
                uriAudioSelecionado != null -> {
                    val stream: InputStream? = contentResolver.openInputStream(uriAudioSelecionado!!)
                    val bytes = stream?.readBytes()
                    stream?.close()

                    if (bytes != null) {
                        storageAudioRef.putBytes(bytes)
                            .addOnSuccessListener {
                                storageAudioRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                                    finalAudioUrl = downloadUrl.toString()
                                    onAudioPronto()
                                }.addOnFailureListener { onAudioPronto() }
                            }
                            .addOnFailureListener { onAudioPronto() }
                    } else {
                        onAudioPronto()
                    }
                }

                else -> onAudioPronto()
            }
        }

        // 1. Upload de Imagem (se selecionada)
        if (uriImagemSelecionada != null) {
            val stream: InputStream? = contentResolver.openInputStream(uriImagemSelecionada!!)
            val bytes = stream?.readBytes()
            stream?.close()

            if (bytes != null) {
                storageImgRef.putBytes(bytes)
                    .addOnSuccessListener {
                        storageImgRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                            finalImageUrl = downloadUrl.toString()
                            uploadAudio { finalizarPersistencia() }
                        }.addOnFailureListener {
                            uploadAudio { finalizarPersistencia() }
                        }
                    }
                    .addOnFailureListener {
                        uploadAudio { finalizarPersistencia() }
                    }
            } else {
                uploadAudio { finalizarPersistencia() }
            }
        } else {
            uploadAudio { finalizarPersistencia() }
        }
    }

    private fun mostrarModalConfirmarExclusao(item: CategoriaItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirmar_exclusao, null)
        val txtMensagem = dialogView.findViewById<TextView>(R.id.txtMensagemExclusao)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarExclusao)
        val btnConfirmar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarExclusao)

        txtMensagem?.text = "Tem certeza de que deseja excluir o botão \"${item.rotulo}\"?"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancelar?.setOnClickListener { dialog.dismiss() }
        btnConfirmar?.setOnClickListener {
            viewModel.removerBotao(currentUserId, categoriaId, item.id)
            firestore.collection("categoria_botao").document(item.id).delete()

            Toast.makeText(this, "Botão \"${item.rotulo}\" excluído com sucesso!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
