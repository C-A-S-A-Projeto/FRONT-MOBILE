package fatec.labweb.projetocasa.view

import android.Manifest
import android.content.Intent
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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.adapter.CategoriaAdapter
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import fatec.labweb.projetocasa.viewmodel.CategoriaViewModel
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID

open class CategoriaGeralActivity : BaseDrawerActivity(), TextToSpeech.OnInitListener {

    private val viewModel: CategoriaViewModel by viewModels()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun getContentLayoutId(): Int = R.layout.activity_categoria_item
    override fun getCurrentMenuItemId(): Int = R.id.menuCategorias

    private lateinit var rvCategorias: RecyclerView
    private lateinit var adapter: CategoriaAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var txtSemCategorias: TextView
    private lateinit var etBuscarCategoria: TextInputEditText

    // Instância gerenciada do MediaPlayer Nativo e TextToSpeech
    private var mediaPlayer: MediaPlayer? = null
    private var previewPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady: Boolean = false

    // Gravação In-App (MediaRecorder) e seleção de arquivos
    private var mediaRecorder: MediaRecorder? = null
    private var arquivoAudioGravado: File? = null
    private var uriImagemSelecionada: Uri? = null
    private var uriAudioSelecionado: Uri? = null
    private var desenhoSelecionado: DesenhoBotao? = null
    private var isGravando: Boolean = false

    // Referências dinâmicas para views do modal ativo
    private var ivPreviewModalAtivo: ImageView? = null
    private var txtStatusAudioModalAtivo: TextView? = null
    private var btnPararGravacaoAtivo: MaterialButton? = null
    private var btnOuvirPreviaAtivo: MaterialButton? = null
    private var btnGravarAudioAtivo: MaterialButton? = null
    private var progressModalAtivo: ProgressBar? = null

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

    // 1. Seletor Nativo de Imagem da Galeria com Preview Instantâneo antes do upload
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uriImagemSelecionada = uri
            ivPreviewModalAtivo?.let { imgView ->
                imgView.clearColorFilter()
                imgView.imageTintList = null
                imgView.visibility = View.VISIBLE
                imgView.setImageURI(uri) // Preview instantâneo nativo
                Glide.with(this)
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
                    .error(R.drawable.ic_default_button)
                    .into(imgView)
            }
            Toast.makeText(this, "Imagem selecionada com sucesso", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Seletor Nativo de Áudio do Dispositivo (MIME type "audio/*")
    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uriAudioSelecionado = uri
            arquivoAudioGravado = null
            txtStatusAudioModalAtivo?.text = "Áudio local selecionado do dispositivo."
            btnOuvirPreviaAtivo?.visibility = View.VISIBLE
            Toast.makeText(this, "Arquivo de áudio selecionado", Toast.LENGTH_SHORT).show()
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

        initViews()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.carregarDados(currentUserId)
    }

