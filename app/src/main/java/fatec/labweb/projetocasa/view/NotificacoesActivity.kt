package fatec.labweb.projetocasa.view

import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.R

class NotificacoesActivity : BaseDrawerActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var rvNotifications: RecyclerView

    override fun getContentLayoutId(): Int {
        return R.layout.activity_notifications
    }

    // Notificações não é um item do menu lateral, então nenhum item fica destacado
    override fun getCurrentMenuItemId(): Int {
        return -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = FirebaseFirestore.getInstance()

        rvNotifications = findViewById(R.id.rvNotifications)
        rvNotifications.layoutManager = LinearLayoutManager(this)

        loadNotifications()
    }

    private fun loadNotifications() {
        db.collection("notifications")
            .get()
            .addOnSuccessListener { documents ->
                val notificationList = mutableListOf<String>()

                for (document in documents) {
                    val mensagem = document.getString("message") ?: "Nova notificação"
                    notificationList.add(mensagem)
                }

                // TODO: Adicione o seu Adapter aqui quando for exibir no RecyclerView
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Erro ao carregar notificações: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}