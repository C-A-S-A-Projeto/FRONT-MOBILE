package fatec.labweb.projetocasa.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class PerfilActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int = R.layout.activity_profile
    override fun getCurrentMenuItemId(): Int = -1

    private lateinit var tvTitleProfile: TextView
    private lateinit var tvEmailProfile: TextView
    private lateinit var imgProfile: ImageView
    private lateinit var fabChangePhoto: FloatingActionButton
    private lateinit var progressUploadAvatar: ProgressBar
    private lateinit var etName: TextInputEditText
    private lateinit var btnSaveName: MaterialButton
    private lateinit var btnResetPassword: MaterialButton
    private lateinit var btnDeleteAccount: MaterialButton

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    // 1. ActivityResultLauncher para seleção de mídia de galeria nativa com MIME type "image/*"
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadAvatarFirebaseStorage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initViews()
        setupListeners()
        carregarDadosUsuario()
    }

    private fun initViews() {
        tvTitleProfile = findViewById(R.id.tvTitleProfile)
        tvEmailProfile = findViewById(R.id.tvEmailProfile)
        imgProfile = findViewById(R.id.imgProfile)
        fabChangePhoto = findViewById(R.id.fabChangePhoto)
        progressUploadAvatar = findViewById(R.id.progressUploadAvatar)
        etName = findViewById(R.id.etName)
        btnSaveName = findViewById(R.id.btnSaveName)
        btnResetPassword = findViewById(R.id.btnResetPassword)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)
    }

    private fun setupListeners() {
        // Disparar seletor de mídia de galeria ao clicar no botão flutuante de câmera ou no card da foto
        fabChangePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        findViewById<View>(R.id.cardProfileImage)?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Salvar alterações de nome
        btnSaveName.setOnClickListener {
            salvarNomeUsuario()
        }

        // Redefinir senha
        btnResetPassword.setOnClickListener {
            val intent = Intent(this, RedefinirSenhaActivity::class.java)
            startActivity(intent)
        }

        // Excluir conta
        btnDeleteAccount.setOnClickListener {
            val intent = Intent(this, ExcluirContaActivity::class.java)
            startActivity(intent)
        }
    }

    private fun carregarDadosUsuario() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvEmailProfile.text = user.email ?: "Sem e-mail cadastrado"
        etName.setText(user.displayName ?: "")

        val avatarLocal = File(filesDir, "perfil/${user.uid}.jpg")

        firestore.collection("usuarios").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val nomeDoc = doc.getString("nome")
                if (!nomeDoc.isNullOrBlank()) {
                    etName.setText(nomeDoc)
                }

                val fotoUrl = doc.getString("fotoUrl")
                    ?: doc.getString("avatarUrl")
                    ?: user.photoUrl?.toString()

                if (!fotoUrl.isNullOrBlank()) {
                    Glide.with(this)
                        .load(fotoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.bg_circle_placeholder)
                        .error(R.drawable.bg_circle_placeholder)
                        .into(imgProfile)
                } else if (avatarLocal.exists()) {
                    Glide.with(this)
                        .load(avatarLocal)
                        .circleCrop()
                        .placeholder(R.drawable.bg_circle_placeholder)
                        .error(R.drawable.bg_circle_placeholder)
                        .into(imgProfile)
                }
            }
            .addOnFailureListener {
                if (avatarLocal.exists()) {
                    Glide.with(this)
                        .load(avatarLocal)
                        .circleCrop()
                        .placeholder(R.drawable.bg_circle_placeholder)
                        .error(R.drawable.bg_circle_placeholder)
                        .into(imgProfile)
                }
            }
    }

    /**
     * Upload do Avatar para o Firebase Storage em perfil/{userId}.jpg
     * Atualização do ImageView com Glide e persistência no Firestore.
     */
    private fun uploadAvatarFirebaseStorage(imageUri: Uri) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuário não autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        progressUploadAvatar.visibility = View.VISIBLE
        fabChangePhoto.isEnabled = false

        val storageRef = storage.reference.child("perfil/${user.uid}.jpg")

        val inputStream = contentResolver.openInputStream(imageUri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        if (bytes == null) {
            progressUploadAvatar.visibility = View.GONE
            fabChangePhoto.isEnabled = true
            Toast.makeText(this, "Não foi possível ler a imagem selecionada.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Upload direto do fluxo de bytes para o caminho perfil/{userId}.jpg no Firebase Storage
        storageRef.putBytes(bytes)
            .addOnSuccessListener {
                // 2. Obter downloadUrl após conclusão do upload
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val urlString = downloadUrl.toString()

                    // 3. Persistir URL no documento do usuário no Firestore
                    val dadosUsuario = mapOf(
                        "fotoUrl" to urlString,
                        "avatarUrl" to urlString
                    )

                    firestore.collection("usuarios").document(user.uid)
                        .update(dadosUsuario)
                        .addOnCompleteListener {
                            // Salva também cópia local para cache rápido e offline
                            salvarCopiaLocalAvatar(imageUri, user.uid)

                            // 4. Atualizar ImageView com Glide (circleCrop)
                            Glide.with(this@PerfilActivity)
                                .load(urlString)
                                .circleCrop()
                                .placeholder(R.drawable.bg_circle_placeholder)
                                .error(R.drawable.bg_circle_placeholder)
                                .into(imgProfile)

                            // Atualizar perfil do FirebaseAuth
                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setPhotoUri(downloadUrl)
                                .build()
                            user.updateProfile(profileUpdates)

                            progressUploadAvatar.visibility = View.GONE
                            fabChangePhoto.isEnabled = true
                            Toast.makeText(this@PerfilActivity, "Foto de perfil atualizada com sucesso!", Toast.LENGTH_SHORT).show()
                        }
                }.addOnFailureListener { e ->
                    progressUploadAvatar.visibility = View.GONE
                    fabChangePhoto.isEnabled = true
                    Toast.makeText(this@PerfilActivity, "Erro ao obter URL da imagem: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                progressUploadAvatar.visibility = View.GONE
                fabChangePhoto.isEnabled = true
                Toast.makeText(this@PerfilActivity, "Falha no upload para o Storage: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun salvarCopiaLocalAvatar(uri: Uri, userId: String) {
        try {
            val pastaPerfil = File(filesDir, "perfil")
            if (!pastaPerfil.exists()) pastaPerfil.mkdirs()

            val arquivoDestino = File(pastaPerfil, "$userId.jpg")
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                FileOutputStream(arquivoDestino).use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
            }
        } catch (_: Exception) {}
    }

    private fun salvarNomeUsuario() {
        val novoNome = etName.text?.toString()?.trim() ?: ""
        if (novoNome.isBlank()) {
            etName.error = "Por favor, informe seu nome"
            return
        }

        val user = auth.currentUser ?: return
        btnSaveName.isEnabled = false

        val updates = mapOf("nome" to novoNome)
        firestore.collection("usuarios").document(user.uid)
            .update(updates)
            .addOnSuccessListener {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(novoNome)
                    .build()

                user.updateProfile(profileUpdates).addOnCompleteListener {
                    btnSaveName.isEnabled = true
                    Toast.makeText(this, "Perfil atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                btnSaveName.isEnabled = true
                Toast.makeText(this, "Erro ao atualizar perfil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
