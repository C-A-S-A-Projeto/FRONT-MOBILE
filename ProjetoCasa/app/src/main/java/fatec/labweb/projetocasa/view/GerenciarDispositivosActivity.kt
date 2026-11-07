package fatec.labweb.projetocasa.view

import android.os.Bundle
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import fatec.labweb.projetocasa.R

class GerenciarDispositivosActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int {
        return R.layout.activity_gerenciar_dispositivos
    }

    override fun getCurrentMenuItemId(): Int {
        return R.id.menuInicio
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<MaterialButton>(R.id.btnConectarDispositivo)?.setOnClickListener {
            Toast.makeText(this, "Conectado com sucesso!", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnEditarDispositivo)?.setOnClickListener {
            Toast.makeText(this, "Editar dispositivo", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnExcluirDispositivo)?.setOnClickListener {
            Toast.makeText(this, "Dispositivo excluído", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
