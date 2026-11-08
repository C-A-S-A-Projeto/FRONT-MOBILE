package fatec.labweb.projetocasa.view

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.R

class NotificacoesActivity : BaseDrawerActivity() {

    private lateinit var db: FirebaseFirestore

    override fun getContentLayoutId(): Int {
        return R.layout.activity_notifications
    }

    override fun getCurrentMenuItemId(): Int {
        return -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = FirebaseFirestore.getInstance()

        findViewById<Button>(R.id.btnFecharNotificacoes)?.setOnClickListener {
            finish()
        }

        loadNotifications()
    }

    private fun loadNotifications() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val col = if (uid != null) {
            db.collection("usuarios").document(uid).collection("notificacoes")
        } else {
            db.collection("notifications")
        }

        col.get()
            .addOnSuccessListener { documents ->
                val notificationList = mutableListOf<String>()

                for (document in documents) {
                    val mensagem = document.getString("mensagem")
                        ?: document.getString("message")
                        ?: document.getString("titulo")
                        ?: "Nova notificação"
                    notificationList.add(mensagem)
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Erro ao carregar notificações: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
