package fatec.labweb.projetocasa.view

import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import java.util.Locale

open class CategoriaBotaoActivity : BaseDrawerActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_CATEGORIA_ID = "extra_categoria_id"
        const val EXTRA_CATEGORIA_NOME = "extra_categoria_nome"
        const val EXTRA_CATEGORIA_COR = "extra_categoria_cor"
    }

    private val viewModel: CategoriaViewModel by viewModels()
    private val firestore = FirebaseFirestore.getInstance()

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

    // Player de Áudio Nativo e TextToSpeech com ciclo de vida gerenciado
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsPronto: Boolean = false

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

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
            val result = textToSpeech?.setLanguage(Locale("pt", "BR"))
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

    /**
     * Execução de streaming real de arquivos de áudio via MediaPlayer nativo do Android
     */
    protected fun tocarAudioItem(item: CategoriaItem) {
        val audioAlvo = item.audio?.takeIf { it.isNotBlank() }
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
            when {
                // Caso 1: URL HTTP/HTTPS ou Firebase Storage Download URL
                audioAlvo.startsWith("http://") || audioAlvo.startsWith("https://") -> {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(audioAlvo)
                        setOnPreparedListener {
                            start()
                            Toast.makeText(this@CategoriaBotaoActivity, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                        }
                        setOnCompletionListener { liberarMediaPlayer() }
                        setOnErrorListener { _, _, _ ->
                            liberarMediaPlayer()
                            if (ttsPronto) {
                                textToSpeech?.speak(item.rotulo, TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                            true
                        }
                        prepareAsync()
                    }
                }

                // Caso 2: Arquivo local
                else -> {
                    val localUploads = File(filesDir, "uploads/$audioAlvo")
                    val localDireto = File(filesDir, audioAlvo)
                    val arquivoLocal = when {
                        localUploads.exists() -> localUploads
                        localDireto.exists() -> localDireto
                        else -> null
                    }

                    if (arquivoLocal != null) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(arquivoLocal.absolutePath)
                            prepare()
                            start()
                            setOnCompletionListener { liberarMediaPlayer() }
                        }
                        Toast.makeText(this, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                    } else {
                        // Caso 3: Resolve do Firebase Storage
                        val storageRef = if (audioAlvo.contains("/")) {
                            FirebaseStorage.getInstance().reference.child(audioAlvo)
                        } else {
                            FirebaseStorage.getInstance().reference.child("uploads/$audioAlvo")
                        }

                        Toast.makeText(this, "Carregando áudio de ${item.rotulo}...", Toast.LENGTH_SHORT).show()
                        storageRef.downloadUrl.addOnSuccessListener { uri ->
                            try {
                                liberarMediaPlayer()
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(this@CategoriaBotaoActivity, uri)
                                    setOnPreparedListener {
                                        start()
                                        Toast.makeText(this@CategoriaBotaoActivity, "Reproduzindo: ${item.rotulo}", Toast.LENGTH_SHORT).show()
                                    }
                                    setOnCompletionListener { liberarMediaPlayer() }
                                    prepareAsync()
                                }
                            } catch (e: Exception) {
                                if (ttsPronto) textToSpeech?.speak(item.rotulo, TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        }.addOnFailureListener {
                            if (ttsPronto) textToSpeech?.speak(item.rotulo, TextToSpeech.QUEUE_FLUSH, null, null)
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

    override fun onStop() {
        super.onStop()
        liberarMediaPlayer()
        textToSpeech?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        liberarMediaPlayer()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun mostrarModalCriarBotao() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.item_categoria_card, null)
        val txtModalTitle = dialogView.findViewById<TextView>(R.id.txtModalTitle)
        val txtModalSubtitle = dialogView.findViewById<TextView>(R.id.txtModalSubtitle)
        val etTitulo = dialogView.findViewById<EditText>(R.id.etTituloBotao)
        val btnCancelar = dialogView.findViewById<View>(R.id.btnCancelarModal)
        val btnSalvar = dialogView.findViewById<View>(R.id.btnSalvarModal)

        txtModalTitle?.text = "Adicionar Botão"
        txtModalSubtitle?.text = "Categoria: $categoriaNome"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancelar?.setOnClickListener { dialog.dismiss() }

        btnSalvar?.setOnClickListener {
            val rotulo = etTitulo?.text?.toString()?.trim() ?: ""
            if (rotulo.isBlank()) {
                etTitulo?.error = "Informe o título do botão"
                return@setOnClickListener
            }

            val novoItem = CategoriaItem(
                rotulo = rotulo,
                significado = rotulo,
                ordem = adapter.itemCount,
                defaultGender = GeneroAudio.FEMALE
            )

            viewModel.salvarBotaoComMidia(
                context = this,
                categoriaId = categoriaId,
                item = novoItem,
                imagemUri = null,
                audioUri = null,
                userId = currentUserId
            )

            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Modal: Editar Botão
     * Preenchido com os dados atuais e realiza update real no Firestore
     */
    private fun mostrarModalEditarBotao(item: CategoriaItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.item_categoria_card, null)
        val txtModalTitle = dialogView.findViewById<TextView>(R.id.txtModalTitle)
        val txtModalSubtitle = dialogView.findViewById<TextView>(R.id.txtModalSubtitle)
        val etTitulo = dialogView.findViewById<EditText>(R.id.etTituloBotao)
        val btnCancelar = dialogView.findViewById<View>(R.id.btnCancelarModal)
        val btnSalvar = dialogView.findViewById<View>(R.id.btnSalvarModal)

        txtModalTitle?.text = "Editar Botão"
        txtModalSubtitle?.text = "Categoria: $categoriaNome"
        etTitulo?.setText(item.rotulo)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancelar?.setOnClickListener { dialog.dismiss() }

        btnSalvar?.setOnClickListener {
            val novoRotulo = etTitulo?.text?.toString()?.trim() ?: ""
            if (novoRotulo.isBlank()) {
                etTitulo?.error = "Informe o título do botão"
                return@setOnClickListener
            }

            val itemAtualizado = item.copy(
                rotulo = novoRotulo,
                significado = novoRotulo
            )

            // 1. Atualização via ViewModel
            viewModel.salvarBotaoComMidia(
                context = this,
                categoriaId = categoriaId,
                item = itemAtualizado,
                imagemUri = null,
                audioUri = null,
                userId = currentUserId
            )

            // 2. Update real e imediato no Firestore
            val updateMap = mapOf(
                "rotulo" to novoRotulo,
                "significado" to novoRotulo
            )
            firestore.collection("categoria_botao").document(item.id).update(updateMap)

            Toast.makeText(this, "Botão \"$novoRotulo\" atualizado com sucesso!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Modal: Confirmar Exclusão de Botão
     * Executa delete real no documento do Firestore
     */
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
            // 1. Remoção via ViewModel
            viewModel.removerBotao(currentUserId, categoriaId, item.id)

            // 2. Delete real no documento do Firestore
            firestore.collection("categoria_botao").document(item.id).delete()

            Toast.makeText(this, "Botão \"${item.rotulo}\" excluído com sucesso!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