    override fun onResume() {
        super.onResume()
        viewModel.carregarDados(currentUserId)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun initViews() {
        rvCategorias = findViewById(R.id.rvCategorias)
        progressBar = findViewById(R.id.progressBarCategorias)
        txtSemCategorias = findViewById(R.id.txtSemCategorias)
        etBuscarCategoria = findViewById(R.id.etBuscarCategoria)
    }

    private fun setupRecyclerView() {
        adapter = CategoriaAdapter(
            todasCategorias = emptyList(),
            onAcessarClick = { categoria ->
                abrirBotoesDaCategoria(categoria)
            },
            onPlayAudio = { categoria ->
                tocarAudioCategoria(categoria)
            },
            onEditarClick = { categoria ->
                mostrarModalEditarCategoria(categoria)
            },
            onExcluirClick = { categoria ->
                mostrarModalConfirmarExclusao(categoria)
            }
        )
        rvCategorias.layoutManager = LinearLayoutManager(this)
        rvCategorias.adapter = adapter
    }

    private fun setupListeners() {
        findViewById<MaterialButton>(R.id.btnAdicionarCategoria)?.setOnClickListener {
            mostrarModalCriarCategoria()
        }

        etBuscarCategoria.doAfterTextChanged { editable ->
            adapter.filtrar(editable?.toString() ?: "")
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading == true) View.VISIBLE else View.GONE
        }

        viewModel.categorias.observe(this) { lista ->
            adapter.atualizarDados(lista)
            val vazia = lista.isNullOrEmpty()
            txtSemCategorias.visibility = if (vazia) View.VISIBLE else View.GONE
            rvCategorias.visibility = if (vazia) View.GONE else View.VISIBLE
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

    // ==========================================
    // REPRODUÇÃO DE ÁUDIO REAL (REGRA 3)
    // ==========================================

    private fun tocarAudioCategoria(categoria: Categoria) {
        val audioLocal = MidiaLocalHelper.obterAudioLocal(this, categoria.id)
        if (audioLocal != null && audioLocal.exists() && audioLocal.length() > 0) {
            try {
                mediaPlayer?.reset()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioLocal.absolutePath)
                    prepare()
                    start()
                }
                Toast.makeText(this, "Reproduzindo áudio local: ${categoria.nome}", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                liberarMediaPlayer()
            }
        }

        val audioUrl = categoria.audioUrl?.takeIf { it.isNotBlank() }
            ?: categoria.audioPath?.takeIf { it.isNotBlank() }

        if (!audioUrl.isNullOrBlank()) {
            try {
                mediaPlayer?.reset()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioUrl) // Pega a URL salva no Firestore
                    prepareAsync()
                    setOnPreparedListener { start() }
                }
                Toast.makeText(this, "Reproduzindo áudio: ${categoria.nome}", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                liberarMediaPlayer()
            }
        }

        if (ttsReady && textToSpeech != null) {
            textToSpeech?.speak("Categoria ${categoria.nome}", TextToSpeech.QUEUE_FLUSH, null, "cat_${categoria.id}")
        }
        Toast.makeText(this, "Reproduzindo: ${categoria.nome}", Toast.LENGTH_SHORT).show()
    }

