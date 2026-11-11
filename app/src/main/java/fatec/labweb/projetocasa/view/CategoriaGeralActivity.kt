package fatec.labweb.projetocasa.view

import android.Manifest
import android.content.Context
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
import fatec.labweb.projetocasa.adapter.CategoriaAdapter
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.repository.CategoriaRepository
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import fatec.labweb.projetocasa.util.SoundManager
import fatec.labweb.projetocasa.viewmodel.CategoriaViewModel
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * CategoriaGeralActivity: Gerenciamento completo de Categorias com suporte dual de imagem
 * (Galeria + Câmera via FileProvider), gravação e seleção de áudios locais, persistência física
 * no armazenamento do dispositivo em conformidade com as regras v2 do Cloud Firestore,
 * e renderização prioritária com Glide sem cache defasado.
 */
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

    // Câmera & FileProvider
    private var cameraImageUri: Uri? = null
    private var modalEdicaoBotaoAtivo: ModalEdicaoBotao? = null

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
            desenhoSelecionado = null
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
            modalEdicaoBotaoAtivo?.definirImagemPreview(uri)
            Toast.makeText(this, "Imagem selecionada com sucesso", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Captura Nativa de Foto com a Câmera (TakePicture com FileProvider)
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso: Boolean ->
        if (sucesso && cameraImageUri != null) {
            val uri = cameraImageUri!!
            uriImagemSelecionada = uri
            desenhoSelecionado = null
            ivPreviewModalAtivo?.let { imgView ->
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
            modalEdicaoBotaoAtivo?.definirImagemPreview(uri)
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

    // 4. Seletor Nativo de Áudio do Dispositivo (MIME type "audio/*")
    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uriAudioSelecionado = uri
            arquivoAudioGravado = null
            txtStatusAudioModalAtivo?.text = "Áudio local selecionado do dispositivo."
            btnOuvirPreviaAtivo?.visibility = View.VISIBLE
            modalEdicaoBotaoAtivo?.definirAudioSelecionado(uri)
            Toast.makeText(this, "Arquivo de áudio selecionado", Toast.LENGTH_SHORT).show()
        }
    }

    // 5. Permissão Dinâmica para Gravação de Voz (RECORD_AUDIO)
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

        carregarCategoriasComBotoesLocais()
    }

    override fun onResume() {
        super.onResume()
        carregarCategoriasComBotoesLocais()
    }

    /**
     * Força a leitura do arquivo/banco local (filesDir e SharedPreferences) garantindo que,
     * ao voltar para a tela ou recarregar, todos os botões e mídias salvos localmente
     * permaneçam visíveis e atualizados, imunes a restrições de permissão do Firestore v2.
     */
    fun carregarCategoriasComBotoesLocais() {
        val listaAtual = adapter.obterCategorias().toMutableList()
        val categoriasBase = if (listaAtual.isNotEmpty()) {
            listaAtual
        } else {
            CategoriaRepository.getCategoriasPadrao().map { it.copy(botoes = it.botoes.toMutableList()) }.toMutableList()
        }

        for (cat in categoriasBase) {
            val botoesLocais = carregarListaLocalmente(cat.id)
            val botoesPorSlug = if (botoesLocais.isEmpty() && cat.slug.isNotBlank()) carregarListaLocalmente(cat.slug) else emptyList()
            val botoesFinais = if (botoesLocais.isNotEmpty()) botoesLocais else botoesPorSlug

            if (botoesFinais.isNotEmpty()) {
                for (bLocal in botoesFinais) {
                    val idx = cat.botoes.indexOfFirst { it.id == bLocal.id }
                    if (idx != -1) {
                        cat.botoes[idx] = bLocal
                    } else {
                        cat.botoes.add(bLocal)
                    }
                }
            } else if (cat.botoes.isNotEmpty()) {
                salvarListaLocalmente(cat.id, cat.botoes)
            }
        }

        adapter.atualizarDados(categoriasBase)
        adapter.notifyDataSetChanged()
        val vazia = categoriasBase.isEmpty()
        txtSemCategorias.visibility = if (vazia) View.VISIBLE else View.GONE
        rvCategorias.visibility = if (vazia) View.GONE else View.VISIBLE

        viewModel.carregarDados(currentUserId)
    }

    fun salvarListaLocalmente(categoriaId: String, listaBotoes: List<CategoriaItem>) {
        MidiaLocalHelper.salvarBotoesCategoriaLocalmente(this, categoriaId, listaBotoes)
    }

    fun carregarListaLocalmente(categoriaId: String): MutableList<CategoriaItem> {
        return MidiaLocalHelper.carregarBotoesCategoriaLocalmente(this, categoriaId)
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
        findViewById<MaterialButton>(R.id.btnAdicionarCategoria)?.setOnClickListener { view ->
            dispararAudioCue(view)
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

        viewModel.categorias.observe(this) { listaRemota ->
            if (!listaRemota.isNullOrEmpty()) {
                val listaComLocais = listaRemota.map { cat ->
                    val catCopia = cat.copy(botoes = cat.botoes.toMutableList())
                    val botoesLocais = carregarListaLocalmente(cat.id)
                    val botoesPorSlug = if (botoesLocais.isEmpty() && cat.slug.isNotBlank()) carregarListaLocalmente(cat.slug) else emptyList()
                    val locais = if (botoesLocais.isNotEmpty()) botoesLocais else botoesPorSlug

                    for (bLocal in locais) {
                        val idx = catCopia.botoes.indexOfFirst { it.id == bLocal.id }
                        if (idx != -1) {
                            catCopia.botoes[idx] = bLocal
                        } else {
                            catCopia.botoes.add(bLocal)
                        }
                    }
                    catCopia
                }
                adapter.atualizarDados(listaComLocais)
                adapter.notifyDataSetChanged()
                txtSemCategorias.visibility = View.GONE
                rvCategorias.visibility = View.VISIBLE
            }
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
    // CAPTURA DE FOTO VIA CÂMERA (FILEPROVIDER)
    // ==========================================

    private fun dispararCapturaCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        try {
            val pastaTemp = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: cacheDir
            if (!pastaTemp.exists()) pastaTemp.mkdirs()
            val arquivoFoto = File.createTempFile("camera_categoria_", ".jpg", pastaTemp)
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

    // ==========================================
    // REPRODUÇÃO DE ÁUDIO REAL
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
                    setDataSource(audioUrl)
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
                Toast.makeText(this, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
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
                    setDataSource(audioUrl)
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
        modalEdicaoBotaoAtivo = null
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
    // MODAL DE CRIAÇÃO COM SUPORTE DUAL (GALERIA + CÂMERA)
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
        val btnFotoCamera = dialogView.findViewById<View>(R.id.btnTakeCategoryCamera)
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
            dispararAudioCue(it)
            pickImageLauncher.launch("image/*")
        }

        btnFotoCamera?.setOnClickListener {
            dispararAudioCue(it)
            dispararCapturaCamera()
        }

        btnDesenhosDrive?.setOnClickListener {
            dispararAudioCue(it)
            DialogSeletorDesenhos(this) { desenho ->
                desenhoSelecionado = desenho
                uriImagemSelecionada = null
                ivPreview?.clearColorFilter()
                ivPreview?.imageTintList = null
                ivPreview?.setImageResource(desenho.resId)
            }.exibir()
        }

        btnDefaultImg?.setOnClickListener {
            dispararAudioCue(it)
            uriImagemSelecionada = null
            desenhoSelecionado = null
            ivPreview?.setImageResource(R.drawable.ic_default_button)
        }

        btnAudioDispositivo?.setOnClickListener {
            dispararAudioCue(it)
            pickAudioLauncher.launch("audio/*")
        }

        btnGravarAudio?.setOnClickListener {
            dispararAudioCue(it)
            ModalGravadorAudio(this, catId) { arquivoLocal ->
                arquivoAudioGravado = arquivoLocal
                uriAudioSelecionado = null
                txtStatusAudio?.text = "Voz gravada no dispositivo (${arquivoLocal.name})"
                btnPreviaAudio?.visibility = View.VISIBLE
            }.iniciar()
        }

        btnPararGravacao?.setOnClickListener {
            dispararAudioCue(it)
            pararGravacaoAudioNativa()
        }

        btnPreviaAudio?.setOnClickListener {
            dispararAudioCue(it)
            reproduzirPreviaAudio()
        }

        btnSaveCategory?.setOnClickListener {
            dispararAudioCue(it)
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
    // MODAL DE EDIÇÃO COM SUPORTE DUAL (GALERIA + CÂMERA)
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
        val btnFotoCamera = dialogView.findViewById<View>(R.id.btnTakeCategoryCamera)
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
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
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
            dispararAudioCue(it)
            pickImageLauncher.launch("image/*")
        }

        btnFotoCamera?.setOnClickListener {
            dispararAudioCue(it)
            dispararCapturaCamera()
        }

        btnDesenhosDrive?.setOnClickListener {
            dispararAudioCue(it)
            DialogSeletorDesenhos(this) { desenho ->
                desenhoSelecionado = desenho
                uriImagemSelecionada = null
                ivPreview?.clearColorFilter()
                ivPreview?.imageTintList = null
                ivPreview?.setImageResource(desenho.resId)
            }.exibir()
        }

        btnDefaultImg?.setOnClickListener {
            dispararAudioCue(it)
            uriImagemSelecionada = null
            desenhoSelecionado = null
            categoria.imageUri = null
            categoria.imageUrl = null
            categoria.localPath = null
            ivPreview?.setImageResource(R.drawable.ic_default_button)
        }

        btnAudioDispositivo?.setOnClickListener {
            dispararAudioCue(it)
            pickAudioLauncher.launch("audio/*")
        }

        btnGravarAudio?.setOnClickListener {
            dispararAudioCue(it)
            ModalGravadorAudio(this, categoria.id) { arquivoLocal ->
                arquivoAudioGravado = arquivoLocal
                uriAudioSelecionado = null
                txtStatusAudio?.text = "Voz gravada no dispositivo (${arquivoLocal.name})"
                btnPreviaAudio?.visibility = View.VISIBLE
            }.iniciar()
        }

        btnPararGravacao?.setOnClickListener {
            dispararAudioCue(it)
            pararGravacaoAudioNativa()
        }

        btnPreviaAudio?.setOnClickListener {
            dispararAudioCue(it)
            if (arquivoAudioGravado != null || uriAudioSelecionado != null) {
                reproduzirPreviaAudio()
            } else if (!categoria.audioUrl.isNullOrBlank() || !categoria.audioPath.isNullOrBlank()) {
                tocarAudioCategoria(categoria)
            }
        }

        btnSaveCategory?.setOnClickListener {
            dispararAudioCue(it)
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
    // PERSISTÊNCIA 100% LOCAL & SINCRONIZAÇÃO EM NUVEM (FIRESTORE v2)
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

        // 1. Salvamento Local Obrigatório (getExternalFilesDir("botoes_imagens"))
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

        // 2. Conformidade com Firestore v2: Persistência em SharedPreferences para funcionamento offline imediato
        val prefs = getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("cat_${categoriaId}_nome", nome)
            putString("cat_${categoriaId}_slug", slug)
            putString("cat_${categoriaId}_localPath", localPath ?: "")
            putString("cat_${categoriaId}_audioPath", localAudioFile?.absolutePath ?: "")
            apply()
        }

        val categoriaAtualizada = Categoria(
            id = categoriaId,
            nome = nome,
            slug = slug,
            cor = cor,
            ordem = ordem,
            imageUri = localPath ?: imagemUrlExistente,
            imageUrl = localPath ?: imagemUrlExistente,
            localPath = localPath,
            audioPath = localAudioFile?.absolutePath ?: audioUrlExistente,
            audioUrl = localAudioFile?.absolutePath ?: audioUrlExistente
        )

        // Atualiza imediatamente o adapter para alta velocidade e sem bloqueio de rede
        adapter.atualizarItem(categoriaAtualizada)
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Categoria salva localmente com sucesso!", Toast.LENGTH_SHORT).show()
        onComplete(categoriaAtualizada.imageUrl ?: "", categoriaAtualizada.audioUrl ?: "")

        // 3. Gravação em nuvem resiliente (Cloud Firestore / Firebase Storage) tolerante a regras v2
        fun salvarNoFirestore(finalImageUrl: String, finalAudioUrl: String) {
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
                        .addOnFailureListener { /* Regras v2 de leitura: tolerante */ }
                }
                .addOnFailureListener { /* Regras v2 de leitura: tolerante */ }
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

        btnCancelar?.setOnClickListener {
            dispararAudioCue(it)
            dialog.dismiss()
        }
        btnConfirmar?.setOnClickListener {
            dispararAudioCue(it)
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
    // MODAL DE BOTÕES (DELEGAÇÃO COM SUPORTE DUAL GALERIA + CÂMERA E PERSISTÊNCIA LOCAL)
    // ==========================================

    fun mostrarModalEditarBotao(item: CategoriaItem?, categoriaId: String = "", categoriaNome: String = "") {
        val modal = ModalEdicaoBotao(
            activity = this,
            onAbrirGaleria = { pickImageLauncher.launch("image/*") },
            onTirarFoto = { dispararCapturaCamera() },
            onAbrirAudioLocal = { pickAudioLauncher.launch("audio/*") }
        )
        modalEdicaoBotaoAtivo = modal
        modal.mostrar(
            itemExistente = item,
            categoriaId = categoriaId,
            categoriaNome = categoriaNome,
            onSalvo = { novoBotao ->
                // 1. Adiciona o novo botão à lista local da categoria
                val listaBotoes = carregarListaLocalmente(categoriaId)
                val idx = listaBotoes.indexOfFirst { it.id == novoBotao.id }
                if (idx != -1) {
                    listaBotoes[idx] = novoBotao
                } else {
                    listaBotoes.add(novoBotao)
                }

                // 2. Persiste a lista atualizada no armazenamento interno do dispositivo
                salvarListaLocalmente(categoriaId, listaBotoes)

                // 3. Notifica o Adapter para renderizar na hora na RecyclerView
                adapter.adicionarBotaoACategoria(categoriaId, novoBotao)
                adapter.notifyDataSetChanged()
            }
        )
    }

    fun mostrarModalEditarBotao(item: CategoriaItem) {
        mostrarModalEditarBotao(item, "", "")
    }
}
