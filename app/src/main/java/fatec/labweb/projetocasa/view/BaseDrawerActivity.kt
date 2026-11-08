package fatec.labweb.projetocasa.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.R
import java.io.File

abstract class BaseDrawerActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var tvInitial: TextView
    private lateinit var imgAvatar: ImageView
    private lateinit var tvUserName: TextView

    abstract fun getContentLayoutId(): Int
    abstract fun getCurrentMenuItemId(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base_drawer)

        layoutInflater.inflate(getContentLayoutId(), findViewById(R.id.contentFrame), true)

        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)

        setupToolbar()
        setupCustomMenuClicks()
        highlightCurrentMenuItem()
        setupProfile()
        loadUser()
        setupBackPressHandler()
        setupNotificationButton()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
        toolbar.navigationIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_sort_by_size)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupNotificationButton() {
        val notificationButton = findViewById<View>(R.id.notificationBadgeDot)?.parent as? View
            ?: findViewById<View>(R.id.toolbar)?.findViewById<View>(R.id.notificationBadgeDot)?.parent as? View

        notificationButton?.setOnClickListener {
            if (this !is NotificacoesActivity) {
                val intent = Intent(this, NotificacoesActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun highlightCurrentMenuItem() {
        val headerView = navView.getHeaderView(0) ?: return
        val currentId = getCurrentMenuItemId()

        val menuItems = listOf(
            R.id.menuInicio,
            R.id.menuCategorias,
            R.id.menuDesempenho,
            R.id.menuHistorico,
            R.id.menuConfiguracoes
        )

        for (itemId in menuItems) {
            val layoutItem = headerView.findViewById<LinearLayout>(itemId) ?: continue
            val imageView = layoutItem.getChildAt(0) as? ImageView
            val textView = layoutItem.getChildAt(1) as? TextView

            if (itemId == currentId) {
                layoutItem.setBackgroundResource(R.drawable.menu_item_selected_bg)
                imageView?.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
                textView?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                layoutItem.background = null
                imageView?.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray))
                textView?.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }

    private fun setupCustomMenuClicks() {
        val headerView = navView.getHeaderView(0) ?: return

        headerView.findViewById<LinearLayout>(R.id.menuInicio)?.setOnClickListener {
            navigateTo(HomeActivity::class.java)
        }
        headerView.findViewById<LinearLayout>(R.id.menuCategorias)?.setOnClickListener {
            navigateTo(CategoriaItemActivity::class.java)
        }
        headerView.findViewById<LinearLayout>(R.id.menuDesempenho)?.setOnClickListener {
            navigateTo(DesempenhoActivity::class.java)
        }
        headerView.findViewById<LinearLayout>(R.id.menuHistorico)?.setOnClickListener {
            navigateTo(HistoricoActivity::class.java)
        }
        headerView.findViewById<LinearLayout>(R.id.menuConfiguracoes)?.setOnClickListener {
            navigateTo(ConfiguracoesActivity::class.java)
        }
    }

    private fun navigateTo(destination: Class<*>) {
        drawerLayout.closeDrawer(GravityCompat.START)
        if (destination.isAssignableFrom(this::class.java).not() && destination != this::class.java) {
            val intent = Intent(this, destination)
            startActivity(intent)
            if (destination != HomeActivity::class.java) {
                finish()
            }
        }
    }

    private fun setupProfile() {
        val headerView = navView.getHeaderView(0) ?: return
        tvInitial = headerView.findViewById(R.id.tvInitial)
        imgAvatar = headerView.findViewById(R.id.imgAvatar)
        tvUserName = headerView.findViewById(R.id.tvUserName)

        // Ao clicar no card do usuário, abre a tela de perfil diretamente
        headerView.findViewById<View>(R.id.profileContainer)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            if (this !is PerfilActivity) {
                val intent = Intent(this, PerfilActivity::class.java)
                startActivity(intent)
            }
        }

        // Ao clicar no botão vermelho "Desconectar", faz o logout
        headerView.findViewById<View>(R.id.btnDesconectar)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            logout()
        }
    }

    private fun loadUser() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            irParaDesconectar()
            return
        }

        FirebaseFirestore.getInstance().collection("usuarios").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val nomeFirestore = doc.getString("nome")
                mostrarNome(nomeFirestore ?: user.displayName ?: "Usuário")
            }
            .addOnFailureListener {
                mostrarNome(user.displayName ?: "Usuário")
            }

        // Avatar salvo localmente em disco
        val avatarLocal = File(filesDir, "perfil/${user.uid}.jpg")
        if (avatarLocal.exists()) {
            tvInitial.visibility = View.GONE
            imgAvatar.visibility = View.VISIBLE
            Glide.with(this).load(avatarLocal).circleCrop().into(imgAvatar)
        } else {
            tvInitial.visibility = View.VISIBLE
            imgAvatar.visibility = View.GONE
        }
    }

    private fun mostrarNome(nome: String) {
        tvUserName.text = nome
        tvInitial.text = nome.trim().firstOrNull()?.uppercase() ?: "?"
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        irParaDesconectar()
    }

    private fun irParaDesconectar() {
        val intent = Intent(this, DesconectarContaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
