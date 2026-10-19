package fatec.labweb.projetocasa.dao

//import com.google.firebase.firestore.FirebaseFirestore
/*
class DispositivoDao {
   private val db = FirebaseFirestore.getInstance()

    fun buscarStatus(callback: (DispositivoModel?) -> Unit) {
        db.collection("status_casa").document("atual")
            .addSnapshotListener { snapshot, _ ->
                val dados = snapshot?.toObject(DispositivoModel::class.java)
                callback(dados)
            }

}} */