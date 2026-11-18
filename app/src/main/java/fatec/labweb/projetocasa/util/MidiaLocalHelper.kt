package fatec.labweb.projetocasa.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import fatec.labweb.projetocasa.CasaApplication
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.repository.CategoriaRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object MidiaLocalHelper {

    fun extrairCaminhoLimpo(caminhoOuUri: String?): String? {
        if (caminhoOuUri.isNullOrBlank()) return null
        return caminhoOuUri.substringBefore("?t=").removePrefix("file://").trim().takeIf { it.isNotBlank() }
    }

    fun anexarTimestampCacheBusting(caminhoOuUri: String, timestamp: Long = System.currentTimeMillis()): String {
        val base = caminhoOuUri.substringBefore("?t=").trim()
        return if (base.isBlank()) "" else "$base?t=$timestamp"
    }

    /**
     * Copia imediatamente uma URI temporária (content:// ou file://) retornada pela Galeria
     * para o diretório permanente de documentos da aplicação (botoes_imagens).
     */
    fun copiarUriParaArmazenamentoPermanente(
        context: Context,
        uri: Uri,
        prefixo: String = "galeria"
    ): File? {
        return try {
            val pastaDestino = context.getExternalFilesDir("botoes_imagens")
                ?: File(context.filesDir, "botoes_imagens")
            if (!pastaDestino.exists()) {
                pastaDestino.mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val arquivoPermanente = File(pastaDestino, "${prefixo}_${timestamp}.jpg")

            val copiado = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(arquivoPermanente).use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false

            if (!copiado && uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                val orig = File(uri.path!!)
                if (orig.exists() && orig.length() > 0) {
                    orig.copyTo(arquivoPermanente, overwrite = true)
                }
            }

            if (arquivoPermanente.exists() && arquivoPermanente.length() > 0) {
                arquivoPermanente.setLastModified(timestamp)
                arquivoPermanente
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun salvarImagemLocalmente(context: Context, botaoId: String, fonte: Any): File? {
        val pastaDestino = context.getExternalFilesDir("botoes_imagens")
            ?: File(context.filesDir, "botoes_imagens")
        if (!pastaDestino.exists()) {
            pastaDestino.mkdirs()
        }

        val idLimpo = botaoId.substringBefore("?t=").trim()
        val arquivoDestino = File(pastaDestino, "$idLimpo.jpg")

        return try {
            when (fonte) {
                is Uri -> {
                    // Se o arquivo destino já existe com conteúdo e a URI aponta para ele (ex: foto direta da câmera)
                    if (arquivoDestino.exists() && arquivoDestino.length() > 0) {
                        val fileUri = try {
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", arquivoDestino)
                        } catch (_: Exception) { null }
                        if ((fileUri != null && fileUri == fonte) || (fonte.scheme == "file" && fonte.path == arquivoDestino.absolutePath)) {
                            arquivoDestino.setLastModified(System.currentTimeMillis())
                            return arquivoDestino
                        }
                    }

                    // Se for file:// URI apontando para arquivo permanente já copiado
                    if (fonte.scheme == "file" && !fonte.path.isNullOrBlank()) {
                        val f = File(extrairCaminhoLimpo(fonte.path) ?: fonte.path!!)
                        if (f.exists() && f.length() > 0) {
                            if (f.absolutePath != arquivoDestino.absolutePath) {
                                f.copyTo(arquivoDestino, overwrite = true)
                            }
                            arquivoDestino.setLastModified(System.currentTimeMillis())
                            return arquivoDestino
                        }
                    }

                    // Copia segura usando arquivo temporário no cache para evitar truncamento acidental
                    val tempFile = File(context.cacheDir, "temp_img_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(fonte)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.copyTo(arquivoDestino, overwrite = true)
                        tempFile.delete()
                    }
                }
                is File -> {
                    if (fonte.exists() && fonte.length() > 0) {
                        if (fonte.absolutePath != arquivoDestino.absolutePath) {
                            fonte.copyTo(arquivoDestino, overwrite = true)
                        }
                    }
                }
                is Int -> {
                    val drawable = ContextCompat.getDrawable(context, fonte) ?: return null
                    salvarDrawableComoJpg(drawable, arquivoDestino)
                }
                is Bitmap -> {
                    FileOutputStream(arquivoDestino).use { out ->
                        fonte.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                }
                else -> return null
            }
            if (arquivoDestino.exists() && arquivoDestino.length() > 0) {
                arquivoDestino.setLastModified(System.currentTimeMillis())
                arquivoDestino
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun salvarDrawableComoJpg(drawable: Drawable, arquivoDestino: File) {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 300
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 300
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        FileOutputStream(arquivoDestino).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    }

    fun obterArquivoLocal(context: Context, botaoId: String): File? {
        val pasta = context.getExternalFilesDir("botoes_imagens")
            ?: File(context.filesDir, "botoes_imagens")
        val arquivo = File(pasta, "$botaoId.jpg")
        return if (arquivo.exists()) arquivo else null
    }

    fun salvarAudioLocalmente(context: Context, id: String, fonte: Any): File? {
        val pastaDestino = context.getExternalFilesDir("botoes_audios")
            ?: File(context.filesDir, "botoes_audios")
        if (!pastaDestino.exists()) {
            pastaDestino.mkdirs()
        }

        val ext = when (fonte) {
            is File -> fonte.extension.ifBlank { "mp3" }
            is Uri -> if (fonte.path?.endsWith(".wav", true) == true) "wav" else if (fonte.path?.endsWith(".mp3", true) == true) "mp3" else "m4a"
            else -> "mp3"
        }
        val arquivoDestino = File(pastaDestino, "$id.$ext")

        return try {
            when (fonte) {
                is Uri -> {
                    if (arquivoDestino.exists() && arquivoDestino.length() > 0) {
                        val fileUri = try {
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", arquivoDestino)
                        } catch (_: Exception) { null }
                        if (fileUri != null && fileUri == fonte) {
                            return arquivoDestino
                        }
                    }

                    val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.$ext")
                    context.contentResolver.openInputStream(fonte)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.copyTo(arquivoDestino, overwrite = true)
                        tempFile.delete()
                    }
                }
                is File -> {
                    if (fonte.exists() && fonte.absolutePath != arquivoDestino.absolutePath) {
                        fonte.copyTo(arquivoDestino, overwrite = true)
                    }
                }
                else -> return null
            }
            if (arquivoDestino.exists() && arquivoDestino.length() > 0) arquivoDestino else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun obterAudioLocal(context: Context, id: String): File? {
        val pasta = context.getExternalFilesDir("botoes_audios")
            ?: File(context.filesDir, "botoes_audios")
        val extensoes = listOf("mp3", "wav", "m4a")
        for (ext in extensoes) {
            val f = File(pasta, "$id.$ext")
            if (f.exists() && f.length() > 0) return f
        }
        val arquivos = pasta.listFiles { _, name -> name.startsWith(id) }
        return arquivos?.firstOrNull { it.exists() && it.length() > 0 }
    }

    fun salvarPerfilLocalmente(context: Context, imageUri: Uri): File? {
        val pastaPerfil = context.getExternalFilesDir("perfil")
            ?: File(context.filesDir, "perfil")
        if (!pastaPerfil.exists()) {
            pastaPerfil.mkdirs()
        }
        val perfilLocalFile = File(pastaPerfil, "foto_perfil.jpg")
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(perfilLocalFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (perfilLocalFile.exists()) perfilLocalFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun obterPerfilLocal(context: Context): File? {
        val pastaPerfil = context.getExternalFilesDir("perfil")
            ?: File(context.filesDir, "perfil")
        val arquivo = File(pastaPerfil, "foto_perfil.jpg")
        return if (arquivo.exists() && arquivo.length() > 0) arquivo else null
    }

    fun salvarLogoLocalmenteSeguranca(context: Context): File? {
        val pastaDestino = context.filesDir
        val arquivoDestino = File(pastaDestino, "ic_logo_casa.png")
        val arquivoJpg = File(pastaDestino, "logo.jpg")

        return try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_logo_casa) ?: return null
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }

            FileOutputStream(arquivoDestino).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileOutputStream(arquivoJpg).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            if (arquivoDestino.exists() && arquivoDestino.length() > 0) arquivoDestino else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun obterLogoLocal(context: Context): File? {
        val arquivo = File(context.filesDir, "ic_logo_casa.png")
        if (arquivo.exists() && arquivo.length() > 0) return arquivo
        return salvarLogoLocalmenteSeguranca(context)
    }

    /**
     * Persiste a lista completa de botões de uma categoria em formato JSON no filesDir
     * e no SharedPreferences para redundância total e resiliência a restrições do Firestore v2.
     */
    fun salvarBotoesCategoriaLocalmente(context: Context, categoriaId: String, lista: List<CategoriaItem>) {
        if (categoriaId.isBlank()) return
        try {
            val jsonArray = JSONArray()
            for (item in lista) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("rotulo", item.rotulo)
                    put("nome", item.rotulo)
                    put("significado", item.significado)
                    put("categoriaId", categoriaId)
                    put("categoria_id", categoriaId)
                    put("localImagePath", item.localPath ?: "")
                    put("localPath", item.localPath ?: "")
                    put("localAudioPath", item.audioPath ?: "")
                    put("audioPath", item.audioPath ?: "")
                    put("imageUrl", item.imageUrl ?: "")
                    put("audioUrl", item.audioUrl ?: "")
                    put("imagem", item.imagem ?: "")
                    put("imagem_id", item.imagem_id ?: "")
                    put("audio", item.audio ?: "")
                    put("audio_id", item.audio_id ?: "")
                    put("ordem", item.ordem)
                    put("defaultGender", item.defaultGender.name)
                }
                jsonArray.put(obj)
            }
            val jsonStr = jsonArray.toString()

            // 1. Persistência em SharedPreferences
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("botoes_categoria_$categoriaId", jsonStr).apply()

            // 2. Persistência física em arquivo JSON no filesDir
            val arquivoJson = File(context.filesDir, "botoes_${categoriaId}.json")
            arquivoJson.writeText(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun temBotoesSalvosLocalmente(context: Context, categoriaId: String): Boolean {
        if (categoriaId.isBlank()) return false
        val arquivoJson = File(context.filesDir, "botoes_${categoriaId}.json")
        if (arquivoJson.exists() && arquivoJson.length() > 0) return true
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains("botoes_categoria_$categoriaId")
    }

    /**
     * Carrega a lista de botões salvos localmente para uma categoria (filesDir ou SharedPreferences).
     */
    fun carregarBotoesCategoriaLocalmente(context: Context, categoriaId: String): MutableList<CategoriaItem> {
        if (categoriaId.isBlank()) return mutableListOf()
        try {
            var jsonStr: String? = null
            val arquivoJson = File(context.filesDir, "botoes_${categoriaId}.json")
            if (arquivoJson.exists() && arquivoJson.length() > 0) {
                jsonStr = arquivoJson.readText()
            }
            if (jsonStr.isNullOrBlank()) {
                val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
                jsonStr = prefs.getString("botoes_categoria_$categoriaId", null)
            }
            if (!jsonStr.isNullOrBlank()) {
                val jsonArray = JSONArray(jsonStr)
                val lista = mutableListOf<CategoriaItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id")
                    val rotulo = obj.optString("rotulo", obj.optString("nome", "Botão"))
                    val significado = obj.optString("significado", rotulo)
                    val localPath = obj.optString("localPath", obj.optString("localImagePath", "")).takeIf { it.isNotBlank() }
                    val audioPath = obj.optString("audioPath", obj.optString("localAudioPath", "")).takeIf { it.isNotBlank() }
                    val imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() } ?: localPath
                    val audioUrl = obj.optString("audioUrl").takeIf { it.isNotBlank() } ?: audioPath
                    val imagem = obj.optString("imagem").takeIf { it.isNotBlank() } ?: imageUrl
                    val imagemId = obj.optString("imagem_id").takeIf { it.isNotBlank() } ?: imagem
                    val audio = obj.optString("audio").takeIf { it.isNotBlank() } ?: audioUrl
                    val audioId = obj.optString("audio_id").takeIf { it.isNotBlank() } ?: audio
                    val ordem = obj.optInt("ordem", i)
                    val genStr = obj.optString("defaultGender", "FEMALE")
                    val genero = try { GeneroAudio.valueOf(genStr) } catch (_: Exception) { GeneroAudio.FEMALE }

                    lista.add(
                        CategoriaItem(
                            id = id,
                            rotulo = rotulo,
                            significado = significado,
                            imagem = imagem,
                            imagem_id = imagemId,
                            audio = audio,
                            audio_id = audioId,
                            ordem = ordem,
                            defaultGender = genero,
                            imageUrl = imageUrl,
                            audioUrl = audioUrl,
                            localPath = localPath
                        )
                    )
                }
                return lista
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return mutableListOf()
    }

    /**
     * Persiste a lista de categorias ativas localmente para consulta global entre telas.
     */
    fun salvarCategoriasAtivasLocalmente(context: Context, categorias: List<Categoria>) {
        try {
            val jsonArray = JSONArray()
            for ((idx, cat) in categorias.withIndex()) {
                val obj = JSONObject().apply {
                    put("id", cat.id)
                    put("nome", cat.nome)
                    put("slug", cat.slug)
                    put("cor", cat.cor)
                    put("ordem", if (cat.ordem >= 0) cat.ordem else idx)
                    put("imageUrl", cat.imageUrl ?: cat.imageUri ?: "")
                    put("audioUrl", cat.audioUrl ?: cat.audioPath ?: "")
                    put("localPath", cat.localPath ?: "")
                }
                jsonArray.put(obj)
            }
            val jsonStr = jsonArray.toString()
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("categorias_ativas_json", jsonStr).apply()
            File(context.filesDir, "categorias_ativas.json").writeText(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun temCategoriasAtivasSalvas(context: Context): Boolean {
        val arq = File(context.filesDir, "categorias_ativas.json")
        if (arq.exists() && arq.length() > 0) return true
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains("categorias_ativas_json")
    }

    fun carregarCategoriasAtivasSalvas(context: Context): List<Categoria> {
        try {
            var jsonStr: String? = null
            val arq = File(context.filesDir, "categorias_ativas.json")
            if (arq.exists() && arq.length() > 0) {
                jsonStr = arq.readText()
            }
            if (jsonStr.isNullOrBlank()) {
                val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
                jsonStr = prefs.getString("categorias_ativas_json", null)
            }
            if (!jsonStr.isNullOrBlank()) {
                val arr = JSONArray(jsonStr)
                val lista = mutableListOf<Categoria>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    val nome = obj.optString("nome", id)
                    val slug = obj.optString("slug", nome.lowercase().replace(" ", "_"))
                    val cor = obj.optString("cor", "#4A90E2")
                    val ordem = obj.optInt("ordem", i)
                    val imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() }
                    val audioUrl = obj.optString("audioUrl").takeIf { it.isNotBlank() }
                    val localPath = obj.optString("localPath").takeIf { it.isNotBlank() }
                    lista.add(
                        Categoria(
                            id = id,
                            nome = nome,
                            slug = slug,
                            cor = cor,
                            ordem = ordem,
                            imageUrl = imageUrl,
                            imageUri = imageUrl,
                            audioUrl = audioUrl,
                            audioPath = audioUrl,
                            localPath = localPath,
                            botoes = mutableListOf()
                        )
                    )
                }
                return lista
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }

    /**
     * Remove os registros locais de uma categoria excluída para liberar seus botões predefinidos.
     */
    fun removerCategoriaLocalmente(context: Context, categoriaId: String, slug: String = "") {
        try {
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            val idsParaLimpar = listOf(categoriaId, slug).filter { it.isNotBlank() }.distinct()
            for (idAlvo in idsParaLimpar) {
                editor.remove("botoes_categoria_$idAlvo")
                editor.remove("cat_${idAlvo}_nome")
                editor.remove("cat_${idAlvo}_slug")
                editor.remove("cat_${idAlvo}_localPath")
                editor.remove("cat_${idAlvo}_audioPath")
                val arqBotoes = File(context.filesDir, "botoes_${idAlvo}.json")
                if (arqBotoes.exists()) {
                    arqBotoes.delete()
                }
            }
            editor.apply()

            if (temCategoriasAtivasSalvas(context)) {
                val ativas = carregarCategoriasAtivasSalvas(context).filterNot { cat ->
                    CategoriaRepository.ehMesmaCategoria(cat, categoriaId) ||
                        (slug.isNotBlank() && CategoriaRepository.ehMesmaCategoria(cat, slug))
                }
                salvarCategoriasAtivasLocalmente(context, ativas)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Carrega o estado global de todas as categorias existentes e seus respectivos botões.
     */
    fun carregarCategoriasComBotoesGlobalmente(
        context: Context,
        categoriaIdAtual: String = "",
        categoriaNomeAtual: String = ""
    ): List<Categoria> {
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val categoriasBase = if (temCategoriasAtivasSalvas(context)) {
            carregarCategoriasAtivasSalvas(context).toMutableList()
        } else {
            CategoriaRepository.getCategoriasPadrao()
                .map { it.copy(botoes = it.botoes.toMutableList()) }
                .toMutableList()
        }

        if (categoriaIdAtual.isNotBlank()) {
            val jaPresente = categoriasBase.any { CategoriaRepository.ehMesmaCategoria(it, categoriaIdAtual) }
            if (!jaPresente) {
                val nomeCat = categoriaNomeAtual.ifBlank {
                    prefs.getString("cat_${categoriaIdAtual}_nome", null) ?: categoriaIdAtual
                }
                val slugCat = prefs.getString("cat_${categoriaIdAtual}_slug", null)
                    ?: nomeCat.lowercase().replace(" ", "_")
                categoriasBase.add(
                    Categoria(
                        id = categoriaIdAtual,
                        nome = nomeCat,
                        slug = slugCat,
                        botoes = mutableListOf()
                    )
                )
            }
        }

        // Também verifica arquivos botoes_*.json salvos diretamente no dispositivo
        val arquivosBotoes = context.filesDir.listFiles { _, name ->
            name.startsWith("botoes_") && name.endsWith(".json")
        } ?: emptyArray()

        for (arq in arquivosBotoes) {
            val catIdArq = arq.name.removePrefix("botoes_").removeSuffix(".json")
            if (catIdArq.isNotBlank()) {
                val jaMapeada = categoriasBase.any { CategoriaRepository.ehMesmaCategoria(it, catIdArq) }
                if (!jaMapeada && !temCategoriasAtivasSalvas(context)) {
                    val nomeCat = prefs.getString("cat_${catIdArq}_nome", null) ?: catIdArq
                    val slugCat = prefs.getString("cat_${catIdArq}_slug", null) ?: catIdArq
                    categoriasBase.add(
                        Categoria(
                            id = catIdArq,
                            nome = nomeCat,
                            slug = slugCat,
                            botoes = mutableListOf()
                        )
                    )
                }
            }
        }

        val padroes = CategoriaRepository.getCategoriasPadrao()
        return categoriasBase.map { cat ->
            val botoesFinais: MutableList<CategoriaItem> = when {
                temBotoesSalvosLocalmente(context, cat.id) -> {
                    carregarBotoesCategoriaLocalmente(context, cat.id)
                }
                cat.slug.isNotBlank() && temBotoesSalvosLocalmente(context, cat.slug) -> {
                    carregarBotoesCategoriaLocalmente(context, cat.slug)
                }
                cat.botoes.isNotEmpty() -> {
                    cat.botoes.toMutableList()
                }
                else -> {
                    val padrao = padroes.find {
                        CategoriaRepository.ehMesmaCategoria(it, cat.id) ||
                            (cat.slug.isNotBlank() && CategoriaRepository.ehMesmaCategoria(it, cat.slug)) ||
                            (cat.nome.isNotBlank() && it.nome.equals(cat.nome, ignoreCase = true))
                    }
                    padrao?.botoes?.map { it.copy() }?.toMutableList() ?: mutableListOf()
                }
            }
            cat.copy(botoes = botoesFinais)
        }
    }

    /**
     * Verifica se o botão candidato conflita globalmente com qualquer botão em qualquer categoria existente.
     */
    fun verificarConflitoGlobalBotao(
        context: Context,
        candidato: CategoriaItem,
        categoriaIdAtual: String = "",
        categoriaNomeAtual: String = "",
        ignorarBotaoId: String = ""
    ): CategoriaRepository.ConflitoBotaoGlobal? {
        val todasCategorias = carregarCategoriasComBotoesGlobalmente(context, categoriaIdAtual, categoriaNomeAtual)
        return CategoriaRepository.verificarConflitoGlobalBotao(
            categorias = todasCategorias,
            candidato = candidato,
            categoriaIdAtual = categoriaIdAtual,
            ignorarBotaoId = ignorarBotaoId
        )
    }

    /**
     * Retorna o mapa de todos os DesenhoBotao em uso globalmente em qualquer categoria.
     */
    fun obterMapaDesenhosEmUsoGlobal(
        context: Context,
        categoriaIdAtual: String = "",
        categoriaNomeAtual: String = "",
        ignorarBotaoId: String = ""
    ): Map<DesenhoBotao, String> {
        val todasCategorias = carregarCategoriasComBotoesGlobalmente(context, categoriaIdAtual, categoriaNomeAtual)
        return CategoriaRepository.obterMapaDesenhosEmUsoGlobal(
            categorias = todasCategorias,
            categoriaIdAtual = categoriaIdAtual,
            ignorarBotaoId = ignorarBotaoId
        )
    }

    /**
     * Retorna apenas os DesenhoBotao predefinidos que ainda não estão em uso em nenhuma categoria.
     */
    fun obterDesenhosDisponiveisGlobalmente(
        context: Context,
        categoriaIdAtual: String = "",
        categoriaNomeAtual: String = "",
        ignorarBotaoId: String = ""
    ): List<DesenhoBotao> {
        val todasCategorias = carregarCategoriasComBotoesGlobalmente(context, categoriaIdAtual, categoriaNomeAtual)
        return CategoriaRepository.filtrarDesenhosDisponiveisGlobalmente(
            categorias = todasCategorias,
            categoriaIdAtual = categoriaIdAtual,
            ignorarBotaoId = ignorarBotaoId
        )
    }

    /**
     * Filtra uma lista de botões candidatos removendo itens que já estejam alocados em outras categorias
     * ou duplicados dentro da própria lista.
     */
    fun filtrarBotoesSemConflitoGlobal(
        context: Context,
        botoesCandidatos: List<CategoriaItem>,
        categoriaIdAtual: String,
        categoriaNomeAtual: String = ""
    ): MutableList<CategoriaItem> {
        val outrasCategorias = carregarCategoriasComBotoesGlobalmente(context, categoriaIdAtual, categoriaNomeAtual)
            .filterNot { CategoriaRepository.ehMesmaCategoria(it, categoriaIdAtual) }
        val aceitos = mutableListOf<CategoriaItem>()

        for (item in botoesCandidatos) {
            val conflitoOutras = CategoriaRepository.verificarConflitoGlobalBotao(
                categorias = outrasCategorias,
                candidato = item,
                categoriaIdAtual = categoriaIdAtual,
                ignorarBotaoId = ""
            )
            val conflitoInterno = aceitos.any { CategoriaRepository.botoesConflitam(it, item) }
            if (conflitoOutras == null && !conflitoInterno) {
                aceitos.add(item)
            }
        }
        aceitos.forEachIndexed { idx, b -> b.ordem = idx }
        return aceitos
    }
}