    fun tocarAudioItem(item: CategoriaItem) {
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

        if (ttsReady && textToSpeech != null) {
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
    // GRAVAÇÃO DE ÁUDIO IN-APP (MEDIARECORDER)
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
            val arquivoTemp = File(cacheDir, "gravacao_cat_${System.currentTimeMillis()}.m4a")
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
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(arquivoTemp.absolutePath)
                prepare()
                start()
            }

            isGravando = true
            txtStatusAudioModalAtivo?.text = "Gravando áudio... fale agora."
            btnPararGravacaoAtivo?.visibility = View.VISIBLE
            btnGravarAudioAtivo?.visibility = View.GONE
            btnOuvirPreviaAtivo?.visibility = View.GONE
            Toast.makeText(this, "Gravação iniciada...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            isGravando = false
            Toast.makeText(this, "Erro ao iniciar gravação: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun pararGravacaoAudioNativa() {
        if (!isGravando) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isGravando = false

            txtStatusAudioModalAtivo?.text = "Voz gravada com sucesso!"
            btnPararGravacaoAtivo?.visibility = View.GONE
            btnGravarAudioAtivo?.visibility = View.VISIBLE
            btnOuvirPreviaAtivo?.visibility = View.VISIBLE
            Toast.makeText(this, "Gravação concluída!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            isGravando = false
            mediaRecorder?.release()
            mediaRecorder = null
            Toast.makeText(this, "Erro ao parar gravação: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reproduzirPreviaAudio() {
        liberarPreviewPlayer()
        try {
            previewPlayer = MediaPlayer().apply {
                when {
                    arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                        setDataSource(arquivoAudioGravado!!.absolutePath)
                    }
                    uriAudioSelecionado != null -> {
                        setDataSource(this@CategoriaGeralActivity, uriAudioSelecionado!!)
                    }
                    else -> return
                }
                prepare()
                start()
                setOnCompletionListener {
                    liberarPreviewPlayer()
                }
            }
            Toast.makeText(this, "Ouvindo prévia do áudio...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            liberarPreviewPlayer()
            Toast.makeText(this, "Erro ao reproduzir prévia: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun liberarPreviewPlayer() {
        previewPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {}
            player.release()
        }
        previewPlayer = null
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
        liberarPreviewPlayer()
    }

    private fun abrirBotoesDaCategoria(categoria: Categoria) {
        val intent = Intent(this, CategoriaBotaoActivity::class.java).apply {
            putExtra(CategoriaBotaoActivity.EXTRA_CATEGORIA_ID, categoria.id)
            putExtra(CategoriaBotaoActivity.EXTRA_CATEGORIA_NOME, categoria.nome)
            putExtra(CategoriaBotaoActivity.EXTRA_CATEGORIA_COR, categoria.cor)
        }
        startActivity(intent)
    }

    // ==========================================
    // MODAL DE CRIAÇÃO COM SUPORTE A MÍDIAS
    // ==========================================

    private fun mostrarModalCriarCategoria() {
        uriImagemSelecionada = null
        uriAudioSelecionado = null
        arquivoAudioGravado = null
        desenhoSelecionado = null

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_form_categoria, null)
        val tvModalTitle = dialogView.findViewById<TextView>(R.id.tvModalTitle)
        val etCategoryName = dialogView.findViewById<EditText>(R.id.etCategoryName)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.ivCategoryPreview)
        val btnFotoGaleria = dialogView.findViewById<View>(R.id.btnSelectCategoryPhoto)
        val btnDesenhosDrive = dialogView.findViewById<View>(R.id.btnSelectCategoryDrawing)
        val btnDefaultImg = dialogView.findViewById<View>(R.id.btnDefaultCategoryImg)
        val btnAudioDispositivo = dialogView.findViewById<View>(R.id.btnSelectCategoryAudio)
        val btnGravarAudio = dialogView.findViewById<MaterialButton>(R.id.btnRecordCategoryAudio)
        val btnPararGravacao = dialogView.findViewById<MaterialButton>(R.id.btnStopCategoryAudio)
        val btnPreviaAudio = dialogView.findViewById<MaterialButton>(R.id.btnPreviewCategoryAudio)
        val txtStatusAudio = dialogView.findViewById<TextView>(R.id.txtCategoryAudioStatus)
        val progressBarModal = dialogView.findViewById<ProgressBar>(R.id.progressCategoryModal)
        val btnSaveCategory = dialogView.findViewById<MaterialButton>(R.id.btnSaveCategory)

        ivPreviewModalAtivo = ivPreview
        txtStatusAudioModalAtivo = txtStatusAudio
        btnPararGravacaoAtivo = btnPararGravacao
        btnOuvirPreviaAtivo = btnPreviaAudio
        btnGravarAudioAtivo = btnGravarAudio
        progressModalAtivo = progressBarModal

        tvModalTitle?.text = "Criar Nova Categoria"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val catId = "cat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)

        btnFotoGaleria?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnDesenhosDrive?.setOnClickListener {
            DialogSeletorDesenhos(this) { desenho ->
                desenhoSelecionado = desenho
                uriImagemSelecionada = null
                ivPreview?.clearColorFilter()
                ivPreview?.imageTintList = null
                ivPreview?.setImageResource(desenho.resId)
            }.exibir()
        }

        btnDefaultImg?.setOnClickListener {
            uriImagemSelecionada = null
            desenhoSelecionado = null
            ivPreview?.setImageResource(R.drawable.ic_default_button)
        }

        btnAudioDispositivo?.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        btnGravarAudio?.setOnClickListener {
            ModalGravadorAudio(this, catId) { arquivoLocal ->
                arquivoAudioGravado = arquivoLocal
                uriAudioSelecionado = null
                txtStatusAudio?.text = "Voz gravada no dispositivo (${arquivoLocal.name})"
                btnPreviaAudio?.visibility = View.VISIBLE
            }.iniciar()
        }

        btnPararGravacao?.setOnClickListener {
            pararGravacaoAudioNativa()
        }

        btnPreviaAudio?.setOnClickListener {
            reproduzirPreviaAudio()
        }

        btnSaveCategory?.setOnClickListener {
            val nome = etCategoryName?.text?.toString()?.trim() ?: ""
            if (nome.isBlank()) {
                etCategoryName?.error = "Informe o nome da categoria"
                return@setOnClickListener
            }

            if (isGravando) {
                pararGravacaoAudioNativa()
            }

            btnSaveCategory.isEnabled = false
            progressBarModal?.visibility = View.VISIBLE

            val slug = nome.lowercase().replace(" ", "_")

            fazerUploadMidiasESalvarCategoria(
                categoriaId = catId,
                nome = nome,
                slug = slug,
                cor = "#2563EB",
                ordem = adapter.itemCount,
                imagemUrlExistente = null,
                audioUrlExistente = null,
                onComplete = { _, _ ->
                    viewModel.carregarDados(currentUserId)
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
    // MODAL DE EDIÇÃO COM SUPORTE A MÍDIAS
    // ==========================================

    private fun mostrarModalEditarCategoria(categoria: Categoria) {
        uriImagemSelecionada = null
        uriAudioSelecionado = null
        arquivoAudioGravado = null
        desenhoSelecionado = null

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_form_categoria, null)
        val tvModalTitle = dialogView.findViewById<TextView>(R.id.tvModalTitle)
        val etCategoryName = dialogView.findViewById<EditText>(R.id.etCategoryName)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.ivCategoryPreview)
        val btnFotoGaleria = dialogView.findViewById<View>(R.id.btnSelectCategoryPhoto)
        val btnDesenhosDrive = dialogView.findViewById<View>(R.id.btnSelectCategoryDrawing)
        val btnDefaultImg = dialogView.findViewById<View>(R.id.btnDefaultCategoryImg)
        val btnAudioDispositivo = dialogView.findViewById<View>(R.id.btnSelectCategoryAudio)
        val btnGravarAudio = dialogView.findViewById<MaterialButton>(R.id.btnRecordCategoryAudio)
        val btnPararGravacao = dialogView.findViewById<MaterialButton>(R.id.btnStopCategoryAudio)
        val btnPreviaAudio = dialogView.findViewById<MaterialButton>(R.id.btnPreviewCategoryAudio)
        val txtStatusAudio = dialogView.findViewById<TextView>(R.id.txtCategoryAudioStatus)
        val progressBarModal = dialogView.findViewById<ProgressBar>(R.id.progressCategoryModal)
        val btnSaveCategory = dialogView.findViewById<MaterialButton>(R.id.btnSaveCategory)

        ivPreviewModalAtivo = ivPreview
        txtStatusAudioModalAtivo = txtStatusAudio
        btnPararGravacaoAtivo = btnPararGravacao
        btnOuvirPreviaAtivo = btnPreviaAudio
        btnGravarAudioAtivo = btnGravarAudio
        progressModalAtivo = progressBarModal

        tvModalTitle?.text = "Editar Categoria"
        etCategoryName?.setText(categoria.nome)

        val localExistente = MidiaLocalHelper.obterArquivoLocal(this, categoria.id)
        val imgAlvo: Any = when {
            localExistente != null -> localExistente
            !categoria.localPath.isNullOrEmpty() && File(categoria.localPath!!).exists() -> File(categoria.localPath!!)
            !categoria.imageUrl.isNullOrEmpty() -> categoria.imageUrl!!
            !categoria.imageUri.isNullOrEmpty() -> categoria.imageUri!!
            DesenhoBotao.obterDrawable(categoria.nome, categoria.slug) != null -> DesenhoBotao.obterDrawable(categoria.nome, categoria.slug)!!
            else -> R.drawable.ic_default_button
        }

        ivPreview?.clearColorFilter()
        ivPreview?.imageTintList = null
        Glide.with(this)
            .load(imgAlvo)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .placeholder(R.drawable.ic_default_button)
            .error(R.drawable.ic_default_button)
            .into(ivPreview)

        val audioAlvo = categoria.audioUrl ?: categoria.audioPath
        if (!audioAlvo.isNullOrBlank()) {
            txtStatusAudio?.text = "Áudio vinculado disponível"
            btnPreviaAudio?.visibility = View.VISIBLE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnFotoGaleria?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnDesenhosDrive?.setOnClickListener {
            DialogSeletorDesenhos(this) { desenho ->
                desenhoSelecionado = desenho
                uriImagemSelecionada = null
                ivPreview?.clearColorFilter()
                ivPreview?.imageTintList = null
                ivPreview?.setImageResource(desenho.resId)
            }.exibir()
        }

        btnDefaultImg?.setOnClickListener {
            uriImagemSelecionada = null
            desenhoSelecionado = null
            categoria.imageUri = null
            categoria.imageUrl = null
            categoria.localPath = null
            ivPreview?.setImageResource(R.drawable.ic_default_button)
        }

        btnAudioDispositivo?.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        btnGravarAudio?.setOnClickListener {
            ModalGravadorAudio(this, categoria.id) { arquivoLocal ->
                arquivoAudioGravado = arquivoLocal
                uriAudioSelecionado = null
                txtStatusAudio?.text = "Voz gravada no dispositivo (${arquivoLocal.name})"
                btnPreviaAudio?.visibility = View.VISIBLE
            }.iniciar()
        }

        btnPararGravacao?.setOnClickListener {
            pararGravacaoAudioNativa()
        }

        btnPreviaAudio?.setOnClickListener {
            if (arquivoAudioGravado != null || uriAudioSelecionado != null) {
                reproduzirPreviaAudio()
            } else if (!categoria.audioUrl.isNullOrBlank() || !categoria.audioPath.isNullOrBlank()) {
                tocarAudioCategoria(categoria)
            }
        }

        btnSaveCategory?.setOnClickListener {
            val novoNome = etCategoryName?.text?.toString()?.trim() ?: ""
            if (novoNome.isBlank()) {
                etCategoryName?.error = "Informe o nome da categoria"
                return@setOnClickListener
            }

            if (isGravando) {
                pararGravacaoAudioNativa()
            }

            btnSaveCategory.isEnabled = false
            progressBarModal?.visibility = View.VISIBLE

            val novoSlug = novoNome.lowercase().replace(" ", "_")

            fazerUploadMidiasESalvarCategoria(
                categoriaId = categoria.id,
                nome = novoNome,
                slug = novoSlug,
                cor = categoria.cor,
                ordem = categoria.ordem,
                imagemUrlExistente = categoria.imageUrl ?: categoria.imageUri,
                audioUrlExistente = categoria.audioUrl ?: categoria.audioPath,
                onComplete = { novaImg, novoAudio ->
                    categoria.nome = novoNome
                    if (novaImg.isNotBlank()) {
                        categoria.imageUrl = novaImg
                        categoria.imageUri = novaImg
                    }
                    if (novoAudio.isNotBlank()) {
                        categoria.audioUrl = novoAudio
                        categoria.audioPath = novoAudio
                    }
                    adapter.atualizarItem(categoria)
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

    private fun fazerUploadMidiasESalvarCategoria(
        categoriaId: String,
        nome: String,
        slug: String,
        cor: String,
        ordem: Int,
        imagemUrlExistente: String?,
        audioUrlExistente: String?,
        onComplete: (novaImagemUrl: String, novoAudioUrl: String) -> Unit
    ) {
        val storageRefImg = FirebaseStorage.getInstance().reference.child("categorias/imagens/$categoriaId.jpg")
        val storageRefAudio = FirebaseStorage.getInstance().reference.child("categorias/audios/$categoriaId.m4a")

        // 1. Salvamento Local Obrigatório (filesDir / getExternalFilesDir)
        val localFile = when {
            uriImagemSelecionada != null -> MidiaLocalHelper.salvarImagemLocalmente(this, categoriaId, uriImagemSelecionada!!)
            desenhoSelecionado != null -> MidiaLocalHelper.salvarImagemLocalmente(this, categoriaId, desenhoSelecionado!!.resId)
            else -> MidiaLocalHelper.obterArquivoLocal(this, categoriaId)
        }
        val localPath = localFile?.absolutePath

        val localAudioFile = when {
            arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                MidiaLocalHelper.salvarAudioLocalmente(this, categoriaId, arquivoAudioGravado!!)
            }
            uriAudioSelecionado != null -> {
                MidiaLocalHelper.salvarAudioLocalmente(this, categoriaId, uriAudioSelecionado!!)
            }
            else -> MidiaLocalHelper.obterAudioLocal(this, categoriaId)
        }

        fun salvarNoFirestore(finalImageUrl: String, finalAudioUrl: String) {
            val categoriaAtualizada = Categoria(
                id = categoriaId,
                nome = nome,
                slug = slug,
                cor = cor,
                ordem = ordem,
                imageUri = finalImageUrl.ifBlank { null },
                imageUrl = finalImageUrl.ifBlank { null },
                localPath = localPath,
                audioPath = finalAudioUrl.ifBlank { null },
                audioUrl = finalAudioUrl.ifBlank { null }
            )

            // 2. Grava no Cloud Firestore com localPath e URLs
            val updateMap = hashMapOf<String, Any>(
                "id" to categoriaId,
                "nome" to nome,
                "slug" to slug,
                "cor" to cor,
                "ordem" to ordem,
                "owner_uid" to currentUserId,
                "imageUrl" to finalImageUrl,
                "imageUri" to finalImageUrl,
                "localPath" to (localPath ?: ""),
                "audioUrl" to finalAudioUrl,
                "audioPath" to finalAudioUrl
            )

            firestore.collection("categoria").document(categoriaId).set(updateMap)
                .addOnSuccessListener {
                    firestore.collection("usuarios").document(currentUserId)
                        .collection("categorias").document(categoriaId).set(categoriaAtualizada)

                    Toast.makeText(this, "Categoria e mídias sincronizadas com sucesso!", Toast.LENGTH_SHORT).show()
                    onComplete(finalImageUrl, finalAudioUrl)
                }
                .addOnFailureListener { erro ->
                    Toast.makeText(this, "Erro ao salvar categoria no Firestore: ${erro.message}", Toast.LENGTH_LONG).show()
                    onComplete(finalImageUrl, finalAudioUrl)
                }
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

        val uriParaUploadImagem: Uri? = when {
            uriImagemSelecionada != null -> uriImagemSelecionada
            localFile != null && localFile.exists() -> Uri.fromFile(localFile)
            else -> null
        }

        if (uriParaUploadImagem != null) {
            storageRefImg.putFile(uriParaUploadImagem).continueWithTask { task ->
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

    private fun mostrarModalConfirmarExclusao(categoria: Categoria) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirmar_exclusao, null)
        val txtMensagem = dialogView.findViewById<TextView>(R.id.txtMensagemExclusao)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarExclusao)
        val btnConfirmar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarExclusao)

        txtMensagem?.text = "Tem certeza de que deseja excluir a categoria \"${categoria.nome}\"? Todos os seus botões também serão removidos."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancelar?.setOnClickListener { dialog.dismiss() }
        btnConfirmar?.setOnClickListener {
            viewModel.excluirCategoria(currentUserId, categoria.id)

            firestore.collection("categoria").document(categoria.id).delete()
            firestore.collection("usuarios").document(currentUserId)
                .collection("categorias").document(categoria.id)
                .delete()

            Toast.makeText(this, "Categoria \"${categoria.nome}\" excluída com sucesso!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    // ==========================================
    // MODAL DE BOTÕES (DELEGAÇÃO)
    // ==========================================

    fun mostrarModalEditarBotao(item: CategoriaItem, categoriaId: String = "") {
        val modal = ModalEdicaoBotao(
            activity = this,
            onAbrirGaleria = { pickImageLauncher.launch("image/*") },
            onAbrirAudioLocal = { pickAudioLauncher.launch("audio/*") }
        )
        modal.mostrar(
            itemExistente = item,
            categoriaId = categoriaId,
            onSalvo = { itemAtualizado ->
                item.rotulo = itemAtualizado.rotulo
                item.imageUrl = itemAtualizado.imageUrl
                item.imageUri = itemAtualizado.imageUrl
                item.localPath = itemAtualizado.localPath
                item.audioUrl = itemAtualizado.audioUrl
                item.audioPath = itemAtualizado.audioUrl
                adapter.notifyDataSetChanged()
            }
        )
    }
}
