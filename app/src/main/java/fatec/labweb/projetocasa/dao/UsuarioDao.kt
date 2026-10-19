package fatec.labweb.projetocasa.dao

import fatec.labweb.projetocasa.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class UsuarioDao {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Função para realizar o login com E-mail e Senha
    fun logar(email: String, senha: String, onComplete: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, senha)
            .addOnSuccessListener {
                onComplete(true, null)
            }
            .addOnFailureListener { exception ->
                onComplete(false, exception.message)
            }
    }

    // Função para cadastrar o utilizador no Authentication e salvar os dados no Firestore
    fun cadastrar(usuario: Usuario, senha: String, onComplete: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(usuario.email, senha)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: ""
                usuario.id = userId

                // Salva os dados adicionais na coleção "usuarios" do Firestore
                db.collection("usuarios").document(userId)
                    .set(usuario)
                    .addOnSuccessListener {
                        onComplete(true, null)
                    }
                    .addOnFailureListener { exception ->
                        onComplete(false, exception.message)
                    }
            }
            .addOnFailureListener { exception ->
                onComplete(false, exception.message)
            }
    }

    // Função para obter o ID do utilizador atualmente logado
    fun obterUsuarioAtualId(): String? {
        return auth.currentUser?.uid
    }

    // Função para terminar sessão
    fun deslogar() {
        auth.signOut()
    }
}