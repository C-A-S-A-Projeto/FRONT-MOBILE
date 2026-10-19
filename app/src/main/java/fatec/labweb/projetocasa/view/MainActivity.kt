package fatec.labweb.projetocasa.view

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Teste de conexão com o Firestore
        val db = FirebaseFirestore.getInstance()

        val dadosTeste = hashMapOf(
            "mensagem" to "Conexão com o Firebase estabelecida com sucesso!",
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("teste_conexao")
            .add(dadosTeste)
            .addOnSuccessListener { documentReference ->
                // 1. Mostra o balãozinho no ecrã do telemóvel
                Toast.makeText(this, "Conectado com sucesso!", Toast.LENGTH_SHORT).show()

                // 2. Imprime a mensagem detalhada no terminal (Logcat)
                Log.d("FIREBASE_TEST", "Sucesso! ID do documento: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao conectar!", Toast.LENGTH_SHORT).show()
                Log.e("FIREBASE_TEST", "Erro ao conectar com o Firestore", e)
            }
    }
}