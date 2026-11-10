package fatec.labweb.projetocasa.view

import android.content.Intent
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
import fatec.labweb.projetocasa.adapter.CategoriaGeralAdapter
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.viewmodel.CategoriaViewModel
import java.io.File
import java.util.Locale

class CategoriaGeralActivity : BaseDrawerActivity(), TextToSpeech.OnInitListener {

    private val viewModel: CategoriaViewModel by viewModels()
    private val firestore = FirebaseFirestore.getInstance()

    override fun getContentLayoutId(): Int = R.layout.activity_categoria_item
    override fun getCurrentMenuItemId(): Int = R.id.menuCategorias

    private lateinit var rvCategorias: RecyclerView
    private lateinit var adapter: CategoriaGeralAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var txtSemCategorias: TextView
    private lateinit var etBuscarCategoria: TextInputEditText

    // Instância gerenciada do MediaPlayer Nativo e TextToSpeech para retorno sonoro imediato
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady: Boolean = false

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

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
            val result = textToSpeech?.setLanguage(Locale("pt", "BR"))
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
        adapter = CategoriaGeralAdapter(
            onAcessarClick = { categoria ->
                abrirBotoesDaCategoria(categoria)
            },
            onPlayAudioClick = { categoria ->
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

    /**
     * Execução de streaming real de áudio via MediaPlayer nativo do Android
     */
    private fun tocarAudioCategoria(categoria: Categoria) {
        val audioUrl = categoria.audioPath?.takeIf { it.isNotBlank() }

        liberarMediaPlayer()

        if (audioUrl.isNullOrBlank()) {
            // Feedback por áudio através de síntese de voz (TTS) nativa
            if (ttsReady && textToSpeech != null) {
                textToSpeech?.speak("Categoria ${categoria.nome}", TextToSpeech.QUEUE_FLUSH, null, "cat_audio_${categoria.id}")
            }
            Toast.makeText(this, "Reproduzindo áudio: ${categoria.nome}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            when {
                // Caso 1: URL remota direta (Firebase Storage / HTTP / HTTPS)
                audioUrl.startsWith("http://") || audioUrl.startsWith("https://") -> {
                    iniciarPlayerRemoto(audioUrl, categoria.nome)
                }

                // Caso 2: Arquivo no armazenamento local / cache
                else -> {
                    val local = File(filesDir, "uploads/$audioUrl")
                    if (local.exists()) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(local.absolutePath)
                            prepare()
                            start()
                            setOnCompletionListener { liberarMediaPlayer() }
                            setOnErrorListener { _, _, _ ->
                                liberarMediaPlayer()
                                true
                            }
                        }
                        Toast.makeText(this, "Reproduzindo áudio: ${categoria.nome}", Toast.LENGTH_SHORT).show()
                    } else {
                        // Caso 3: Resolve URL remota do Firebase Storage
                        val storageRef = if (audioUrl.contains("/")) {
                            FirebaseStorage.getInstance().reference.child(audioUrl)
                        } else {
                            FirebaseStorage.getInstance().reference.child("uploads/$audioUrl")
                        }

                        Toast.makeText(this, "Carregando áudio de ${categoria.nome}...", Toast.LENGTH_SHORT).show()
                        storageRef.downloadUrl.addOnSuccessListener { uri ->
                            iniciarPlayerRemoto(uri.toString(), categoria.nome)
                        }.addOnFailureListener {
                            if (ttsReady) {
                                textToSpeech?.speak(categoria.nome, TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                            Toast.makeText(this, "Reproduzindo: ${categoria.nome}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            liberarMediaPlayer()
            if (ttsReady) {
                textToSpeech?.speak(categoria.nome, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            Toast.makeText(this, "Reproduzindo: ${categoria.nome}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarPlayerRemoto(url: String, nomeCategoria: String) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { player ->
                    player.start()
                    Toast.makeText(this@CategoriaGeralActivity, "Reproduzindo áudio: $nomeCategoria", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener {
                    liberarMediaPlayer()
                }
                setOnErrorListener { _, what, extra ->
                    liberarMediaPlayer()
                    if (ttsReady) {
                        textToSpeech?.speak(nomeCategoria, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            liberarMediaPlayer()
            if (ttsReady) {
                textToSpeech?.speak(nomeCategoria, TextToSpeech.QUEUE_FLUSH, null, null)
            }
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

    private fun abrirBotoesDaCategoria(categoria: Categoria) {
        val intent = Intent(this, CategoriaBotaoActivity::class.java).apply {
            putExtra(CategoriaBotaoActivity.EXTRA_CATEGORIA_ID, categoria.id)
            putExtra(CategoriaBotaoActivity.EXTRA_CATEGORIA_NOME, categoria.nome)
            putExtra(CategoriaBotaoActivity.EXTRA_CATEGORIA_COR, categoria.cor)
        }
        startActivity(intent)
    }

    private fun mostrarModalCriarCategoria() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_form_categoria, null)
        val tvModalTitle = dialogView.findViewById<TextView>(R.id.tvModalTitle)
        val etCategoryName = dialogView.findViewById<EditText>(R.id.etCategoryName)
        val btnSaveCategory = dialogView.findViewById<View>(R.id.btnSaveCategory)

        tvModalTitle?.text = "Criar Nova Categoria"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnSaveCategory?.setOnClickListener {
            val nome = etCategoryName?.text?.toString()?.trim() ?: ""
            if (nome.isBlank()) {
                etCategoryName?.error = "Informe o nome da categoria"
                return@setOnClickListener
            }

            val novaCategoria = Categoria(
                nome = nome,
                slug = nome.lowercase().replace(" ", "_"),
                cor = "#2563EB",
                ordem = adapter.itemCount
            )
            viewModel.salvarCategoria(currentUserId, novaCategoria)
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Modal: Editar Categoria
     * Preenchido com os dados atuais e realiza update real no Firestore
     */
    private fun mostrarModalEditarCategoria(categoria: Categoria) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_form_categoria, null)
        val tvModalTitle = dialogView.findViewById<TextView>(R.id.tvModalTitle)
        val etCategoryName = dialogView.findViewById<EditText>(R.id.etCategoryName)
        val btnSaveCategory = dialogView.findViewById<View>(R.id.btnSaveCategory)

        tvModalTitle?.text = "Editar Categoria"
        etCategoryName?.setText(categoria.nome)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnSaveCategory?.setOnClickListener {
            val novoNome = etCategoryName?.text?.toString()?.trim() ?: ""
            if (novoNome.isBlank()) {
                etCategoryName?.error = "Informe o nome da categoria"
                return@setOnClickListener
            }

            val novoSlug = novoNome.lowercase().replace(" ", "_")
            val categoriaAtualizada = categoria.copy(
                nome = novoNome,
                slug = novoSlug
            )

            // 1. Atualização via ViewModel
            viewModel.salvarCategoria(currentUserId, categoriaAtualizada)

            // 2. Update real e imediato nas coleções do Firestore
            val updateMap = mapOf(
                "nome" to novoNome,
                "slug" to novoSlug
            )
            firestore.collection("categoria").document(categoria.id).update(updateMap)
            firestore.collection("usuarios").document(currentUserId)
                .collection("categorias").document(categoria.id)
                .update(updateMap)

            Toast.makeText(this, "Categoria atualizada com sucesso!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Modal: Confirmar Exclusão de Categoria
     * Executa delete real no documento do Firestore
     */
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
            // 1. Remoção via ViewModel
            viewModel.excluirCategoria(currentUserId, categoria.id)

            // 2. Delete real no documento do Firestore
            firestore.collection("categoria").document(categoria.id).delete()
            firestore.collection("usuarios").document(currentUserId)
                .collection("categorias").document(categoria.id)
                .delete()

            Toast.makeText(this, "Categoria \"${categoria.nome}\" excluída com sucesso!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
