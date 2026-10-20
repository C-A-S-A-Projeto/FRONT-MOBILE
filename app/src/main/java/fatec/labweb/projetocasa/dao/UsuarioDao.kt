package fatec.labweb.projetocasa.dao

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.api.CasaApiClient
import fatec.labweb.projetocasa.model.Usuario

class UsuarioDao {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Login no Firebase Auth + sincronização imediata do perfil na CASA-API (GET /me)
    fun logar(email: String, senha: String, onComplete: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, senha)
            .addOnSuccessListener {
                CasaApiClient.sincronizarPerfil { _, _ ->
                    onComplete(true, null)
                }
            }
            .addOnFailureListener { exception ->
                onComplete(false, exception.message)
            }
    }

    // Etapa 1 do cadastro pela CASA-API (envia código de 6 dígitos para o e-mail/terminal)
    fun iniciarCadastroApi(nome: String, email: String, senha: String, onComplete: (Boolean, String?) -> Unit) {
        CasaApiClient.iniciarCadastro(nome, email, senha, onComplete)
    }

    // Etapa 2 do cadastro pela CASA-API (valida o código de 6 dígitos, loga no Firebase e inicializa /me)
    fun confirmarCadastroApi(email: String, codigo: String, senha: String, onComplete: (Boolean, String?) -> Unit) {
        CasaApiClient.confirmarCadastro(email, codigo, senha) { ok, erroConfirmacao ->
            if (!ok) {
                onComplete(false, erroConfirmacao)
                return@confirmarCadastro
            }
            auth.signInWithEmailAndPassword(email, senha)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: ""
                    CasaApiClient.sincronizarPerfil { perfil, _ ->
                        if (uid.isNotEmpty() && perfil != null) {
                            db.collection("usuarios").document(uid).set(perfil)
                        }
                        onComplete(true, null)
                    }
                }
                .addOnFailureListener { e ->
                    onComplete(false, e.message)
                }
        }
    }

    fun reenviarCodigoApi(email: String, onComplete: (Boolean, String?) -> Unit) {
        CasaApiClient.reenviarCodigo(email, onComplete)
    }

    // Cadastro direto no Firebase Authentication + Firestore + sincronização com a CASA-API
    fun cadastrar(usuario: Usuario, senha: String, onComplete: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(usuario.email, senha)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: ""
                usuario.id = userId

                db.collection("usuarios").document(userId)
                    .set(usuario)
                    .addOnSuccessListener {
                        CasaApiClient.sincronizarPerfil { _, _ ->
                            onComplete(true, null)
                        }
                    }
                    .addOnFailureListener { exception ->
                        onComplete(false, exception.message)
                    }
            }
            .addOnFailureListener { exception ->
                onComplete(false, exception.message)
            }
    }

    fun enviarRecuperacaoSenha(email: String, onComplete: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    fun atualizarPerfil(
        nome: String? = null,
        notificacoesAtivas: Boolean? = null,
        modoEscuro: Boolean? = null,
        tamanhoFonte: Int? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        CasaApiClient.atualizarPerfil(nome, notificacoesAtivas, modoEscuro, tamanhoFonte, onComplete)
    }

    fun excluirConta(onComplete: (Boolean, String?) -> Unit) {
        CasaApiClient.excluirConta { ok, err ->
            if (ok) auth.signOut()
            onComplete(ok, err)
        }
    }

    fun obterUsuarioAtualId(): String? {
        return auth.currentUser?.uid
    }

    fun deslogar() {
        auth.signOut()
    }
}
