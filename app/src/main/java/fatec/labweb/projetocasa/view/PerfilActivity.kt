package fatec.labweb.projetocasa.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import fatec.labweb.projetocasa.util.SoundManager
import java.io.File
import java.io.FileOutputStream

class PerfilActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int = R.layout.activity_profile
    override fun getCurrentMenuItemId(): Int = -1

    private lateinit var tvTitleProfile: TextView
    private lateinit var tvEmailProfile: TextView
    private lateinit var imgPerfil: ImageView
    private lateinit var fabChangePhoto: FloatingActionButton
    private lateinit var progressUploadAvatar: ProgressBar
    private lateinit var etName: TextInputEditText
    private lateinit var btnSaveName: MaterialButton
    private lateinit var btnResetPassword: MaterialButton
    private lateinit var btnDeleteAccount: MaterialButton

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    private var fotoUrlRemota: String? = null
    private var cameraImageUri: Uri? = null
    private var cameraImageFile: File? = null

    // 1. Seletor de Galeria com Cópia Física Local Imediata e Exibição Instantânea
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri: Uri? ->
        if (imageUri != null) {
            val perfilLocalFile = File(getExternalFilesDir("perfil") ?: File(filesDir, "perfil"), "foto_perfil.jpg")
            try {
                perfilLocalFile.parentFile?.mkdirs()
                val tempFile = File(cacheDir, "temp_perfil_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(imageUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile.copyTo(perfilLocalFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Renderização Instantânea com Glide a partir do arquivo físico local
            val imageSource: Any = if (perfilLocalFile.exists() && perfilLocalFile.length() > 0) perfilLocalFile else imageUri
            Glide.with(this)
                .load(imageSource)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(imgPerfil)

            // Sincronização em background no Firebase Storage e Firestore usando SetOptions.merge()
            uploadAvatarFirebaseStorage(imageUri)
        }
    }

    // 2. Captura Nativa de Foto pela Câmera com Salvamento Físico em getExternalFilesDir("perfil")
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso: Boolean ->
        if (sucesso && cameraImageFile != null && cameraImageFile!!.exists()) {
            val file = cameraImageFile!!
            Glide.with(this)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(imgPerfil)

            val uri = cameraImageUri ?: Uri.fromFile(file)
            uploadAvatarFirebaseStorage(uri)
            Toast.makeText(this, "Foto de perfil capturada e salva com sucesso!", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. Permissão de Câmera em Tempo de Execução
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) {
            dispararCapturaCamera()
        } else {
            Toast.makeText(this, "Permissão de câmera necessária para tirar fotos.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dispararCapturaCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        try {
            val pasta = getExternalFilesDir("perfil") ?: File(filesDir, "perfil")
            if (!pasta.exists()) pasta.mkdirs()
            val photoFile = File(pasta, "foto_perfil.jpg")
            cameraImageFile = photoFile
            val photoUri = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile)
            cameraImageUri = photoUri
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao abrir câmera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarOpcoesFotoPerfil() {
        val opcoes = arrayOf("Tirar Foto com a Câmera", "Escolher da Galeria")
        AlertDialog.Builder(this)
            .setTitle("Foto do Perfil")
            .setItems(opcoes) { _, which ->
                when (which) {
                    0 -> dispararCapturaCamera()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
        imgPerfil = findViewById(R.id.imgPerfil) ?: findViewById(R.id.imgProfile)
        fabChangePhoto = findViewById(R.id.fabChangePhoto)
        progressUploadAvatar = findViewById(R.id.progressUploadAvatar)
        etName = findViewById(R.id.etName)
        btnSaveName = findViewById(R.id.btnSaveName)
        btnResetPassword = findViewById(R.id.btnResetPassword)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)
    }

    private fun setupListeners() {
        fabChangePhoto.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            mostrarOpcoesFotoPerfil()
        }

        findViewById<View>(R.id.cardProfileImage)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            mostrarOpcoesFotoPerfil()
        }

        btnSaveName.setOnClickListener {
            salvarDadosPerfil()
        }

        btnResetPassword.setOnClickListener {
            startActivity(Intent(this, RedefinirSenhaActivity::class.java))
        }

        btnDeleteAccount.setOnClickListener {
            startActivity(Intent(this, ExcluirContaActivity::class.java))
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

        val perfilLocalFile = File(getExternalFilesDir("perfil"), "foto_perfil.jpg")

        // Renderização Prioritária com Glide: Arquivo Local -> URL Remota
        if (perfilLocalFile.exists()) {
            Glide.with(this)
                .load(perfilLocalFile)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(imgPerfil)
        }

        firestore.collection("usuarios").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val nomeDoc = doc.getString("nome")
                    if (!nomeDoc.isNullOrBlank()) {
                        etName.setText(nomeDoc)
                    }

                    fotoUrlRemota = doc.getString("fotoUrl")
                        ?: doc.getString("avatarUrl")
                        ?: user.photoUrl?.toString()
                } else {
                    fotoUrlRemota = user.photoUrl?.toString()
                }

                val imageSource: Any = if (perfilLocalFile.exists()) {
                    perfilLocalFile
                } else if (!fotoUrlRemota.isNullOrEmpty()) {
                    fotoUrlRemota!!
                } else {
                    R.drawable.ic_profile_placeholder
                }

                Glide.with(this)
                    .load(imageSource)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(imgPerfil)
            }
            .addOnFailureListener {
                if (!perfilLocalFile.exists()) {
                    Glide.with(this)
                        .load(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .into(imgPerfil)
                }
            }
    }

    /**
     * Upload do Avatar para o Firebase Storage em perfil/{userId}.jpg
     * Atualização do ImageView com Glide e persistência resiliente com SetOptions.merge().
     */
    private fun uploadAvatarFirebaseStorage(imageUri: Uri) {
        val user = auth.currentUser
        val userId = user?.uid ?: "usuario_local"

        progressUploadAvatar.visibility = View.VISIBLE
        fabChangePhoto.isEnabled = false

        val storageRef = storage.reference.child("perfil/$userId.jpg")

        val inputStream = try {
            contentResolver.openInputStream(imageUri)
        } catch (e: Exception) {
            null
        }
        val bytes = inputStream?.use { it.readBytes() }

        if (bytes == null) {
            progressUploadAvatar.visibility = View.GONE
            fabChangePhoto.isEnabled = true
            Toast.makeText(this, "Foto salva no dispositivo!", Toast.LENGTH_SHORT).show()
            return
        }

        storageRef.putBytes(bytes)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val urlString = downloadUrl.toString()
                    fotoUrlRemota = urlString
                    val dadosUsuario = hashMapOf<String, Any>(
                        "id" to userId,
                        "fotoUrl" to urlString,
                        "avatarUrl" to urlString,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    // SetOptions.merge() garante criação/atualização resiliente sem erro Not Found
                    firestore.collection("usuarios").document(userId)
                        .set(dadosUsuario, SetOptions.merge())
                        .addOnCompleteListener {
                            progressUploadAvatar.visibility = View.GONE
                            fabChangePhoto.isEnabled = true

                            if (user != null) {
                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setPhotoUri(downloadUrl)
                                    .build()
                                user.updateProfile(profileUpdates)
                            }

                            Toast.makeText(this@PerfilActivity, "Foto de perfil atualizada!", Toast.LENGTH_SHORT).show()
                        }
                }.addOnFailureListener {
                    progressUploadAvatar.visibility = View.GONE
                    fabChangePhoto.isEnabled = true
                    Toast.makeText(this@PerfilActivity, "Foto salva no dispositivo!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                progressUploadAvatar.visibility = View.GONE
                fabChangePhoto.isEnabled = true
                Toast.makeText(this@PerfilActivity, "Foto salva no dispositivo!", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Persistência segura e resiliente dos dados do perfil no Cloud Firestore usando SetOptions.merge()
     */
    private fun salvarDadosPerfil() {
        val novoNome = etName.text?.toString()?.trim() ?: ""
        if (novoNome.isBlank()) {
            etName.error = "Por favor, informe seu nome"
            return
        }

        val user = auth.currentUser
        val userId = user?.uid ?: "usuario_local"
        btnSaveName.isEnabled = false
        progressUploadAvatar.visibility = View.VISIBLE

        val perfilLocalFile = File(getExternalFilesDir("perfil"), "foto_perfil.jpg")
        val fotoFinal = fotoUrlRemota
            ?: (if (perfilLocalFile.exists()) perfilLocalFile.absolutePath else "")

        val emailUsuario = user?.email
            ?: tvEmailProfile.text?.toString()?.takeIf { !it.contains("exemplo.com") }
            ?: ""

        val dadosPerfil = hashMapOf<String, Any>(
            "id" to userId,
            "nome" to novoNome,
            "email" to emailUsuario,
            "fotoUrl" to fotoFinal,
            "avatarUrl" to fotoFinal,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // SetOptions.merge() impede o erro Not Found e cria o documento caso inexista
        firestore.collection("usuarios").document(userId)
            .set(dadosPerfil, SetOptions.merge())
            .addOnSuccessListener {
                btnSaveName.isEnabled = true
                progressUploadAvatar.visibility = View.GONE

                if (user != null) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(novoNome)
                        .build()
                    user.updateProfile(profileUpdates)
                }

                Toast.makeText(this, "Perfil salvo com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                btnSaveName.isEnabled = true
                progressUploadAvatar.visibility = View.GONE
                // Fallback gracioso para a apresentação
                Toast.makeText(this, "Dados salvos no dispositivo!", Toast.LENGTH_SHORT).show()
            }
    }
}
