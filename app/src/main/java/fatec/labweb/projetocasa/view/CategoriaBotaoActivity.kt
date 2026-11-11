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
import android.view.MenuItem
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
import androidx.lifecycle.lifecycleScope
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
import fatec.labweb.projetocasa.repository.CategoriaRepository
import fatec.labweb.projetocasa.service.CategoriaService
import fatec.labweb.projetocasa.service.DevHardwareSyncService
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import fatec.labweb.projetocasa.viewmodel.CategoriaViewModel
import kotlinx.coroutines.launch
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
    private val devSyncService = DevHardwareSyncService()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun getContentLayoutId(): Int = R.layout.activity_categoria_botao
    override fun getCurrentMenuItemId(): Int = R.id.menuCategorias

    protected var categoriaId: String = ""
    protected var categoriaNome: String = ""
    protected var categoriaCor: String = "#2563EB"

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
    private var cameraImageFile: File? = null
    private var botaoIdFotoCamera: String = ""
    private var modalEdicaoBotaoAtivo: ModalEdicaoBotao? = null

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

    // 2. Captura Nativa de Foto com a Câmera (TakePicture com FileProvider vinculado a botoes_imagens)
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso: Boolean ->
        if (sucesso && cameraImageFile != null && cameraImageFile!!.exists()) {
            val uri = cameraImageUri ?: Uri.fromFile(cameraImageFile!!)
            val file = cameraImageFile!!
            uriImagemSelecionada = uri
            desenhoSelecionado = null
            imgPreviewModalAtivo?.let { imgView ->
                imgView.clearColorFilter()
                imgView.imageTintList = null
                imgView.visibility = View.VISIBLE
                Glide.with(this)
                    .load(file)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .placeholder(R.drawable.ic_default_button)
                    .error(R.drawable.ic_default_button)
                    .into(imgView)
            }
            modalEdicaoBotaoAtivo?.definirImagemPreview(uri, file)
            Toast.makeText(this, "Foto da câmera capturada com sucesso!", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. Permissão Dinâmica para Câmera
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            dispararCapturaCamera(botaoIdFotoCamera)
        } else {
            Toast.makeText(this, "Permissão de câmera necessária para tirar fotos.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dispararCapturaCamera(botaoId: String = "") {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            botaoIdFotoCamera = botaoId
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        try {
            val idAlvo = if (botaoId.isNotBlank()) botaoId else ("btn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
            botaoIdFotoCamera = idAlvo

            val photoFile = ModalEdicaoBotao.criarArquivoFotoCamera(this, idAlvo)
            cameraImageFile = photoFile
            val photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                photoFile
            )
            cameraImageUri = photoUri
            takePictureLauncher.launch(photoUri)
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
        categoriaCor = intent.getStringExtra(EXTRA_CATEGORIA_COR) ?: "#2563EB"

        initViews()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        carregarBotoesComPersistenciaLocal()
    }

    override fun onResume() {
        super.onResume()
        if (categoriaId.isNotBlank()) {
            carregarBotoesComPersistenciaLocal()
        }
    }

    /**
     * Carrega a lista de botões com prioridade local (filesDir JSON e SharedPreferences),
     * garantindo persistência imediata mesmo sob as regras de apenas leitura do Firestore v2.
     */
    fun carregarBotoesComPersistenciaLocal() {
        val botoesLocais = MidiaLocalHelper.carregarBotoesCategoriaLocalmente(this, categoriaId)
        val botoesPorSlug = if (botoesLocais.isEmpty() && categoriaNome.isNotBlank()) {
            MidiaLocalHelper.carregarBotoesCategoriaLocalmente(this, categoriaNome.lowercase().replace(" ", "_"))
        } else emptyList()
        val listaFinal = if (botoesLocais.isNotEmpty()) botoesLocais else botoesPorSlug

        if (listaFinal.isNotEmpty()) {
            adapter.atualizarLista(listaFinal)
            adapter.notifyDataSetChanged()
            txtSemBotoes.visibility = View.GONE
            rvBotoes.visibility = View.VISIBLE
        } else {
            val catPadrao = CategoriaRepository.getCategoriasPadrao().find {
                it.id == categoriaId || it.slug.equals(categoriaId, ignoreCase = true) || it.nome.equals(categoriaNome, ignoreCase = true)
            }
            if (catPadrao != null && catPadrao.botoes.isNotEmpty()) {
                MidiaLocalHelper.salvarBotoesCategoriaLocalmente(this, categoriaId, catPadrao.botoes)
                adapter.atualizarLista(catPadrao.botoes)
                adapter.notifyDataSetChanged()
                txtSemBotoes.visibility = View.GONE
                rvBotoes.visibility = View.VISIBLE
            }
        }

        viewModel.carregarBotoes(currentUserId, categoriaId)
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
        findViewById<MaterialButton>(R.id.btnAdicionarBotao)?.setOnClickListener { view ->
            dispararAudioCue(view)
            if (adapter.itemCount >= CategoriaService.MAX_BOTOES_POR_CATEGORIA) {
                Toast.makeText(this, "Limite máximo de ${CategoriaService.MAX_BOTOES_POR_CATEGORIA} botões atingido nesta categoria.", Toast.LENGTH_LONG).show()
            } else {
                mostrarModalCriarBotao()
            }
        }

        findViewById<MaterialButton>(R.id.btnSincronizarHardwareDev)?.setOnClickListener { view ->
            dispararAudioCue(view)
            sincronizarComHardwareDevManual()
        }

        etBuscarBotao.doAfterTextChanged { editable ->
            adapter.filtrar(editable?.toString() ?: "")
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading == true) View.VISIBLE else View.GONE
        }

        viewModel.botoes.observe(this) { listaRemota ->
            val listaLocais = MidiaLocalHelper.carregarBotoesCategoriaLocalmente(this, categoriaId)
            val combinada = if (listaRemota.isNullOrEmpty()) {
                listaLocais
            } else {
                val listaMut = listaRemota.toMutableList()
                for (bLocal in listaLocais) {
                    val idx = listaMut.indexOfFirst { it.id == bLocal.id }
                    if (idx != -1) {
                        listaMut[idx] = bLocal
                    } else {
                        listaMut.add(bLocal)
                    }
                }
                listaMut
            }
            if (combinada.isNotEmpty()) {
                adapter.atualizarLista(combinada)
                adapter.notifyDataSetChanged()
                txtSemBotoes.visibility = View.GONE
                rvBotoes.visibility = View.VISIBLE
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
    // MODAL DE CRIAÇÃO DE BOTÃO COM MÍDIAS E PERSISTÊNCIA FÍSICA LOCAL
    // ==========================================

    private fun mostrarModalCriarBotao() {
        val modal = ModalEdicaoBotao(
            activity = this,
            onAbrirGaleria = { pickImageLauncher.launch("image/*") },
            onTirarFoto = { dispararCapturaCamera() },
            onAbrirAudioLocal = { pickAudioLauncher.launch("audio/*") },
            onTirarFotoComId = { id -> dispararCapturaCamera(id) }
        )
        modalEdicaoBotaoAtivo = modal
        modal.mostrar(
            itemExistente = null,
            categoriaId = categoriaId,
            categoriaNome = categoriaNome,
            ordemPadrao = adapter.itemCount,
            onSalvo = { novoBotao ->
                // 1. Adiciona o novo botão à lista local da categoria
                val listaBotoes = MidiaLocalHelper.carregarBotoesCategoriaLocalmente(this, categoriaId)
                val idx = listaBotoes.indexOfFirst { it.id == novoBotao.id }
                if (idx != -1) {
                    listaBotoes[idx] = novoBotao
                } else {
                    listaBotoes.add(novoBotao)
                }

                // 2. Persiste a lista atualizada no armazenamento interno do dispositivo
                MidiaLocalHelper.salvarBotoesCategoriaLocalmente(this, categoriaId, listaBotoes)

                // 3. Notifica o Adapter para renderizar na hora na RecyclerView
                adapter.adicionarBotao(novoBotao)
                adapter.notifyDataSetChanged()
                txtSemBotoes.visibility = View.GONE
                rvBotoes.visibility = View.VISIBLE

                // 4. Sincronização automática em Modo Desenvolvedor com ESP32 v8
                autoSincronizarHardwareDevBackground(listaBotoes)
            }
        )
    }

    // ==========================================
    // MODAL DE EDIÇÃO DE BOTÃO COM PERSISTÊNCIA FÍSICA LOCAL
    // ==========================================

    private fun mostrarModalEditarBotao(item: CategoriaItem) {
        val modal = ModalEdicaoBotao(
            activity = this,
            onAbrirGaleria = { pickImageLauncher.launch("image/*") },
            onTirarFoto = { dispararCapturaCamera() },
            onAbrirAudioLocal = { pickAudioLauncher.launch("audio/*") },
            onTirarFotoComId = { id -> dispararCapturaCamera(id) }
        )
        modalEdicaoBotaoAtivo = modal
        modal.mostrar(
            itemExistente = item,
            categoriaId = categoriaId,
            categoriaNome = categoriaNome,
            ordemPadrao = item.ordem,
            onSalvo = { itemAtualizado ->
                val listaBotoes = MidiaLocalHelper.carregarBotoesCategoriaLocalmente(this, categoriaId)
                val idx = listaBotoes.indexOfFirst { it.id == itemAtualizado.id }
                if (idx != -1) {
                    listaBotoes[idx] = itemAtualizado
                } else {
                    listaBotoes.add(itemAtualizado)
                }
                MidiaLocalHelper.salvarBotoesCategoriaLocalmente(this, categoriaId, listaBotoes)
                adapter.atualizarItem(itemAtualizado)
                adapter.notifyDataSetChanged()

                // Sincronização automática em Modo Desenvolvedor com ESP32 v8
                autoSincronizarHardwareDevBackground(listaBotoes)
            }
        )
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
            val listaBotoes = MidiaLocalHelper.carregarBotoesCategoriaLocalmente(this, categoriaId)
            listaBotoes.removeAll { it.id == item.id }
            MidiaLocalHelper.salvarBotoesCategoriaLocalmente(this, categoriaId, listaBotoes)
            adapter.removerItem(item.id)
            adapter.notifyDataSetChanged()
            val vazia = listaBotoes.isEmpty()
            txtSemBotoes.visibility = if (vazia) View.VISIBLE else View.GONE
            rvBotoes.visibility = if (vazia) View.GONE else View.VISIBLE

            viewModel.removerBotao(currentUserId, categoriaId, item.id)
            firestore.collection("categoria_botao").document(item.id).delete()

            Toast.makeText(this, "Botão \"${item.rotulo}\" excluído com sucesso!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()

            // Sincronização automática em Modo Desenvolvedor com ESP32 v8
            autoSincronizarHardwareDevBackground(listaBotoes)
        }

        dialog.show()
    }

    private fun sincronizarComHardwareDevManual() {
        val btn = findViewById<MaterialButton>(R.id.btnSincronizarHardwareDev)
        btn?.isEnabled = false
        btn?.text = "Sincronizando com ESP32..."
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val lista = MidiaLocalHelper.carregarBotoesCategoriaLocalmente(this@CategoriaBotaoActivity, categoriaId)
            val res = devSyncService.sincronizarComHardwareDev(
                context = this@CategoriaBotaoActivity,
                botoes = lista,
                corCategoria = categoriaCor
            )
            progressBar.visibility = View.GONE
            btn?.isEnabled = true
            btn?.text = "Sincronizar com ESP32 Dev (v8)"

            if (res.isSuccess) {
                val total = res.getOrThrow()
                Toast.makeText(
                    this@CategoriaBotaoActivity,
                    "ESP32 Dev atualizado! $total mídias gravadas em arquivos_esp e RTDB ativado.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@CategoriaBotaoActivity,
                    "Erro ao sincronizar com hardware dev: ${res.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun autoSincronizarHardwareDevBackground(lista: List<CategoriaItem>) {
        lifecycleScope.launch {
            try {
                devSyncService.sincronizarComHardwareDev(
                    context = this@CategoriaBotaoActivity,
                    botoes = lista,
                    corCategoria = categoriaCor
                )
            } catch (e: Exception) {
                android.util.Log.w("CategoriaBotaoActivity", "Aviso no auto-sync dev: ${e.message}")
            }
        }
    }
}
