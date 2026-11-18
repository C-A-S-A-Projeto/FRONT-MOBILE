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
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
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
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.CasaApplication
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.adapter.CategoriaAdapter
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.service.Esp32MediaSyncService
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.util.AudioDspHelper
import fatec.labweb.projetocasa.util.AudioPredefinidoHelper
import fatec.labweb.projetocasa.repository.CategoriaRepository
import fatec.labweb.projetocasa.service.DevHardwareSyncService
import fatec.labweb.projetocasa.service.DispositivoService
import fatec.labweb.projetocasa.service.Esp32TriggerService
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import fatec.labweb.projetocasa.util.SoundManager
import fatec.labweb.projetocasa.viewmodel.CategoriaViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    companion object {
        var modoDesenvolvedorSessao: Boolean = false
        var temAlteracoesPendentes: Boolean = false
    }

    private val viewModel: CategoriaViewModel by viewModels()
    private val devSyncService = DevHardwareSyncService()
    private val dispositivoService = DispositivoService()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private lateinit var containerConteudoCategorias: View
    private lateinit var containerAcessoBloqueado: View
    private lateinit var cardModoDevGeral: View
    private lateinit var cardGerenciarWifiGeral: View
    private var macDispositivoConectado: String? = null

    override fun getContentLayoutId(): Int = R.layout.activity_categoria_item
    override fun getCurrentMenuItemId(): Int = R.id.menuDispositivo

    private var temAlteracoesNaoSalvas = false

    override fun podeNavegar(): Boolean = !temAlteracoesNaoSalvas

    override fun avisarAlteracoesNaoSalvas(onConfirmar: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Alterações não salvas")
            .setMessage("Você tem alterações na prancha que ainda não foram sincronizadas com o dispositivo. Se sair agora, as mudanças não chegarão ao hardware. Deseja sair mesmo assim?")
            .setPositiveButton("Sair sem Salvar") { _, _ ->
                temAlteracoesNaoSalvas = false
                onConfirmar()
            }
            .setNegativeButton("Continuar Editando", null)
            .show()
    }

    data class CategoriaPredefinidaInfo(
        val id: String,
        val nome: String,
        val cor: String,
        val drawableRes: Int
    )

    private val categoriasPredefinidasOficiais = listOf(
        CategoriaPredefinidaInfo("casa", "Casa", "#4A90E2", R.drawable.cat_casa),
        CategoriaPredefinidaInfo("desejos", "Desejos", "#F59E0B", R.drawable.cat_desejos),
        CategoriaPredefinidaInfo("emocoes", "Emoções", "#10B981", R.drawable.cat_emocoes)
    )

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
    private var cameraImageFile: File? = null
    private var botaoIdFotoCamera: String = ""
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

    // 2. Captura Nativa de Foto com a Câmera (TakePicture com FileProvider vinculado a botoes_imagens)
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso: Boolean ->
        if (sucesso && cameraImageFile != null && cameraImageFile!!.exists()) {
            val uri = cameraImageUri ?: Uri.fromFile(cameraImageFile!!)
            val file = cameraImageFile!!
            uriImagemSelecionada = uri
            desenhoSelecionado = null
            ivPreviewModalAtivo?.let { imgView ->
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
            Toast.makeText(this, "Foto da câmera capturada e salva com sucesso!", Toast.LENGTH_SHORT).show()
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

        verificarStatusDispositivoEModoDev()
        carregarCategoriasComBotoesLocais()
        atualizarVisibilidadeBotoes()
    }

    override fun onResume() {
        super.onResume()
        if (temAlteracoesPendentes) {
            temAlteracoesNaoSalvas = true
        }
        verificarStatusDispositivoEModoDev()
        carregarCategoriasComBotoesLocais()
        atualizarVisibilidadeBotoes()
    }

    /**
     * Valida se há dispositivo conectado para a conta do usuário.
     * 1. Se tiver dispositivo conectado: Modo desenvolvedor DESAPARECE e entrada é liberada.
     * 2. Se NÃO tiver dispositivo conectado: Trava a entrada, exibindo aviso e opção para entrar em modo dev.
     */
    private fun verificarStatusDispositivoEModoDev() {
        val uid = currentUserId
        if (uid.isBlank()) return

        lifecycleScope.launch {
            val dispositivos = dispositivoService.listarDispositivosDoUsuario(uid)
            val temDispositivo = dispositivos.isNotEmpty()
            macDispositivoConectado = dispositivos.firstOrNull()?.mac

            if (temDispositivo) {
                // Dispositivo conectado: desliga modo dev e esconde o banner
                modoDesenvolvedorSessao = false
                aplicarVisibilidadeModo(temDispositivo = true, emModoDev = false)
            } else {
                // Sem dispositivo conectado: verifica se a opção de entrar em modo dev foi ativada
                aplicarVisibilidadeModo(temDispositivo = false, emModoDev = modoDesenvolvedorSessao)
            }
        }
    }

    private fun aplicarVisibilidadeModo(temDispositivo: Boolean, emModoDev: Boolean) {
        if (temDispositivo) {
            containerAcessoBloqueado.visibility = View.GONE
            containerConteudoCategorias.visibility = View.VISIBLE
            cardModoDevGeral.visibility = View.GONE // Modo desenvolvedor desaparece se tiver dispositivo conectado
            cardGerenciarWifiGeral.visibility = View.VISIBLE
        } else if (emModoDev) {
            containerAcessoBloqueado.visibility = View.GONE
            containerConteudoCategorias.visibility = View.VISIBLE
            cardModoDevGeral.visibility = View.VISIBLE
            cardGerenciarWifiGeral.visibility = View.GONE
        } else {
            // Trava entrada na tela dispositivo
            containerAcessoBloqueado.visibility = View.VISIBLE
            containerConteudoCategorias.visibility = View.GONE
            cardModoDevGeral.visibility = View.GONE
            cardGerenciarWifiGeral.visibility = View.GONE
        }
    }

    /**
     * Força a leitura do arquivo/banco local (filesDir e SharedPreferences) garantindo que,
     * ao voltar para a tela ou reiniciar o aplicativo, todas as categorias, títulos personalizados,
     * botões e mídias salvos localmente sejam hidratados imediatamente sem perda de dados.
     */
    fun carregarCategoriasComBotoesLocais() {
        val temSalvasLocalmente = MidiaLocalHelper.temCategoriasAtivasSalvas(this)
        val categoriasSalvas = MidiaLocalHelper.carregarCategoriasAtivasSalvas(this)
        val categoriasBase = when {
            temSalvasLocalmente && categoriasSalvas.isNotEmpty() -> categoriasSalvas.map { it.copy(botoes = it.botoes.toMutableList()) }.toMutableList()
            temSalvasLocalmente -> categoriasSalvas.toMutableList()
            else -> CategoriaRepository.getCategoriasPadrao().map { it.copy(botoes = it.botoes.toMutableList()) }.toMutableList()
        }

        for (cat in categoriasBase) {
            val arqImgLocal = MidiaLocalHelper.obterArquivoLocal(this, cat.id)
            if (arqImgLocal != null && arqImgLocal.exists()) {
                cat.localPath = arqImgLocal.absolutePath
                cat.imageUrl = arqImgLocal.absolutePath
                cat.imageUri = arqImgLocal.absolutePath
            }
            val arqAudioLocal = MidiaLocalHelper.obterAudioLocal(this, cat.id)
            if (arqAudioLocal != null && arqAudioLocal.exists()) {
                cat.audioPath = arqAudioLocal.absolutePath
                cat.audioUrl = arqAudioLocal.absolutePath
            }

            val temLocalId = MidiaLocalHelper.temBotoesSalvosLocalmente(this, cat.id)
            val temLocalSlug = !temLocalId && cat.slug.isNotBlank() && MidiaLocalHelper.temBotoesSalvosLocalmente(this, cat.slug)

            if (temLocalId) {
                val botoesLocais = carregarListaLocalmente(cat.id)
                cat.botoes.clear()
                cat.botoes.addAll(botoesLocais)
            } else if (temLocalSlug) {
                val botoesPorSlug = carregarListaLocalmente(cat.slug)
                cat.botoes.clear()
                cat.botoes.addAll(botoesPorSlug)
                salvarListaLocalmente(cat.id, botoesPorSlug)
            } else if (cat.botoes.isNotEmpty()) {
                val botoesValidados = MidiaLocalHelper.filtrarBotoesSemConflitoGlobal(this, cat.botoes, cat.id, cat.nome)
                cat.botoes.clear()
                cat.botoes.addAll(botoesValidados)
                salvarListaLocalmente(cat.id, cat.botoes)
            } else {
                val catPadrao = CategoriaRepository.getCategoriasPadrao().find {
                    CategoriaRepository.ehMesmaCategoria(it, cat.id) ||
                        (cat.slug.isNotBlank() && CategoriaRepository.ehMesmaCategoria(it, cat.slug))
                }
                if (catPadrao != null && catPadrao.botoes.isNotEmpty()) {
                    val botoesValidados = MidiaLocalHelper.filtrarBotoesSemConflitoGlobal(this, catPadrao.botoes, cat.id, cat.nome)
                    cat.botoes.clear()
                    cat.botoes.addAll(botoesValidados)
                    salvarListaLocalmente(cat.id, cat.botoes)
                }
            }
        }

        MidiaLocalHelper.salvarCategoriasAtivasLocalmente(this, categoriasBase)
        adapter.atualizarDados(categoriasBase)
        adapter.notifyDataSetChanged()
        val vazia = categoriasBase.isEmpty()
        txtSemCategorias.visibility = if (vazia) View.VISIBLE else View.GONE
        rvCategorias.visibility = if (vazia) View.GONE else View.VISIBLE

        if (!temSalvasLocalmente) {
            viewModel.carregarDados(currentUserId)
        }
        atualizarVisibilidadeBotoes()
    }

    private fun obterCategoriasConsolidadas(): List<Categoria> {
        val listaAtual = if (::adapter.isInitialized) adapter.obterCategorias() else emptyList()
        return listaAtual.mapIndexed { idx, cat ->
            val botoesLocais = if (MidiaLocalHelper.temBotoesSalvosLocalmente(this, cat.id)) {
                carregarListaLocalmente(cat.id)
            } else if (cat.slug.isNotBlank() && MidiaLocalHelper.temBotoesSalvosLocalmente(this, cat.slug)) {
                carregarListaLocalmente(cat.slug)
            } else {
                cat.botoes
            }
            cat.copy(botoes = botoesLocais.toMutableList(), ordem = idx)
        }
    }

    fun atualizarVisibilidadeBotoes() {
        val categoriasConsolidadas = obterCategoriasConsolidadas()
        val totalCategorias = categoriasConsolidadas.size
        findViewById<View>(R.id.btnAdicionarCategoria)?.visibility =
            if (totalCategorias >= CategoriaRepository.MAX_CATEGORIAS) View.GONE else View.VISIBLE

        val cardAviso = findViewById<View>(R.id.cardAvisoPendenciaCategorias)
        val txtAviso = findViewById<TextView>(R.id.txtAvisoPendenciaCategorias)
        val msgPendencia = CategoriaRepository.gerarMensagemPendenciaGlobal(categoriasConsolidadas)
        if (msgPendencia != null) {
            txtAviso?.text = msgPendencia
            cardAviso?.visibility = View.VISIBLE
        } else {
            cardAviso?.visibility = View.GONE
        }

        val temAlteracoes = temAlteracoesNaoSalvas || temAlteracoesPendentes || msgPendencia != null
        findViewById<View>(R.id.btnSalvarDispositivo)?.visibility =
            if (temAlteracoes) View.VISIBLE else View.GONE
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
        containerConteudoCategorias = findViewById(R.id.containerConteudoCategorias)
        containerAcessoBloqueado = findViewById(R.id.containerAcessoBloqueado)
        cardModoDevGeral = findViewById(R.id.cardModoDevGeral)
        cardGerenciarWifiGeral = findViewById(R.id.cardGerenciarWifiGeral)
        findViewById<View>(R.id.btnAbrirGerenciadorWifiGeral)?.setOnClickListener {
            val mac = macDispositivoConectado
            if (!mac.isNullOrBlank()) {
                DialogGerenciarWifi(this, mac).mostrar()
            } else {
                Toast.makeText(this, "Nenhum dispositivo conectado encontrado.", Toast.LENGTH_SHORT).show()
            }
        }
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
        findViewById<MaterialButton>(R.id.btnIrParaInicioBloqueio)?.setOnClickListener { view ->
            dispararAudioCue(view)
            finish()
        }

        findViewById<MaterialButton>(R.id.btnEntrarModoDevBloqueio)?.setOnClickListener { view ->
            dispararAudioCue(view)
            modoDesenvolvedorSessao = true
            aplicarVisibilidadeModo(temDispositivo = false, emModoDev = true)
        }

        findViewById<MaterialButton>(R.id.btnSalvarDispositivo)?.setOnClickListener { view ->
            dispararAudioCue(view)
            salvarAlteracoesNoDispositivo()
        }

        findViewById<MaterialButton>(R.id.btnAdicionarCategoria)?.setOnClickListener { view ->
            dispararAudioCue(view)
            mostrarModalCriarCategoria()
        }

        findViewById<MaterialButton>(R.id.btnSincronizarHardwareDevGeral)?.setOnClickListener { view ->
            dispararAudioCue(view)
            sincronizarCategoriaAtivaComHardwareDev()
        }

        etBuscarCategoria.doAfterTextChanged { editable ->
            adapter.filtrar(editable?.toString() ?: "")
        }
    }

    private fun salvarAlteracoesNoDispositivo() {
        val categoriasParaValidar = obterCategoriasConsolidadas()
        val validacao = CategoriaRepository.validarSalvamentoFinal(categoriasParaValidar)
        if (validacao.isFailure) {
            atualizarVisibilidadeBotoes()
            findViewById<View>(R.id.cardAvisoPendenciaCategorias)?.let { banner ->
                banner.visibility = View.VISIBLE
                banner.requestFocus()
            }
            val msgErro = validacao.exceptionOrNull()?.message
                ?: "Complete as 3 categorias e 5 ações em cada categoria antes de salvar."
            Toast.makeText(this, msgErro, Toast.LENGTH_LONG).show()
            return
        }

        val btn = findViewById<MaterialButton>(R.id.btnSalvarDispositivo)
        btn?.isEnabled = false
        val textoOriginal = btn?.text?.toString() ?: "Salvar no Dispositivo"
        btn?.text = "Salvando no dispositivo..."
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val userId = currentUserId
                val categoriasComLocais = categoriasParaValidar.take(3).mapIndexed { catIdx, cat ->
                    cat.copy(botoes = cat.botoes.take(5).toMutableList(), ordem = catIdx)
                }

                // 1. Sincronização oficial de mídias e prancha conforme contrato do ESP32:
                // subir blocos -> arquivos_midia -> midias -> categorias -> disparar flag no RTDB (/dispositivo/controle/$mac)
                try {
                    val mediaSyncService = Esp32MediaSyncService()
                    mediaSyncService.sincronizarPranchaCompleta(
                        context = this@CategoriaGeralActivity,
                        userId = userId,
                        categorias = categoriasComLocais
                    )
                } catch (eMedia: Exception) {
                    Log.w("CategoriaGeral", "Erro no pipeline de mídia do ESP32: ${eMedia.message}")
                }

                // 2. Enviar categorias para as coleções do Firestore (canônica e legada)
                for (catAtualizada in categoriasComLocais) {
                    try {
                        firestore.collection("usuarios").document(userId)
                            .collection("categorias").document(catAtualizada.id)
                            .set(catAtualizada)
                            .await()
                    } catch (eFs: Exception) {
                        Log.w("CategoriaGeral", "Salvar em /usuarios/$userId/categorias tolerante: ${eFs.message}")
                    }

                    try {
                        firestore.collection("categoria").document(catAtualizada.id)
                            .set(catAtualizada)
                            .await()
                    } catch (eFs: Exception) {
                        Log.w("CategoriaGeral", "Salvar em /categoria tolerante: ${eFs.message}")
                    }
                }

                // 3. Sincronizar com hardware dev em arquivos_esp (foto1..5 e som1..5)
                val primeiraCat = categoriasComLocais.firstOrNull() ?: CategoriaRepository.getCategoriasPadrao().first()
                val botoesPrimeira = carregarListaLocalmente(primeiraCat.id).ifEmpty { primeiraCat.botoes }
                try {
                    devSyncService.sincronizarComHardwareDev(
                        context = this@CategoriaGeralActivity,
                        botoes = botoesPrimeira,
                        corCategoria = primeiraCat.cor
                    )
                } catch (eDev: Exception) {
                    Log.w("CategoriaGeral", "Sincronização dev direta tolerante: ${eDev.message}")
                }

                // 4. Disparar a flag no Realtime Database (/dispositivo/controle/$mac) para todos os aparelhos
                try {
                    Esp32TriggerService().dispararFlagParaTodosDispositivos(userId)
                } catch (eRtdb: Exception) {
                    Log.w("CategoriaGeral", "Gatilho Realtime DB tolerante: ${eRtdb.message}")
                }

                // 5. Incrementar config_versao em /dispositivos do Firestore para o hardware
                try {
                    dispositivoService.sincronizarDispositivos(userId)
                } catch (eDisp: Exception) {
                    Log.w("CategoriaGeral", "Incrementar config_versao tolerante: ${eDisp.message}")
                }

                // 6. Limpar estado de alterações pendentes, permitindo navegar livremente
                temAlteracoesNaoSalvas = false
                temAlteracoesPendentes = false

                Toast.makeText(
                    this@CategoriaGeralActivity,
                    "Alterações salvas com sucesso no Firestore e notificadas ao dispositivo!",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e("CategoriaGeral", "Erro ao salvar no dispositivo", e)
                Toast.makeText(
                    this@CategoriaGeralActivity,
                    "Erro ao salvar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
                btn?.isEnabled = true
                btn?.text = textoOriginal
                atualizarVisibilidadeBotoes()
            }
        }
    }

    private fun sincronizarCategoriaAtivaComHardwareDev() {
        val categoriasParaValidar = obterCategoriasConsolidadas()
        val validacao = CategoriaRepository.validarSalvamentoFinal(categoriasParaValidar)
        if (validacao.isFailure) {
            atualizarVisibilidadeBotoes()
            findViewById<View>(R.id.cardAvisoPendenciaCategorias)?.visibility = View.VISIBLE
            val msgErro = validacao.exceptionOrNull()?.message
                ?: "Complete as 3 categorias e 5 ações em cada categoria antes de sincronizar."
            Toast.makeText(this, msgErro, Toast.LENGTH_LONG).show()
            return
        }

        val btn = findViewById<MaterialButton>(R.id.btnSincronizarHardwareDevGeral)
        btn?.isEnabled = false
        btn?.text = "Sincronizando com o dispositivo..."
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val listaCategorias = adapter.obterCategorias()
            val primeiraCat = listaCategorias.firstOrNull() ?: CategoriaRepository.getCategoriasPadrao().first()
            val botoes = carregarListaLocalmente(primeiraCat.id).ifEmpty { primeiraCat.botoes }

            val res = devSyncService.sincronizarComHardwareDev(
                context = this@CategoriaGeralActivity,
                botoes = botoes,
                corCategoria = primeiraCat.cor
            )
            progressBar.visibility = View.GONE
            btn?.isEnabled = true
            btn?.text = "Sincronizar Categoria Ativa com Dispositivo"

            if (res.isSuccess) {
                temAlteracoesNaoSalvas = false
                atualizarVisibilidadeBotoes()
                val total = res.getOrThrow()
                Toast.makeText(
                    this@CategoriaGeralActivity,
                    "Dispositivo atualizado! Categoria \"${primeiraCat.nome}\" ($total itens) sincronizada com sucesso.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@CategoriaGeralActivity,
                    "Falha ao sincronizar: ${res.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading == true) View.VISIBLE else View.GONE
        }

        viewModel.categorias.observe(this) { listaRemota ->
            // Se o usuário já possui categorias salvas localmente no dispositivo, preserva o estado local
            // para evitar que o retorno assíncrono do Firestore sobrescreva fotos, títulos ou exclusões locais.
            if (MidiaLocalHelper.temCategoriasAtivasSalvas(this)) {
                val locais = MidiaLocalHelper.carregarCategoriasAtivasSalvas(this)
                if (locais.isNotEmpty()) {
                    adapter.atualizarDados(locais)
                    adapter.notifyDataSetChanged()
                    txtSemCategorias.visibility = View.GONE
                    rvCategorias.visibility = View.VISIBLE
                    atualizarVisibilidadeBotoes()
                    return@observe
                }
            }
            if (!listaRemota.isNullOrEmpty()) {
                val listaComLocais = listaRemota.map { cat ->
                    val catCopia = cat.copy(botoes = cat.botoes.toMutableList())
                    val temLocalId = MidiaLocalHelper.temBotoesSalvosLocalmente(this, cat.id)
                    val temLocalSlug = !temLocalId && cat.slug.isNotBlank() && MidiaLocalHelper.temBotoesSalvosLocalmente(this, cat.slug)

                    if (temLocalId) {
                        val botoesLocais = carregarListaLocalmente(cat.id)
                        catCopia.botoes.clear()
                        catCopia.botoes.addAll(botoesLocais)
                    } else if (temLocalSlug) {
                        val botoesPorSlug = carregarListaLocalmente(cat.slug)
                        catCopia.botoes.clear()
                        catCopia.botoes.addAll(botoesPorSlug)
                    } else if (catCopia.botoes.isNotEmpty()) {
                        salvarListaLocalmente(catCopia.id, catCopia.botoes)
                    }
                    catCopia
                }
                MidiaLocalHelper.salvarCategoriasAtivasLocalmente(this, listaComLocais)
                adapter.atualizarDados(listaComLocais)
                adapter.notifyDataSetChanged()
                txtSemCategorias.visibility = View.GONE
                rvCategorias.visibility = View.VISIBLE
                atualizarVisibilidadeBotoes()
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
    // CAPTURA DE FOTO VIA CÂMERA (FILEPROVIDER VINCULADO AO ARMAZENAMENTO INTERNO)
    // ==========================================

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

    // ==========================================
    // REPRODUÇÃO DE ÁUDIO REAL
    // ==========================================

    private fun tocarAudioCategoria(categoria: Categoria) {
        val genero = MidiaLocalHelper.obterGeneroCategoria(this, categoria.id)
        AudioDspHelper.configurarRoteamentoAltoFalante(this, paraGravacao = false)

        val audioLocal = MidiaLocalHelper.obterAudioLocal(this, categoria.id)
        if (audioLocal != null && audioLocal.exists() && audioLocal.length() > 0) {
            try {
                liberarMediaPlayer()
                mediaPlayer = MediaPlayer().apply {
                    AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                    setDataSource(audioLocal.absolutePath)
                    prepare()
                    setVolume(1.0f, 1.0f)
                    start()
                }
                Toast.makeText(this, "Reproduzindo: ${categoria.nome}", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                liberarMediaPlayer()
            }
        }

        val audioUrl = categoria.audioUrl?.takeIf { it.isNotBlank() && (it.startsWith("http") || File(it).exists()) }
            ?: categoria.audioPath?.takeIf { it.isNotBlank() && (it.startsWith("http") || File(it).exists()) }

        if (!audioUrl.isNullOrBlank()) {
            try {
                liberarMediaPlayer()
                mediaPlayer = MediaPlayer().apply {
                    AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                    setDataSource(audioUrl)
                    prepareAsync()
                    setOnPreparedListener {
                        it.setVolume(1.0f, 1.0f)
                        it.start()
                    }
                }
                Toast.makeText(this, "Reproduzindo: ${categoria.nome}", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                liberarMediaPlayer()
            }
        }

        AudioPredefinidoHelper.obterAudioVozNeural(this, categoria.nome, genero) { audioGerado ->
            runOnUiThread {
                if (audioGerado != null && audioGerado.exists() && audioGerado.length() > 0) {
                    try {
                        liberarMediaPlayer()
                        mediaPlayer = MediaPlayer().apply {
                            AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                            setDataSource(audioGerado.absolutePath)
                            prepare()
                            setVolume(1.0f, 1.0f)
                            start()
                        }
                    } catch (_: Exception) {
                        if (ttsReady && textToSpeech != null) {
                            AudioPredefinidoHelper.falarPrevia(textToSpeech, categoria.nome, genero, this@CategoriaGeralActivity)
                        }
                    }
                } else if (ttsReady && textToSpeech != null) {
                    AudioPredefinidoHelper.falarPrevia(textToSpeech, categoria.nome, genero, this@CategoriaGeralActivity)
                }
            }
        }
        Toast.makeText(this, "Reproduzindo: ${categoria.nome}", Toast.LENGTH_SHORT).show()
    }

    fun tocarAudioItem(item: CategoriaItem) {
        val genero = MidiaLocalHelper.obterGeneroBotao(this, item.id, item.defaultGender)
        AudioDspHelper.configurarRoteamentoAltoFalante(this, paraGravacao = false)

        val audioLocal = MidiaLocalHelper.obterAudioLocal(this, item.id)
        if (audioLocal != null && audioLocal.exists() && audioLocal.length() > 0) {
            try {
                liberarMediaPlayer()
                mediaPlayer = MediaPlayer().apply {
                    AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                    setDataSource(audioLocal.absolutePath)
                    prepare()
                    setVolume(1.0f, 1.0f)
                    start()
                }
                Toast.makeText(this, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                liberarMediaPlayer()
            }
        }

        val audioUrl = item.audioUrl?.takeIf { it.isNotBlank() && (it.startsWith("http") || File(it).exists()) }
            ?: item.audioPath?.takeIf { it.isNotBlank() && (it.startsWith("http") || File(it).exists()) }

        if (!audioUrl.isNullOrBlank()) {
            try {
                liberarMediaPlayer()
                mediaPlayer = MediaPlayer().apply {
                    AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                    setDataSource(audioUrl)
                    prepareAsync()
                    setOnPreparedListener {
                        it.setVolume(1.0f, 1.0f)
                        it.start()
                    }
                }
                Toast.makeText(this, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                liberarMediaPlayer()
            }
        }

        AudioPredefinidoHelper.obterAudioVozNeural(this, item.rotulo, genero) { audioGerado ->
            runOnUiThread {
                if (audioGerado != null && audioGerado.exists() && audioGerado.length() > 0) {
                    try {
                        liberarMediaPlayer()
                        mediaPlayer = MediaPlayer().apply {
                            AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                            setDataSource(audioGerado.absolutePath)
                            prepare()
                            setVolume(1.0f, 1.0f)
                            start()
                        }
                    } catch (_: Exception) {
                        if (ttsReady && textToSpeech != null) {
                            AudioPredefinidoHelper.falarPrevia(textToSpeech, item.rotulo, genero, this@CategoriaGeralActivity)
                        }
                    }
                } else if (ttsReady && textToSpeech != null) {
                    AudioPredefinidoHelper.falarPrevia(textToSpeech, item.rotulo, genero, this@CategoriaGeralActivity)
                }
            }
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
                AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
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
                setVolume(1.0f, 1.0f)
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
        if (adapter.obterCategorias().size >= CategoriaRepository.MAX_CATEGORIAS) {
            Toast.makeText(this, CategoriaRepository.MSG_MAX_CATEGORIAS, Toast.LENGTH_SHORT).show()
            atualizarVisibilidadeBotoes()
            return
        }

        uriImagemSelecionada = null
        uriAudioSelecionado = null
        arquivoAudioGravado = null
        desenhoSelecionado = null

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_form_categoria, null)
        val tvModalTitle = dialogView.findViewById<TextView>(R.id.tvModalTitle)
        val etCategoryName = dialogView.findViewById<EditText>(R.id.etCategoryName)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.ivCategoryPreview)
        val toggleModoCategoria = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleModoCategoria)
        val btnModoCatPredefinida = dialogView.findViewById<MaterialButton>(R.id.btnModoCatPredefinida)
        val containerCatPredefinida = dialogView.findViewById<View>(R.id.containerCatPredefinida)
        val containerCatPersonalizada = dialogView.findViewById<View>(R.id.containerCatPersonalizada)
        val spnCategoriaPredefinida = dialogView.findViewById<Spinner>(R.id.spnCategoriaPredefinida)

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

        val toggleGeneroAudio = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleGeneroAudioCategoria)
        var generoAudioCategoria = MidiaLocalHelper.obterPreferenciaVozGlobal(this)
        if (generoAudioCategoria == GeneroAudio.MALE) {
            toggleGeneroAudio?.check(R.id.btnCatAudioMasculino)
        } else {
            toggleGeneroAudio?.check(R.id.btnCatAudioFeminino)
        }

        ivPreviewModalAtivo = ivPreview
        txtStatusAudioModalAtivo = txtStatusAudio
        btnPararGravacaoAtivo = btnPararGravacao
        btnOuvirPreviaAtivo = btnPreviaAudio
        btnGravarAudioAtivo = btnGravarAudio
        progressModalAtivo = progressBarModal

        tvModalTitle?.text = "Criar Nova Categoria"

        // 1. Filtrar categorias predefinidas para NÃO mostrar as já adicionadas
        val categoriasExistentes = adapter.obterCategorias()
        val nomesExistentes = categoriasExistentes.map { AudioPredefinidoHelper.normalizarTexto(it.nome) }.toSet()
        val slugsExistentes = categoriasExistentes.map { AudioPredefinidoHelper.normalizarTexto(it.slug) }.toSet()

        val disponiveisPredefinidas = categoriasPredefinidasOficiais.filter { pre ->
            val n = AudioPredefinidoHelper.normalizarTexto(pre.nome)
            val s = AudioPredefinidoHelper.normalizarTexto(pre.id)
            !nomesExistentes.contains(n) && !slugsExistentes.contains(s)
        }

        val nomesPredefinidos = disponiveisPredefinidas.map { it.nome }
        val adapterPredefinidas = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nomesPredefinidos)
        spnCategoriaPredefinida?.adapter = adapterPredefinidas

        fun obterNomeDigitadoOuPredefinido(): String {
            val digitado = etCategoryName?.text?.toString()?.trim() ?: ""
            if (digitado.isNotBlank()) return digitado
            val isPre = toggleModoCategoria?.checkedButtonId == R.id.btnModoCatPredefinida
            val pos = spnCategoriaPredefinida?.selectedItemPosition ?: 0
            val pre = if (isPre && pos in disponiveisPredefinidas.indices) disponiveisPredefinidas[pos] else null
            return pre?.nome ?: ""
        }

        fun atualizarStatusAudio() {
            val rotuloAtual = obterNomeDigitadoOuPredefinido()
            val genNome = if (generoAudioCategoria == GeneroAudio.MALE) "masculino" else "feminino"

            when {
                arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                    txtStatusAudio?.text = "Voz gravada no dispositivo (${arquivoAudioGravado!!.name})"
                    btnPreviaAudio?.visibility = View.VISIBLE
                }
                uriAudioSelecionado != null -> {
                    txtStatusAudio?.text = "Áudio local selecionado do dispositivo."
                    btnPreviaAudio?.visibility = View.VISIBLE
                }
                else -> {
                    val rotuloExibir = rotuloAtual.ifBlank { "categoria" }
                    txtStatusAudio?.text = "Áudio padrão ($genNome) para \"$rotuloExibir\"."
                    btnPreviaAudio?.visibility = View.VISIBLE
                }
            }
        }

        fun aplicarCategoriaPredefinida(pos: Int) {
            if (pos in disponiveisPredefinidas.indices) {
                val pre = disponiveisPredefinidas[pos]
                etCategoryName?.setText(pre.nome)
                if (uriImagemSelecionada == null && desenhoSelecionado == null) {
                    ivPreview?.clearColorFilter()
                    ivPreview?.imageTintList = null
                    ivPreview?.setImageResource(pre.drawableRes)
                }
                atualizarStatusAudio()
            }
        }

        // O campo de texto do título da categoria permanece SEMPRE visível e editável!
        containerCatPersonalizada?.visibility = View.VISIBLE

        if (disponiveisPredefinidas.isEmpty()) {
            btnModoCatPredefinida?.isEnabled = false
            toggleModoCategoria?.check(R.id.btnModoCatPersonalizada)
            containerCatPredefinida?.visibility = View.GONE
            etCategoryName?.setText("")
            ivPreview?.setImageResource(R.drawable.ic_default_button)
        } else {
            toggleModoCategoria?.check(R.id.btnModoCatPredefinida)
            containerCatPredefinida?.visibility = View.VISIBLE
            aplicarCategoriaPredefinida(0)
        }

        spnCategoriaPredefinida?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (toggleModoCategoria?.checkedButtonId == R.id.btnModoCatPredefinida) {
                    aplicarCategoriaPredefinida(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        toggleModoCategoria?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                containerCatPersonalizada?.visibility = View.VISIBLE
                if (checkedId == R.id.btnModoCatPredefinida) {
                    containerCatPredefinida?.visibility = View.VISIBLE
                    aplicarCategoriaPredefinida(spnCategoriaPredefinida?.selectedItemPosition ?: 0)
                } else {
                    containerCatPredefinida?.visibility = View.GONE
                }
                atualizarStatusAudio()
            }
        }

        toggleGeneroAudio?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                generoAudioCategoria = if (checkedId == R.id.btnCatAudioMasculino) GeneroAudio.MALE else GeneroAudio.FEMALE
                MidiaLocalHelper.salvarPreferenciaVozGlobal(this, generoAudioCategoria)
                textToSpeech?.let { AudioPredefinidoHelper.aplicarConfiguracaoVoz(it, generoAudioCategoria) }
                atualizarStatusAudio()
                val nomeAlvo = obterNomeDigitadoOuPredefinido()
                if (nomeAlvo.isNotBlank() && arquivoAudioGravado == null && uriAudioSelecionado == null) {
                    AudioPredefinidoHelper.obterAudioVozNeural(this@CategoriaGeralActivity, nomeAlvo, generoAudioCategoria) { audioNeural ->
                        runOnUiThread {
                            if (audioNeural != null && audioNeural.exists()) {
                                liberarPreviewPlayer()
                                try {
                                    previewPlayer = MediaPlayer().apply {
                                        AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                                        setDataSource(audioNeural.absolutePath)
                                        prepare()
                                        setVolume(1.0f, 1.0f)
                                        start()
                                        setOnCompletionListener { liberarPreviewPlayer() }
                                    }
                                } catch (_: Exception) {
                                    AudioPredefinidoHelper.falarPrevia(textToSpeech, nomeAlvo, generoAudioCategoria, this@CategoriaGeralActivity)
                                }
                            } else {
                                AudioPredefinidoHelper.falarPrevia(textToSpeech, nomeAlvo, generoAudioCategoria, this@CategoriaGeralActivity)
                            }
                        }
                    }
                }
            }
        }

        etCategoryName?.doAfterTextChanged {
            atualizarStatusAudio()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnVoltarModal)?.setOnClickListener { view ->
            dispararAudioCue(view)
            dialog.dismiss()
        }

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
            val isPre = toggleModoCategoria?.checkedButtonId == R.id.btnModoCatPredefinida
            val pos = spnCategoriaPredefinida?.selectedItemPosition ?: 0
            val pre = if (isPre && pos in disponiveisPredefinidas.indices) disponiveisPredefinidas[pos] else null
            uriImagemSelecionada = null
            desenhoSelecionado = null
            ivPreview?.clearColorFilter()
            ivPreview?.imageTintList = null
            if (pre != null) {
                ivPreview?.setImageResource(pre.drawableRes)
            } else {
                ivPreview?.setImageResource(R.drawable.ic_default_button)
            }
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
            when {
                arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                    reproduzirPreviaAudio()
                }
                uriAudioSelecionado != null -> {
                    reproduzirPreviaAudio()
                }
                else -> {
                    val nomeAlvo = obterNomeDigitadoOuPredefinido()

                    if (nomeAlvo.isNotBlank()) {
                        AudioPredefinidoHelper.obterAudioVozNeural(this@CategoriaGeralActivity, nomeAlvo, generoAudioCategoria) { audioNeural ->
                            runOnUiThread {
                                if (audioNeural != null && audioNeural.exists()) {
                                    liberarPreviewPlayer()
                                    try {
                                        previewPlayer = MediaPlayer().apply {
                                            AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                                            setDataSource(audioNeural.absolutePath)
                                            prepare()
                                            setVolume(1.0f, 1.0f)
                                            start()
                                            setOnCompletionListener { liberarPreviewPlayer() }
                                        }
                                    } catch (e: Exception) {
                                        AudioPredefinidoHelper.falarPrevia(textToSpeech, nomeAlvo, generoAudioCategoria, this@CategoriaGeralActivity)
                                    }
                                } else {
                                    AudioPredefinidoHelper.falarPrevia(textToSpeech, nomeAlvo, generoAudioCategoria, this@CategoriaGeralActivity)
                                }
                            }
                        }
                    }
                }
            }
        }

        btnSaveCategory?.setOnClickListener {
            dispararAudioCue(it)

            val isPredefinida = toggleModoCategoria?.checkedButtonId == R.id.btnModoCatPredefinida
            val posPre = spnCategoriaPredefinida?.selectedItemPosition ?: 0
            val preEscolhida = if (isPredefinida && posPre in disponiveisPredefinidas.indices) {
                disponiveisPredefinidas[posPre]
            } else null

            // Sempre respeita o título digitado pelo usuário em etCategoryName!
            val nome = obterNomeDigitadoOuPredefinido()
            if (nome.isBlank()) {
                etCategoryName?.error = "Informe o nome da categoria"
                return@setOnClickListener
            }

            val normNome = AudioPredefinidoHelper.normalizarTexto(nome)
            val jaExiste = categoriasExistentes.any {
                AudioPredefinidoHelper.normalizarTexto(it.nome) == normNome
            }
            if (jaExiste) {
                Toast.makeText(this@CategoriaGeralActivity, "Já existe uma categoria cadastrada com o nome \"$nome\". Apenas uma de cada é permitida.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (isGravando) {
                pararGravacaoAudioNativa()
            }

            btnSaveCategory.isEnabled = false
            progressBarModal?.visibility = View.VISIBLE

            val corFinal = preEscolhida?.cor ?: "#2563EB"
            val slug = preEscolhida?.id ?: nome.lowercase().replace(" ", "_")

            MidiaLocalHelper.salvarGeneroCategoria(this@CategoriaGeralActivity, catId, generoAudioCategoria)

            if (preEscolhida != null && uriImagemSelecionada == null && desenhoSelecionado == null) {
                MidiaLocalHelper.salvarImagemLocalmente(this@CategoriaGeralActivity, catId, preEscolhida.drawableRes)
            }

            fun persistirEFechar() {
                fazerUploadMidiasESalvarCategoria(
                    categoriaId = catId,
                    nome = nome,
                    slug = slug,
                    cor = corFinal,
                    ordem = adapter.itemCount,
                    imagemUrlExistente = null,
                    audioUrlExistente = null,
                    onComplete = { _, _ ->
                        temAlteracoesNaoSalvas = true
                        adapter.notifyDataSetChanged()
                        atualizarVisibilidadeBotoes()
                        dialog.dismiss()
                    }
                )
            }

            if (arquivoAudioGravado == null && uriAudioSelecionado == null) {
                val dir = getExternalFilesDir("categorias_audios") ?: filesDir
                val arqDestino = File(dir, "cat_${catId}_${generoAudioCategoria.name.lowercase()}.mp3")
                AudioPredefinidoHelper.sintetizarVozOficialParaArquivo(
                    this@CategoriaGeralActivity,
                    textToSpeech,
                    nome,
                    generoAudioCategoria,
                    arqDestino
                ) { arqSintetizado ->
                    runOnUiThread {
                        if (arqSintetizado != null) {
                            MidiaLocalHelper.salvarAudioLocalmente(this@CategoriaGeralActivity, catId, arqSintetizado)
                        }
                        persistirEFechar()
                    }
                }
            } else {
                persistirEFechar()
            }
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
        val toggleModoCategoria = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleModoCategoria)
        val btnModoCatPredefinida = dialogView.findViewById<MaterialButton>(R.id.btnModoCatPredefinida)
        val containerCatPredefinida = dialogView.findViewById<View>(R.id.containerCatPredefinida)
        val containerCatPersonalizada = dialogView.findViewById<View>(R.id.containerCatPersonalizada)
        val spnCategoriaPredefinida = dialogView.findViewById<Spinner>(R.id.spnCategoriaPredefinida)

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

        val toggleGeneroAudio = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleGeneroAudioCategoria)
        var generoAudioCategoria = MidiaLocalHelper.obterGeneroCategoria(this, categoria.id)
        var alterouGeneroOuNomeCategoria = false
        if (generoAudioCategoria == GeneroAudio.MALE) {
            toggleGeneroAudio?.check(R.id.btnCatAudioMasculino)
        } else {
            toggleGeneroAudio?.check(R.id.btnCatAudioFeminino)
        }

        ivPreviewModalAtivo = ivPreview
        txtStatusAudioModalAtivo = txtStatusAudio
        btnPararGravacaoAtivo = btnPararGravacao
        btnOuvirPreviaAtivo = btnPreviaAudio
        btnGravarAudioAtivo = btnGravarAudio
        progressModalAtivo = progressBarModal

        tvModalTitle?.text = "Editar Categoria"
        etCategoryName?.setText(categoria.nome)

        // 1. Filtrar opções predefinidas não utilizadas por outras categorias
        val categoriasExistentes = adapter.obterCategorias().filter { it.id != categoria.id }
        val nomesExistentes = categoriasExistentes.map { AudioPredefinidoHelper.normalizarTexto(it.nome) }.toSet()
        val slugsExistentes = categoriasExistentes.map { AudioPredefinidoHelper.normalizarTexto(it.slug) }.toSet()

        val disponiveisPredefinidas = categoriasPredefinidasOficiais.filter { pre ->
            val n = AudioPredefinidoHelper.normalizarTexto(pre.nome)
            val s = AudioPredefinidoHelper.normalizarTexto(pre.id)
            val ehAtual = categoria.nome.equals(pre.nome, true) || categoria.slug.equals(pre.id, true)
            ehAtual || (!nomesExistentes.contains(n) && !slugsExistentes.contains(s))
        }

        val nomesPredefinidos = disponiveisPredefinidas.map { it.nome }
        val adapterPredefinidas = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nomesPredefinidos)
        spnCategoriaPredefinida?.adapter = adapterPredefinidas

        val localExistenteInicial = MidiaLocalHelper.obterArquivoLocal(this, categoria.id)
        val idxPre = disponiveisPredefinidas.indexOfFirst {
            it.nome.equals(categoria.nome, true) || it.id.equals(categoria.slug, true)
        }
        // O campo de texto do título da categoria permanece SEMPRE visível e editável!
        containerCatPersonalizada?.visibility = View.VISIBLE
        var inicializandoSpinnerEdicao = true

        if (idxPre != -1 && localExistenteInicial == null) {
            spnCategoriaPredefinida?.setSelection(idxPre)
            toggleModoCategoria?.check(R.id.btnModoCatPredefinida)
            containerCatPredefinida?.visibility = View.VISIBLE
        } else {
            if (disponiveisPredefinidas.isEmpty()) {
                btnModoCatPredefinida?.isEnabled = false
            }
            toggleModoCategoria?.check(R.id.btnModoCatPersonalizada)
            containerCatPredefinida?.visibility = View.GONE
        }

        fun obterNomeEdicaoAtual(): String {
            val digitado = etCategoryName?.text?.toString()?.trim() ?: ""
            if (digitado.isNotBlank()) return digitado
            val isPre = toggleModoCategoria?.checkedButtonId == R.id.btnModoCatPredefinida
            val pos = spnCategoriaPredefinida?.selectedItemPosition ?: 0
            val pre = if (isPre && pos in disponiveisPredefinidas.indices) disponiveisPredefinidas[pos] else null
            return pre?.nome ?: categoria.nome
        }

        fun atualizarStatusAudioEdicao() {
            val rotuloAtual = obterNomeEdicaoAtual()
            val genNome = if (generoAudioCategoria == GeneroAudio.MALE) "masculino" else "feminino"

            when {
                arquivoAudioGravado != null && arquivoAudioGravado!!.exists() -> {
                    txtStatusAudio?.text = "Voz gravada no dispositivo (${arquivoAudioGravado!!.name})"
                    btnPreviaAudio?.visibility = View.VISIBLE
                }
                uriAudioSelecionado != null -> {
                    txtStatusAudio?.text = "Áudio local selecionado do dispositivo."
                    btnPreviaAudio?.visibility = View.VISIBLE
                }
                else -> {
                    val rotuloExibir = rotuloAtual.ifBlank { "categoria" }
                    txtStatusAudio?.text = "Áudio padrão ($genNome) para \"$rotuloExibir\"."
                    btnPreviaAudio?.visibility = View.VISIBLE
                }
            }
        }

        fun aplicarCategoriaPredefinidaEdicao(pos: Int) {
            if (pos in disponiveisPredefinidas.indices) {
                val pre = disponiveisPredefinidas[pos]
                etCategoryName?.setText(pre.nome)
                if (uriImagemSelecionada == null && desenhoSelecionado == null) {
                    ivPreview?.clearColorFilter()
                    ivPreview?.imageTintList = null
                    ivPreview?.setImageResource(pre.drawableRes)
                }
                atualizarStatusAudioEdicao()
            }
        }

        spnCategoriaPredefinida?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (inicializandoSpinnerEdicao) {
                    inicializandoSpinnerEdicao = false
                    return
                }
                if (toggleModoCategoria?.checkedButtonId == R.id.btnModoCatPredefinida) {
                    aplicarCategoriaPredefinidaEdicao(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        toggleModoCategoria?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                containerCatPersonalizada?.visibility = View.VISIBLE
                if (checkedId == R.id.btnModoCatPredefinida) {
                    containerCatPredefinida?.visibility = View.VISIBLE
                    aplicarCategoriaPredefinidaEdicao(spnCategoriaPredefinida?.selectedItemPosition ?: 0)
                } else {
                    containerCatPredefinida?.visibility = View.GONE
                }
                atualizarStatusAudioEdicao()
            }
        }

        toggleGeneroAudio?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val novoGen = if (checkedId == R.id.btnCatAudioMasculino) GeneroAudio.MALE else GeneroAudio.FEMALE
                if (novoGen != generoAudioCategoria) {
                    alterouGeneroOuNomeCategoria = true
                    if (uriAudioSelecionado == null && (arquivoAudioGravado == null || !arquivoAudioGravado!!.name.endsWith(".wav", true))) {
                        arquivoAudioGravado = null
                        categoria.audioPath = null
                        categoria.audioUrl = null
                        MidiaLocalHelper.limparAudiosAntigos(this, categoria.id)
                    }
                }
                generoAudioCategoria = novoGen
                MidiaLocalHelper.salvarGeneroCategoria(this, categoria.id, generoAudioCategoria)
                textToSpeech?.let { AudioPredefinidoHelper.aplicarConfiguracaoVoz(it, generoAudioCategoria) }
                atualizarStatusAudioEdicao()

                val nomeAlvo = obterNomeEdicaoAtual()
                if (nomeAlvo.isNotBlank() && arquivoAudioGravado == null && uriAudioSelecionado == null) {
                    AudioPredefinidoHelper.obterAudioVozNeural(this@CategoriaGeralActivity, nomeAlvo, generoAudioCategoria) { audioNeural ->
                        runOnUiThread {
                            if (audioNeural != null && audioNeural.exists()) {
                                liberarPreviewPlayer()
                                try {
                                    previewPlayer = MediaPlayer().apply {
                                        AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                                        setDataSource(audioNeural.absolutePath)
                                        prepare()
                                        setVolume(1.0f, 1.0f)
                                        start()
                                        setOnCompletionListener { liberarPreviewPlayer() }
                                    }
                                } catch (_: Exception) {
                                    AudioPredefinidoHelper.falarPrevia(textToSpeech, nomeAlvo, generoAudioCategoria, this@CategoriaGeralActivity)
                                }
                            } else {
                                AudioPredefinidoHelper.falarPrevia(textToSpeech, nomeAlvo, generoAudioCategoria, this@CategoriaGeralActivity)
                            }
                        }
                    }
                }
            }
        }

        etCategoryName?.doAfterTextChanged {
            if (!inicializandoSpinnerEdicao) {
                alterouGeneroOuNomeCategoria = true
            }
            atualizarStatusAudioEdicao()
        }

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
        val reqPreview = Glide.with(this)
            .load(imgAlvo)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .centerCrop()
            .placeholder(R.drawable.ic_default_button)
            .error(R.drawable.ic_default_button)
        if (imgAlvo is File) {
            reqPreview.signature(ObjectKey("${imgAlvo.absolutePath}_${imgAlvo.lastModified()}"))
        }
        reqPreview.into(ivPreview)

        atualizarStatusAudioEdicao()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnVoltarModal)?.setOnClickListener { view ->
            dispararAudioCue(view)
            dialog.dismiss()
        }

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
            val isPre = toggleModoCategoria?.checkedButtonId == R.id.btnModoCatPredefinida
            val pos = spnCategoriaPredefinida?.selectedItemPosition ?: 0
            val pre = if (isPre && pos in disponiveisPredefinidas.indices) disponiveisPredefinidas[pos] else null
            uriImagemSelecionada = null
            desenhoSelecionado = null
            categoria.imageUri = null
            categoria.imageUrl = null
            categoria.localPath = null
            MidiaLocalHelper.limparImagemLocal(this, categoria.id)
            ivPreview?.clearColorFilter()
            ivPreview?.imageTintList = null
            if (pre != null) {
                ivPreview?.setImageResource(pre.drawableRes)
            } else {
                ivPreview?.setImageResource(R.drawable.ic_default_button)
            }
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
            when {
                arquivoAudioGravado != null || uriAudioSelecionado != null -> {
                    reproduzirPreviaAudio()
                }
                else -> {
                    val nomeAlvo = obterNomeEdicaoAtual()

                    if (nomeAlvo.isNotBlank()) {
                        AudioPredefinidoHelper.obterAudioVozNeural(this@CategoriaGeralActivity, nomeAlvo, generoAudioCategoria) { audioNeural ->
                            runOnUiThread {
                                if (audioNeural != null && audioNeural.exists()) {
                                    liberarPreviewPlayer()
                                    try {
                                        previewPlayer = MediaPlayer().apply {
                                            AudioDspHelper.configurarMediaPlayerAltoVolume(this@CategoriaGeralActivity, this)
                                            setDataSource(audioNeural.absolutePath)
                                            prepare()
                                            setVolume(1.0f, 1.0f)
                                            start()
                                            setOnCompletionListener { liberarPreviewPlayer() }
                                        }
                                    } catch (e: Exception) {
                                        AudioPredefinidoHelper.falarPrevia(textToSpeech, nomeAlvo, generoAudioCategoria, this@CategoriaGeralActivity)
                                    }
                                } else {
                                    AudioPredefinidoHelper.falarPrevia(textToSpeech, nomeAlvo, generoAudioCategoria, this@CategoriaGeralActivity)
                                }
                            }
                        }
                    }
                }
            }
        }

        btnSaveCategory?.setOnClickListener {
            dispararAudioCue(it)

            val isPredefinida = toggleModoCategoria?.checkedButtonId == R.id.btnModoCatPredefinida
            val posPre = spnCategoriaPredefinida?.selectedItemPosition ?: 0
            val preEscolhida = if (isPredefinida && posPre in disponiveisPredefinidas.indices) {
                disponiveisPredefinidas[posPre]
            } else null

            // Sempre utiliza o título personalizado digitado no campo etCategoryName!
            val novoNome = obterNomeEdicaoAtual()
            if (novoNome.isBlank()) {
                etCategoryName?.error = "Informe o nome da categoria"
                return@setOnClickListener
            }

            val normNome = AudioPredefinidoHelper.normalizarTexto(novoNome)
            val jaExiste = categoriasExistentes.any {
                AudioPredefinidoHelper.normalizarTexto(it.nome) == normNome
            }
            if (jaExiste) {
                Toast.makeText(this@CategoriaGeralActivity, "Já existe outra categoria com o nome \"$novoNome\". Apenas uma de cada é permitida.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (isGravando) {
                pararGravacaoAudioNativa()
            }

            btnSaveCategory.isEnabled = false
            progressBarModal?.visibility = View.VISIBLE

            val novoSlug = preEscolhida?.id ?: novoNome.lowercase().replace(" ", "_")
            val corFinal = preEscolhida?.cor ?: categoria.cor

            MidiaLocalHelper.salvarGeneroCategoria(this@CategoriaGeralActivity, categoria.id, generoAudioCategoria)

            if (preEscolhida != null && uriImagemSelecionada == null && desenhoSelecionado == null && MidiaLocalHelper.obterArquivoLocal(this@CategoriaGeralActivity, categoria.id) == null) {
                MidiaLocalHelper.salvarImagemLocalmente(this@CategoriaGeralActivity, categoria.id, preEscolhida.drawableRes)
            }

            fun persistirEFecharEdicao() {
                fazerUploadMidiasESalvarCategoria(
                    categoriaId = categoria.id,
                    nome = novoNome,
                    slug = novoSlug,
                    cor = corFinal,
                    ordem = categoria.ordem,
                    imagemUrlExistente = categoria.imageUrl ?: categoria.imageUri,
                    audioUrlExistente = if (alterouGeneroOuNomeCategoria) null else (categoria.audioUrl ?: categoria.audioPath),
                    onComplete = { novaImg, novoAudio ->
                        temAlteracoesNaoSalvas = true
                        categoria.nome = novoNome
                        categoria.slug = novoSlug
                        categoria.cor = corFinal
                        if (novaImg.isNotBlank()) {
                            categoria.imageUrl = novaImg
                            categoria.imageUri = novaImg
                            categoria.localPath = novaImg
                        }
                        if (novoAudio.isNotBlank()) {
                            categoria.audioUrl = novoAudio
                            categoria.audioPath = novoAudio
                        }
                        adapter.atualizarItem(categoria)
                        MidiaLocalHelper.salvarCategoriasAtivasLocalmente(this@CategoriaGeralActivity, adapter.obterCategorias())
                        adapter.notifyDataSetChanged()
                        atualizarVisibilidadeBotoes()
                        dialog.dismiss()
                    }
                )
            }

            if (arquivoAudioGravado == null && uriAudioSelecionado == null && (alterouGeneroOuNomeCategoria || categoria.audioUrl.isNullOrBlank() && categoria.audioPath.isNullOrBlank())) {
                if (alterouGeneroOuNomeCategoria) {
                    MidiaLocalHelper.limparAudiosAntigos(this@CategoriaGeralActivity, categoria.id)
                }
                val dir = getExternalFilesDir("categorias_audios") ?: filesDir
                val arqDestino = File(dir, "cat_${categoria.id}_${generoAudioCategoria.name.lowercase()}.mp3")
                AudioPredefinidoHelper.sintetizarVozOficialParaArquivo(
                    this@CategoriaGeralActivity,
                    textToSpeech,
                    novoNome,
                    generoAudioCategoria,
                    arqDestino
                ) { arqSintetizado ->
                    runOnUiThread {
                        if (arqSintetizado != null) {
                            MidiaLocalHelper.salvarAudioLocalmente(this@CategoriaGeralActivity, categoria.id, arqSintetizado)
                        }
                        persistirEFecharEdicao()
                    }
                }
            } else {
                persistirEFecharEdicao()
            }
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

        val catExistente = adapter.obterCategorias().find { it.id == categoriaId }
        val botoesBase = when {
            MidiaLocalHelper.temBotoesSalvosLocalmente(this, categoriaId) -> carregarListaLocalmente(categoriaId)
            catExistente != null && catExistente.botoes.isNotEmpty() -> catExistente.botoes.toMutableList()
            else -> {
                val catPadrao = CategoriaRepository.getCategoriasPadrao().find {
                    it.id.equals(categoriaId, ignoreCase = true) ||
                        it.slug.equals(slug, ignoreCase = true) ||
                        it.nome.equals(nome, ignoreCase = true)
                }
                catPadrao?.botoes?.map { it.copy() }?.toMutableList() ?: mutableListOf()
            }
        }
        val botoesValidados = MidiaLocalHelper.filtrarBotoesSemConflitoGlobal(this, botoesBase, categoriaId, nome)
        salvarListaLocalmente(categoriaId, botoesValidados)

        val categoriaAtualizada = Categoria(
            id = categoriaId,
            nome = nome,
            slug = slug,
            cor = cor,
            ordem = ordem,
            imagem_id = catExistente?.imagem_id,
            audio_id = catExistente?.audio_id,
            botoes = botoesValidados,
            imageUri = localPath ?: imagemUrlExistente,
            imageUrl = localPath ?: imagemUrlExistente,
            localPath = localPath,
            audioPath = localAudioFile?.absolutePath ?: audioUrlExistente,
            audioUrl = localAudioFile?.absolutePath ?: audioUrlExistente
        )

        // Atualiza imediatamente o adapter para alta velocidade e sem bloqueio de rede
        adapter.atualizarItem(categoriaAtualizada)
        MidiaLocalHelper.salvarCategoriasAtivasLocalmente(this, adapter.obterCategorias())
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

                    // Sincronização automática em Modo Desenvolvedor com ESP32 v8
                    lifecycleScope.launch {
                        try {
                            val botoes = carregarListaLocalmente(categoriaId).ifEmpty { categoriaAtualizada.botoes }
                            devSyncService.sincronizarComHardwareDev(
                                context = this@CategoriaGeralActivity,
                                botoes = botoes,
                                corCategoria = cor
                            )
                        } catch (ignored: Exception) {}
                    }
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

    private fun excluirCategoriaImediatamente(categoria: Categoria) {
        temAlteracoesNaoSalvas = true
        MidiaLocalHelper.removerCategoriaLocalmente(this, categoria.id, categoria.slug)
        adapter.removerItem(categoria.id)
        MidiaLocalHelper.salvarCategoriasAtivasLocalmente(this, adapter.obterCategorias())
        viewModel.excluirCategoria(currentUserId, categoria.id)

        firestore.collection("categoria").document(categoria.id).delete()
        firestore.collection("usuarios").document(currentUserId)
            .collection("categorias").document(categoria.id)
            .delete()

        Toast.makeText(this, "Categoria \"${categoria.nome}\" excluída.", Toast.LENGTH_SHORT).show()
        atualizarVisibilidadeBotoes()
    }

    private fun mostrarModalConfirmarExclusao(categoria: Categoria) {
        excluirCategoriaImediatamente(categoria)
    }

    // ==========================================
    // MODAL DE BOTÕES (DELEGAÇÃO COM SUPORTE DUAL GALERIA + CÂMERA E PERSISTÊNCIA LOCAL)
    // ==========================================

    fun mostrarModalEditarBotao(item: CategoriaItem?, categoriaId: String = "", categoriaNome: String = "") {
        val catEncontrada = if (categoriaId.isBlank() && item != null) {
            adapter.obterCategorias().find { cat -> cat.botoes.any { it.id == item.id } }
        } else {
            adapter.obterCategorias().find { it.id == categoriaId }
        }
        val catIdFinal = categoriaId.ifBlank { catEncontrada?.id ?: "" }
        val catNomeFinal = categoriaNome.ifBlank { catEncontrada?.nome ?: "" }

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
            categoriaId = catIdFinal,
            categoriaNome = catNomeFinal,
            onSalvo = { novoBotao ->
                // 1. Adiciona o novo botão à lista local da categoria
                val listaBotoes = carregarListaLocalmente(catIdFinal)
                val idx = listaBotoes.indexOfFirst { it.id == novoBotao.id }
                if (idx != -1) {
                    listaBotoes[idx] = novoBotao
                } else {
                    listaBotoes.add(novoBotao)
                }

                // 2. Persiste a lista atualizada no armazenamento interno do dispositivo
                salvarListaLocalmente(catIdFinal, listaBotoes)

                // 3. Notifica o Adapter para renderizar na hora na RecyclerView
                adapter.adicionarBotaoACategoria(catIdFinal, novoBotao)
                MidiaLocalHelper.salvarCategoriasAtivasLocalmente(this, adapter.obterCategorias())
                adapter.notifyDataSetChanged()
                atualizarVisibilidadeBotoes()
            }
        )
    }

    fun mostrarModalEditarBotao(item: CategoriaItem) {
        mostrarModalEditarBotao(item, "", "")
    }
}
