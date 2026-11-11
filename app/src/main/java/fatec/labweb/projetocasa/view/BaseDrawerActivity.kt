package fatec.labweb.projetocasa.view

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.CasaApplication
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.util.SoundManager
import java.io.File

abstract class BaseDrawerActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val fontScale = prefs.getFloat(CasaApplication.KEY_FONT_SCALE, 1.0f)
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = fontScale
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var tvInitial: TextView
    private lateinit var imgAvatar: ImageView
    private lateinit var tvUserName: TextView

    abstract fun getContentLayoutId(): Int
    abstract fun getCurrentMenuItemId(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicação forçada e persistência de Dark Mode antes da inflação do layout
        val prefs = getSharedPreferences("app_config", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("KEY_DARK_MODE", prefs.getBoolean("dark_mode", false))
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

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

    override fun onResume() {
        super.onResume()
        highlightCurrentMenuItem()
        loadUser()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_bg))
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val btnVoltar = findViewById<ImageButton>(R.id.btnVoltar)

        if (this !is HomeActivity) {
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
            }
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { view ->
                SoundManager.dispararAudioCue(view)
                onBackPressedDispatcher.onBackPressed()
            }
            btnVoltar?.visibility = View.VISIBLE
            btnVoltar?.setOnClickListener { view ->
                SoundManager.dispararAudioCue(view)
                onBackPressedDispatcher.onBackPressed()
            }
        } else {
            val navIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_sort_by_size)
            navIcon?.setTint(ContextCompat.getColor(this, R.color.text_primary))
            toolbar.navigationIcon = navIcon
            toolbar.setNavigationOnClickListener {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
            }
            btnVoltar?.visibility = View.GONE
        }

        findViewById<ImageView>(R.id.imgToolbarLogo)?.setOnClickListener {
            navigateTo(HomeActivity::class.java)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                SoundManager.dispararAudioCue(findViewById(android.R.id.home) ?: findViewById(R.id.toolbar) ?: window.decorView)
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupNotificationButton() {
        val notificationButton = findViewById<View>(R.id.notificationBadgeDot)?.parent as? View
            ?: findViewById<View>(R.id.toolbar)?.findViewById<View>(R.id.notificationBadgeDot)?.parent as? View

        notificationButton?.setOnClickListener {
            navigateTo(NotificacoesActivity::class.java)
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
                imageView?.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                textView?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun setupCustomMenuClicks() {
        val headerView = navView.getHeaderView(0) ?: return

        headerView.findViewById<LinearLayout>(R.id.menuInicio)?.setOnClickListener {
            navigateTo(HomeActivity::class.java)
        }
        headerView.findViewById<LinearLayout>(R.id.menuCategorias)?.setOnClickListener {
            navigateTo(CategoriaGeralActivity::class.java)
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

    fun dispararAudioCue(view: View) {
        SoundManager.dispararAudioCue(view)
    }

    fun tocarSomClique(view: View) {
        SoundManager.dispararAudioCue(view)
    }

    fun navigateTo(destination: Class<*>) {
        SoundManager.playClickSound(context = this)
        drawerLayout.closeDrawer(GravityCompat.START)

        // Se já está na tela atual, apenas fecha o drawer e não recria a tela
        if (destination == this::class.java) {
            return
        }

        val intent = Intent(this, destination).apply {
            if (destination == HomeActivity::class.java) {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            } else {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        startActivity(intent)
    }

    private fun setupProfile() {
        val headerView = navView.getHeaderView(0) ?: return
        tvInitial = headerView.findViewById(R.id.tvInitial)
        imgAvatar = headerView.findViewById(R.id.imgAvatar)
        tvUserName = headerView.findViewById(R.id.tvUserName)
        tvUserName.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

        headerView.findViewById<View>(R.id.profileContainer)?.setOnClickListener {
            navigateTo(PerfilActivity::class.java)
        }

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

        val avatarLocal = File(filesDir, "perfil/${user.uid}.jpg")

        FirebaseFirestore.getInstance().collection("usuarios").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val nomeFirestore = doc.getString("nome")
                mostrarNome(nomeFirestore ?: user.displayName ?: "Usuário")

                val fotoUrlFirestore = doc.getString("fotoUrl")
                    ?: doc.getString("avatarUrl")
                    ?: user.photoUrl?.toString()

                if (!fotoUrlFirestore.isNullOrBlank()) {
                    tvInitial.visibility = View.GONE
                    imgAvatar.visibility = View.VISIBLE
                    Glide.with(this)
                        .load(fotoUrlFirestore)
                        .circleCrop()
                        .placeholder(R.drawable.bg_circle_placeholder)
                        .error(R.drawable.bg_circle_placeholder)
                        .into(imgAvatar)
                } else if (avatarLocal.exists()) {
                    tvInitial.visibility = View.GONE
                    imgAvatar.visibility = View.VISIBLE
                    Glide.with(this)
                        .load(avatarLocal)
                        .circleCrop()
                        .placeholder(R.drawable.bg_circle_placeholder)
                        .error(R.drawable.bg_circle_placeholder)
                        .into(imgAvatar)
                } else {
                    tvInitial.visibility = View.VISIBLE
                    imgAvatar.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                mostrarNome(user.displayName ?: "Usuário")
                if (avatarLocal.exists()) {
                    tvInitial.visibility = View.GONE
                    imgAvatar.visibility = View.VISIBLE
                    Glide.with(this)
                        .load(avatarLocal)
                        .circleCrop()
                        .into(imgAvatar)
                } else {
                    tvInitial.visibility = View.VISIBLE
                    imgAvatar.visibility = View.GONE
                }
            }
    }

    private fun mostrarNome(nome: String) {
        tvUserName.text = nome
        tvInitial.text = nome.trim().firstOrNull()?.uppercase() ?: "U"
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
        finish()
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
