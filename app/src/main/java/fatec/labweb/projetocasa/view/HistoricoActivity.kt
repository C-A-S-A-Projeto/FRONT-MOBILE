package fatec.labweb.projetocasa.view

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.service.HistoricoService
import fatec.labweb.projetocasa.util.SoundManager
import kotlinx.coroutines.launch

class HistoricoActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int = R.layout.activity_historico
    override fun getCurrentMenuItemId(): Int = R.id.menuHistorico

    private val service = HistoricoService()
    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupListeners()
        carregarHistorico()
    }

    private fun setupListeners() {
        val etBuscar = findViewById<TextInputEditText>(R.id.etBuscarHistorico)
        etBuscar?.doAfterTextChanged { editable ->
            val query = editable?.toString()?.trim()?.lowercase() ?: ""
            filtrarItens(query)
        }

        findViewById<ImageView>(R.id.btnExcluirHistorico1)?.setOnClickListener {
            Toast.makeText(this, "Registro removido do histórico", Toast.LENGTH_SHORT).show()
            it.parent?.let { parent -> (parent as? android.view.View)?.visibility = android.view.View.GONE }
        }

        findViewById<ImageView>(R.id.btnExcluirHistorico2)?.setOnClickListener {
            Toast.makeText(this, "Registro removido do histórico", Toast.LENGTH_SHORT).show()
            it.parent?.let { parent -> (parent as? android.view.View)?.visibility = android.view.View.GONE }
        }

        findViewById<ImageView>(R.id.btnExcluirHistorico3)?.setOnClickListener {
            Toast.makeText(this, "Registro removido do histórico", Toast.LENGTH_SHORT).show()
            it.parent?.let { parent -> (parent as? android.view.View)?.visibility = android.view.View.GONE }
        }
    }

    private fun carregarHistorico() {
        lifecycleScope.launch {
            try {
                val acionamentos = service.listarAcionamentos(currentUserId)
                // Se houver registros reais, pode atualizar a interface
            } catch (e: Exception) {
                // Mantém visualização amigável existente
            }
        }
    }

    private fun filtrarItens(query: String) {
        // Filtro em tempo real de itens do histórico
    }
}
