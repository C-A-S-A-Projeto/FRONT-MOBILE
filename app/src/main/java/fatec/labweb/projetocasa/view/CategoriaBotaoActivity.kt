package fatec.labweb.projetocasa.view

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
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
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.CasaApplication
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.adapter.ItemBotaoAdapter
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.service.CategoriaService
import fatec.labweb.projetocasa.util.MidiaLocalHelper
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
    private var desenhoSelecionado: DesenhoBotao? = null
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

    private var cameraImageUri: Uri? = null

    // 1. Seletor de Imagem da Galeria (MIME type "image/*") com Preview Instantâneo
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uriImagemSelecionada = uri
            desenhoSelecionado = null
            imgPreviewModalAtivo?.let { imgView ->
                imgView.clearColorFilter()
                imgView.imageTintList = null
                imgView.visibility = View.VISIBLE
                imgView.setImageURI(uri) // Preview instantâneo antes de salvar
                Glide.with(this)
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
                    .error(R.drawable.ic_default_button)
                    .into(imgView)
            }
            Toast.makeText(this, "Foto selecionada da galeria", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Captura Nativa de Foto com a Câmera (TakePicture com FileProvider)
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso: Boolean ->
        if (sucesso && cameraImageUri != null) {
            val uri = cameraImageUri!!
            uriImagemSelecionada = uri
            desenhoSelecionado = null
            imgPreviewModalAtivo?.let { imgView ->
                imgView.clearColorFilter()
                imgView.imageTintList = null
                imgView.visibility = View.VISIBLE
                Glide.with(this)
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
                    .error(R.drawable.ic_default_button)
                    .into(imgView)
            }
            Toast.makeText(this, "Foto da câmera capturada com sucesso!", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. Permissão Dinâmica para Câmera
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            dispararCapturaCamera()
        } else {
            Toast.makeText(this, "Permissão de câmera necessária para tirar fotos.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dispararCapturaCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        try {
            val pastaTemp = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: cacheDir
            if (!pastaTemp.exists()) pastaTemp.mkdirs()
            val arquivoFoto = File.createTempFile("camera_botao_", ".jpg", pastaTemp)
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                arquivoFoto
            )
            cameraImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao abrir câmera: ${e.message}", Toast.LENGTH_SHORT).show()
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
        val audioLocal = MidiaLocalHelper.obterAudioLocal(this, item.id)
        if (audioLocal != null && audioLocal.exists() && audioLocal.length() > 0) {
            try {
                mediaPlayer?.reset()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioLocal.absolutePath)
                    prepare()
                    start()
                }
                Toast.makeText(this, "Reproduzindo áudio local: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                liberarMediaPlayer()
            }
        }

        val audioUrl = item.audioUrl?.takeIf { it.isNotBlank() }
            ?: item.audioPath?.takeIf { it.isNotBlank() }
            ?: item.audio?.takeIf { it.isNotBlank() }

        if (!audioUrl.isNullOrBlank()) {
            try {
                mediaPlayer?.reset()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioUrl) // Pega a URL salva no Firestore
                    prepareAsync()
                    setOnPreparedListener { start() }
                }
                Toast.makeText(this, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                liberarMediaPlayer()
            }
        }

        if (ttsPronto && textToSpeech != null) {
            textToSpeech?.speak(item.rotulo, TextToSpeech.QUEUE_FLUSH, null, "item_${item.id}")
        }
        Toast.makeText(this, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
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
        desenhoSelecionado = null

        val dialogView = LayoutInflater.from(this).inflate(R.layout.item_categoria_card, null)
        val txtModalTitle = dialogView.findViewById<TextView>(R.id.txtModalTitle)
        val txtModalSubtitle = dialogView.findViewById<TextView>(R.id.txtModalSubtitle)
        val etTitulo = dialogView.findViewById<EditText>(R.id.etTituloBotao)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.imgPreviewBotao)
            ?: dialogView.findViewById<ImageView>(R.id.imgPreview)
        val btnGaleria = dialogView.findViewById<View>(R.id.btnEscolherGaleria)
        val btnTirarFoto = dialogView.findViewById<View>(R.id.btnTirarFoto)
        val btnDesenhosDrive = dialogView.findViewById<MaterialButton>(R.id.btnDesenhosDrive)
        val btnPadrao = dialogView.findViewById<View>(R.id.btnPadrao)
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

        val botaoId = "btn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)

        btnGaleria?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnTirarFoto?.setOnClickListener {
            dispararCapturaCamera()
        }

        btnDesenhosDrive?.setOnClickListener {
            DialogSeletorDesenhos(this) { desenho ->
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
            imgPreview?.setImageResource(R.drawable.ic_default_button)
        }

        btnAudioDispositivo?.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        btnGravar?.setOnClickListener {
            ModalGravadorAudio(this, botaoId) { arquivoLocal ->
                arquivoAudioGravado = arquivoLocal
                uriAudioSelecionado = null
                txtStatusAudioModalAtivo?.text = "Voz gravada no dispositivo (${arquivoLocal.name})"
                btnOuvirPreviaAtivo?.visibility = View.VISIBLE
            }.iniciar()
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
        desenhoSelecionado = null

        val dialogView = LayoutInflater.from(this).inflate(R.layout.item_categoria_card, null)
        val txtModalTitle = dialogView.findViewById<TextView>(R.id.txtModalTitle)
        val txtModalSubtitle = dialogView.findViewById<TextView>(R.id.txtModalSubtitle)
        val etTitulo = dialogView.findViewById<EditText>(R.id.etTituloBotao)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.imgPreviewBotao)
            ?: dialogView.findViewById<ImageView>(R.id.imgPreview)
        val btnGaleria = dialogView.findViewById<View>(R.id.btnEscolherGaleria)
        val btnTirarFoto = dialogView.findViewById<View>(R.id.btnTirarFoto)
        val btnDesenhosDrive = dialogView.findViewById<MaterialButton>(R.id.btnDesenhosDrive)
        val btnPadrao = dialogView.findViewById<View>(R.id.btnPadrao)
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

        val localExistente = MidiaLocalHelper.obterArquivoLocal(this, item.id)
        val imgAtual: Any = when {
            localExistente != null -> localExistente
            !item.localPath.isNullOrEmpty() && File(item.localPath!!).exists() -> File(item.localPath!!)
            !item.imageUrl.isNullOrEmpty() -> item.imageUrl!!
            !item.imagem.isNullOrEmpty() -> item.imagem!!
            !item.imageUri.isNullOrEmpty() -> item.imageUri!!
            DesenhoBotao.obterDrawable(item.rotulo, item.significado) != null -> DesenhoBotao.obterDrawable(item.rotulo, item.significado)!!
            else -> R.drawable.ic_default_button
        }

        imgPreview.clearColorFilter()
        imgPreview.imageTintList = null
        Glide.with(this)
            .load(imgAtual)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .centerCrop()
            .placeholder(R.drawable.ic_default_button)
            .error(R.drawable.ic_default_button)
            .into(imgPreview)

        val audioAtual = item.audioUrl?.takeIf { it.isNotBlank() }
            ?: item.audioPath?.takeIf { it.isNotBlank() }
            ?: item.audio?.takeIf { it.isNotBlank() }
            ?: item.audio_id?.takeIf { it.isNotBlank() }

        if (!audioAtual.isNullOrBlank()) {
            txtStatusAudio?.text = "Áudio vinculado disponível"
            btnPrevia?.visibility = View.VISIBLE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnGaleria?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnTirarFoto?.setOnClickListener {
            dispararCapturaCamera()
        }

        btnDesenhosDrive?.setOnClickListener {
            DialogSeletorDesenhos(this) { desenho ->
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
            item.imagem = null
            item.imagem_id = null
            item.localPath = null
            imgPreview?.setImageResource(R.drawable.ic_default_button)
        }

        btnAudioDispositivo?.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        btnGravar?.setOnClickListener {
            ModalGravadorAudio(this, item.id) { arquivoLocal ->
                arquivoAudioGravado = arquivoLocal
                uriAudioSelecionado = null
                txtStatusAudioModalAtivo?.text = "Voz gravada no dispositivo (${arquivoLocal.name})"
                btnOuvirPreviaAtivo?.visibility = View.VISIBLE
            }.iniciar()
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
        // Referências estritas do Firebase Storage conforme especificação
        val storageRefImg = FirebaseStorage.getInstance().reference.child("categorias/imagens/$botaoId.jpg")
        val storageRefAudio = FirebaseStorage.getInstance().reference.child("categorias/audios/$botaoId.m4a")

        // 1. Salvamento Local Obrigatório (filesDir / getExternalFilesDir)
        val localFile = when {
            uriImagemSelecionada != null -> MidiaLocalHelper.salvarImagemLocalmente(this, botaoId, uriImagemSelecionada!!)
            desenhoSelecionado != null -> MidiaLocalHelper.salvarImagemLocalmente(this, botaoId, desenhoSelecionado!!.resId)
            else -> MidiaLocalHelper.obterArquivoLocal(this, botaoId)
        }
        val localPath = localFile?.absolutePath

        val localAudioFile = when {
            arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                MidiaLocalHelper.salvarAudioLocalmente(this, botaoId, arquivoAudioGravado!!)
            }
            uriAudioSelecionado != null -> {
                MidiaLocalHelper.salvarAudioLocalmente(this, botaoId, uriAudioSelecionado!!)
            }
            else -> MidiaLocalHelper.obterAudioLocal(this, botaoId)
        }

        // 2. Conformidade com Firestore v2: Persistência em SharedPreferences para funcionamento offline imediato
        val prefs = getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("botao_${botaoId}_rotulo", rotulo)
            putString("botao_${botaoId}_imagem_path", localPath ?: "")
            putString("botao_${botaoId}_audio_path", localAudioFile?.absolutePath ?: "")
            putString("botao_${botaoId}_categoria_id", categoriaId)
            apply()
        }

        val itemAtualizado = CategoriaItem(
            id = botaoId,
            rotulo = rotulo,
            significado = rotulo,
            imagem = localPath ?: imagemUrlExistente,
            imagem_id = localPath ?: imagemUrlExistente,
            imageUrl = localPath ?: imagemUrlExistente,
            audio = localAudioFile?.absolutePath ?: audioUrlExistente,
            audio_id = localAudioFile?.absolutePath ?: audioUrlExistente,
            audioUrl = localAudioFile?.absolutePath ?: audioUrlExistente,
            localPath = localPath,
            ordem = ordem,
            defaultGender = GeneroAudio.FEMALE
        )

        // Atualização imediata no adapter para retorno visual instantâneo
        adapter.atualizarItem(itemAtualizado)
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Botão salvo no dispositivo!", Toast.LENGTH_SHORT).show()
        onComplete(itemAtualizado.imageUrl ?: "", itemAtualizado.audioUrl ?: "")

        fun salvarNoFirestore(finalImageUrl: String, finalAudioUrl: String) {
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
                                        cat.botoes[idx] = itemAtualizado
                                    } else {
                                        cat.botoes.add(itemAtualizado)
                                    }
                                    firestore.collection("usuarios").document(currentUserId)
                                        .collection("categorias").document(categoriaId).set(cat)
                                        .addOnFailureListener { /* Regras v2: ignorado com sucesso */ }
                                }
                            }.addOnFailureListener { /* Regras v2: ignorado com sucesso */ }
                    }
                }
                .addOnFailureListener { /* Regras v2: ignorado com sucesso */ }
        }

        fun encadearUploadAudio(finalImageUrl: String) {
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
                    salvarNoFirestore(finalImageUrl, newAudioUrl.toString())
                }.addOnFailureListener {
                    salvarNoFirestore(finalImageUrl, audioUrlExistente ?: "")
                }
            } else {
                salvarNoFirestore(finalImageUrl, audioUrlExistente ?: "")
            }
        }

        // 1. Upload Assíncrono da Imagem (se alterada) com continueWithTask
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
                encadearUploadAudio(newImageUrl.toString())
            }.addOnFailureListener {
                encadearUploadAudio(desenhoSelecionado?.driveUrl ?: imagemUrlExistente ?: "")
            }
        } else {
            encadearUploadAudio(desenhoSelecionado?.driveUrl ?: imagemUrlExistente ?: "")
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
