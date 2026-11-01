package fatec.labweb.projetocasa.service

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class PerfilService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    fun obterUsuarioAtual(): FirebaseUser? = auth.currentUser

    fun enviarEmailRedefinicaoSenha(
        email: String,
        onComplete: (sucesso: Boolean, erro: Exception?) -> Unit
    ) {
        auth.sendPasswordResetEmail(email.trim())
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful, task.exception)
            }
    }

    fun atualizarSenha(
        novaSenha: String,
        onComplete: (sucesso: Boolean, erro: Exception?) -> Unit
    ) {
        val usuario = auth.currentUser
        if (usuario == null) {
            onComplete(false, IllegalStateException("Usuário não autenticado."))
            return
        }

        usuario.updatePassword(novaSenha)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful, task.exception)
            }
    }

    fun reautenticarEExcluirConta(
        senha: String,
        onComplete: (sucesso: Boolean, erro: Exception?) -> Unit
    ) {
        val usuario = auth.currentUser
        val email = usuario?.email
        if (usuario == null || email.isNullOrBlank()) {
            onComplete(false, IllegalStateException("Usuário não autenticado."))
            return
        }

        val credencial = EmailAuthProvider.getCredential(email, senha)
        usuario.reauthenticate(credencial)
            .addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    usuario.delete()
                        .addOnCompleteListener { deleteTask ->
                            onComplete(deleteTask.isSuccessful, deleteTask.exception)
                        }
                } else {
                    onComplete(false, reauthTask.exception)
                }
            }
    }
}
