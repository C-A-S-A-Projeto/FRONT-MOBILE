package fatec.labweb.projetocasa.view

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
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
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.adapter.CategoriaAdapter
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.viewmodel.CategoriaViewModel
import java.io.File

class CategoriaItemActivity : BaseDrawerActivity() {

    private val viewModel: CategoriaViewModel by viewModels()

    override fun getContentLayoutId(): Int = R.layout.activity_categoria_item
    override fun getCurrentMenuItemId(): Int = R.id.menuCategorias

    private lateinit var rvCategorias: RecyclerView
    private lateinit var adapter: CategoriaAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var txtSemCategorias: TextView
    private lateinit var etBuscarCategoria: TextInputEditText

    private var mediaPlayer: MediaPlayer? = null

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    private fun initViews() {
        rvCategorias = findViewById(R.id.rvCategorias)
        progressBar = findViewById(R.id.progressBarCategorias)
        txtSemCategorias = findViewById(R.id.txtSemCategorias)
        etBuscarCategoria = findViewById(R.id.etBuscarCategoria)
    }

    private fun setupRecyclerView() {
        adapter = CategoriaAdapter(
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
            adapter.atualizarLista(lista)
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

    private fun tocarAudioCategoria(categoria: Categoria) {
        val audioAlvo = categoria.audioPath ?: return
        liberarMediaPlayer()

        try {
            when {
                audioAlvo.startsWith("http://") || audioAlvo.startsWith("https://") -> {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(audioAlvo)
                        setOnPreparedListener { start() }
                        prepareAsync()
                    }
                }
                else -> {
                    val local = File(filesDir, "uploads/$audioAlvo")
                    if (local.exists()) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(local.absolutePath)
                            prepare()
                            start()
                        }
                    } else {
                        val storageRef = FirebaseStorage.getInstance().reference.child("uploads/$audioAlvo")
                        storageRef.downloadUrl.addOnSuccessListener { uri ->
                            try {
                                liberarMediaPlayer()
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(this@CategoriaItemActivity, uri)
                                    setOnPreparedListener { start() }
                                    prepareAsync()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {
            liberarMediaPlayer()
        }
    }

    private fun liberarMediaPlayer() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {}
            player.release()
        }
        mediaPlayer = null
    }

    override fun onStop() {
        super.onStop()
        liberarMediaPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        liberarMediaPlayer()
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

            val categoriaAtualizada = categoria.copy(
                nome = novoNome,
                slug = novoNome.lowercase().replace(" ", "_")
            )
            viewModel.salvarCategoria(currentUserId, categoriaAtualizada)
            dialog.dismiss()
        }

        dialog.show()
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
            dialog.dismiss()
        }

        dialog.show()
    }
}
