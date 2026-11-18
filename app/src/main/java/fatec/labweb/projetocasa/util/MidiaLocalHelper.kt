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

object MidiaLocalHelper {

    fun salvarImagemLocalmente(context: Context, botaoId: String, fonte: Any): File? {
        if (botaoId.isBlank()) return null
        val pastaDestino = context.getExternalFilesDir("botoes_imagens")
            ?: File(context.filesDir, "botoes_imagens")
        if (!pastaDestino.exists()) {
            pastaDestino.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val arquivoTimestamp = File(pastaDestino, "asset_${botaoId}_${timestamp}.jpg")
        val arquivoCanonico = File(pastaDestino, "$botaoId.jpg")

        return try {
            when (fonte) {
                is Uri -> {
                    val tempFile = File(context.cacheDir, "temp_img_${timestamp}.jpg")
                    val copiadoViaResolver = try {
                        context.contentResolver.openInputStream(fonte)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        } != null
                    } catch (_: Exception) {
                        false
                    }

                    if (!copiadoViaResolver || !tempFile.exists() || tempFile.length() == 0L) {
                        val pathUri = fonte.path
                        if (!pathUri.isNullOrBlank()) {
                            val fOrig = File(pathUri)
                            if (fOrig.exists() && fOrig.length() > 0) {
                                fOrig.copyTo(tempFile, overwrite = true)
                            }
                        }
                    }

                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.copyTo(arquivoTimestamp, overwrite = true)
                        tempFile.copyTo(arquivoCanonico, overwrite = true)
                        tempFile.delete()
                    } else if (arquivoCanonico.exists() && arquivoCanonico.length() > 0) {
                        arquivoCanonico.copyTo(arquivoTimestamp, overwrite = true)
                    }
                }
                is File -> {
                    if (fonte.exists() && fonte.length() > 0) {
                        if (fonte.absolutePath != arquivoTimestamp.absolutePath) {
                            fonte.copyTo(arquivoTimestamp, overwrite = true)
                        }
                        if (fonte.absolutePath != arquivoCanonico.absolutePath) {
                            fonte.copyTo(arquivoCanonico, overwrite = true)
                        }
                    }
                }
                is Int -> {
                    val drawable = ContextCompat.getDrawable(context, fonte) ?: return null
                    salvarDrawableComoJpg(drawable, arquivoTimestamp)
                    if (arquivoTimestamp.exists() && arquivoTimestamp.length() > 0) {
                        arquivoTimestamp.copyTo(arquivoCanonico, overwrite = true)
                    }
                }
                is Bitmap -> {
                    FileOutputStream(arquivoTimestamp).use { out ->
                        fonte.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    if (arquivoTimestamp.exists() && arquivoTimestamp.length() > 0) {
                        arquivoTimestamp.copyTo(arquivoCanonico, overwrite = true)
                    }
                }
                else -> return null
            }

            // Remove versões antigas com timestamp do mesmo id para não acumular lixo
            pastaDestino.listFiles { _, name ->
                name.startsWith("asset_${botaoId}_") && name != arquivoTimestamp.name
            }?.forEach { try { it.delete() } catch (_: Exception) {} }

            val finalFile = when {
                arquivoTimestamp.exists() && arquivoTimestamp.length() > 0 -> arquivoTimestamp
                arquivoCanonico.exists() && arquivoCanonico.length() > 0 -> arquivoCanonico
                else -> null
            }

            if (finalFile != null) {
                val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("midia_img_path_$botaoId", finalFile.absolutePath)
                    .putLong("midia_img_ts_$botaoId", timestamp)
                    .apply()
            }
            finalFile
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
        if (botaoId.isBlank()) return null
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val candidatosPrefs = listOfNotNull(
            prefs.getString("midia_img_path_$botaoId", null),
            prefs.getString("botao_${botaoId}_imagem_path", null),
            prefs.getString("cat_${botaoId}_localPath", null)
        )
        for (caminho in candidatosPrefs) {
            if (caminho.isNotBlank()) {
                val f = File(caminho)
                if (f.exists() && f.length() > 0) return f
            }
        }

        val pasta = context.getExternalFilesDir("botoes_imagens")
            ?: File(context.filesDir, "botoes_imagens")
        val maisRecenteTimestamp = pasta.listFiles { _, name ->
            name.startsWith("asset_${botaoId}_") && name.endsWith(".jpg")
        }?.filter { it.exists() && it.length() > 0 }
            ?.maxByOrNull { it.lastModified() }
        if (maisRecenteTimestamp != null) return maisRecenteTimestamp

        val arquivo = File(pasta, "$botaoId.jpg")
        return if (arquivo.exists() && arquivo.length() > 0) arquivo else null
    }

    fun limparImagemLocal(context: Context, botaoId: String) {
        if (botaoId.isBlank()) return
        try {
            val pasta = context.getExternalFilesDir("botoes_imagens")
                ?: File(context.filesDir, "botoes_imagens")
            pasta.listFiles { _, name ->
                name == "$botaoId.jpg" || name.startsWith("asset_${botaoId}_")
            }?.forEach { try { it.delete() } catch (_: Exception) {} }

            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("midia_img_path_$botaoId")
                .remove("botao_${botaoId}_imagem_path")
                .remove("cat_${botaoId}_localPath")
                .apply()
        } catch (_: Exception) {}
    }

    fun limparAudiosAntigos(context: Context, id: String) {
        if (id.isBlank()) return
        try {
            val pastas = listOfNotNull(
                context.getExternalFilesDir("botoes_audios"),
                context.getExternalFilesDir("categorias_audios"),
                File(context.filesDir, "botoes_audios")
            )
            for (pasta in pastas) {
                if (!pasta.exists()) continue
                pasta.listFiles { _, name ->
                    name.startsWith("${id}.") ||
                        name.startsWith("${id}_") ||
                        name.startsWith("cat_${id}_") ||
                        name.startsWith("asset_${id}_")
                }?.forEach { try { it.delete() } catch (_: Exception) {} }
            }
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("midia_audio_path_$id")
                .remove("botao_${id}_audio_path")
                .remove("cat_${id}_audioPath")
                .apply()
        } catch (_: Exception) {}
    }

    fun salvarAudioLocalmente(context: Context, id: String, fonte: Any): File? {
        if (id.isBlank()) return null
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
        val timestamp = System.currentTimeMillis()
        val arquivoTimestamp = File(pastaDestino, "asset_${id}_${timestamp}.$ext")
        val arquivoCanonico = File(pastaDestino, "$id.$ext")

        return try {
            when (fonte) {
                is Uri -> {
                    val tempFile = File(context.cacheDir, "temp_audio_${timestamp}.$ext")
                    val ok = try {
                        context.contentResolver.openInputStream(fonte)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        } != null
                    } catch (_: Exception) {
                        false
                    }
                    if (!ok || !tempFile.exists() || tempFile.length() == 0L) {
                        val p = fonte.path
                        if (!p.isNullOrBlank()) {
                            val fOrig = File(p)
                            if (fOrig.exists() && fOrig.length() > 0) {
                                fOrig.copyTo(tempFile, overwrite = true)
                            }
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.copyTo(arquivoTimestamp, overwrite = true)
                        tempFile.copyTo(arquivoCanonico, overwrite = true)
                        tempFile.delete()
                    }
                }
                is File -> {
                    if (fonte.exists() && fonte.length() > 0) {
                        if (fonte.absolutePath != arquivoTimestamp.absolutePath) {
                            fonte.copyTo(arquivoTimestamp, overwrite = true)
                        }
                        if (fonte.absolutePath != arquivoCanonico.absolutePath) {
                            fonte.copyTo(arquivoCanonico, overwrite = true)
                        }
                    }
                }
                else -> return null
            }

            // Limpa arquivos de outras extensões ou timestamps antigos do mesmo id
            pastaDestino.listFiles { _, name ->
                (name.startsWith("asset_${id}_") && name != arquivoTimestamp.name) ||
                    (name.startsWith("$id.") && name != arquivoCanonico.name)
            }?.forEach { try { it.delete() } catch (_: Exception) {} }

            val finalFile = when {
                arquivoTimestamp.exists() && arquivoTimestamp.length() > 0 -> arquivoTimestamp
                arquivoCanonico.exists() && arquivoCanonico.length() > 0 -> arquivoCanonico
                else -> null
            }

            if (finalFile != null) {
                val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("midia_audio_path_$id", finalFile.absolutePath)
                    .putLong("midia_audio_ts_$id", timestamp)
                    .apply()
            }
            finalFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun obterAudioLocal(context: Context, id: String): File? {
        if (id.isBlank()) return null
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val caminhosSalvos = listOfNotNull(
            prefs.getString("midia_audio_path_$id", null),
            prefs.getString("botao_${id}_audio_path", null),
            prefs.getString("cat_${id}_audioPath", null)
        )
        for (caminho in caminhosSalvos) {
            if (caminho.isNotBlank()) {
                val f = File(caminho)
                if (f.exists() && f.length() > 0) return f
            }
        }

        val pasta = context.getExternalFilesDir("botoes_audios")
            ?: File(context.filesDir, "botoes_audios")
        val maisRecente = pasta.listFiles { _, name ->
            name.startsWith("asset_${id}_")
        }?.filter { it.exists() && it.length() > 0 }
            ?.maxByOrNull { it.lastModified() }
        if (maisRecente != null) return maisRecente

        val extensoes = listOf("wav", "mp3", "m4a")
        for (ext in extensoes) {
            val f = File(pasta, "$id.$ext")
            if (f.exists() && f.length() > 0) return f
        }
        val arquivos = pasta.listFiles { _, name -> name.startsWith(id) }
        return arquivos?.filter { it.exists() && it.length() > 0 }?.maxByOrNull { it.lastModified() }
    }

    // ==========================================
    // PREFERÊNCIA DE VOZ (MASCULINA vs. FEMININA)
    // ==========================================

    fun salvarPreferenciaVozGlobal(context: Context, genero: GeneroAudio) {
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("selected_voice_gender", genero.name).apply()
    }

    fun obterPreferenciaVozGlobal(context: Context): GeneroAudio {
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val genStr = prefs.getString("selected_voice_gender", null)
        return try {
            if (!genStr.isNullOrBlank()) GeneroAudio.valueOf(genStr) else GeneroAudio.FEMALE
        } catch (_: Exception) {
            GeneroAudio.FEMALE
        }
    }

    fun salvarGeneroBotao(context: Context, botaoId: String, genero: GeneroAudio) {
        if (botaoId.isBlank()) return
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("botao_${botaoId}_genero", genero.name)
            .putString("selected_voice_gender", genero.name)
            .apply()
    }

    fun obterGeneroBotao(
        context: Context,
        botaoId: String,
        fallback: GeneroAudio = GeneroAudio.FEMALE
    ): GeneroAudio {
        if (botaoId.isBlank()) return fallback
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val salvo = prefs.getString("botao_${botaoId}_genero", null)
        return try {
            if (!salvo.isNullOrBlank()) GeneroAudio.valueOf(salvo) else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    fun salvarGeneroCategoria(context: Context, categoriaId: String, genero: GeneroAudio) {
        if (categoriaId.isBlank()) return
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("cat_${categoriaId}_genero", genero.name)
            .putString("selected_voice_gender", genero.name)
            .apply()
    }

    fun obterGeneroCategoria(
        context: Context,
        categoriaId: String,
        fallback: GeneroAudio? = null
    ): GeneroAudio {
        if (categoriaId.isNotBlank()) {
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            val salvo = prefs.getString("cat_${categoriaId}_genero", null)
            if (!salvo.isNullOrBlank()) {
                try {
                    return GeneroAudio.valueOf(salvo)
                } catch (_: Exception) {}
            }
        }
        return fallback ?: obterPreferenciaVozGlobal(context)
    }

    // ==========================================
    // PERFIL DO USUÁRIO (AVATAR 240x240 E NOME)
    // ==========================================

    fun salvarAvatarRecortado240(context: Context, bitmap: Bitmap): File? {
        val bitmap240 = if (bitmap.width == 240 && bitmap.height == 240) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, 240, 240, true)
        }

        val pastaPerfil = context.getExternalFilesDir("perfil")
            ?: File(context.filesDir, "perfil")
        if (!pastaPerfil.exists()) pastaPerfil.mkdirs()

        val pastaInterna = File(context.filesDir, "perfil")
        if (!pastaInterna.exists()) pastaInterna.mkdirs()

        val ts = System.currentTimeMillis()
        val arquivoTimestamp = File(pastaPerfil, "asset_perfil_${ts}.jpg")
        val arquivoPadrao = File(pastaPerfil, "foto_perfil.jpg")
        val arquivoInterno = File(pastaInterna, "foto_perfil.jpg")

        return try {
            FileOutputStream(arquivoTimestamp).use { out ->
                bitmap240.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            arquivoTimestamp.copyTo(arquivoPadrao, overwrite = true)
            arquivoTimestamp.copyTo(arquivoInterno, overwrite = true)

            pastaPerfil.listFiles { _, name ->
                name.startsWith("asset_perfil_") && name != arquivoTimestamp.name
            }?.forEach { try { it.delete() } catch (_: Exception) {} }

            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("perfil_foto_path", arquivoTimestamp.absolutePath)
                .putLong("perfil_foto_ts", ts)
                .apply()

            arquivoTimestamp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun salvarPerfilLocalmente(context: Context, imageUri: Uri): File? {
        val pastaPerfil = context.getExternalFilesDir("perfil")
            ?: File(context.filesDir, "perfil")
        if (!pastaPerfil.exists()) {
            pastaPerfil.mkdirs()
        }
        val ts = System.currentTimeMillis()
        val perfilTimestampFile = File(pastaPerfil, "asset_perfil_${ts}.jpg")
        val perfilLocalFile = File(pastaPerfil, "foto_perfil.jpg")
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(perfilTimestampFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (perfilTimestampFile.exists() && perfilTimestampFile.length() > 0) {
                perfilTimestampFile.copyTo(perfilLocalFile, overwrite = true)
                val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("perfil_foto_path", perfilTimestampFile.absolutePath)
                    .putLong("perfil_foto_ts", ts)
                    .apply()
                perfilTimestampFile
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun obterPerfilLocal(context: Context): File? {
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val salvo = prefs.getString("perfil_foto_path", null)
        if (!salvo.isNullOrBlank()) {
            val f = File(salvo)
            if (f.exists() && f.length() > 0) return f
        }

        val pastaPerfil = context.getExternalFilesDir("perfil")
            ?: File(context.filesDir, "perfil")
        val recente = pastaPerfil.listFiles { _, name ->
            name.startsWith("asset_perfil_") && name.endsWith(".jpg")
        }?.filter { it.exists() && it.length() > 0 }
            ?.maxByOrNull { it.lastModified() }
        if (recente != null) return recente

        val arquivo = File(pastaPerfil, "foto_perfil.jpg")
        if (arquivo.exists() && arquivo.length() > 0) return arquivo

        val interno = File(context.filesDir, "perfil/foto_perfil.jpg")
        return if (interno.exists() && interno.length() > 0) interno else null
    }

    fun salvarNomePerfilLocal(context: Context, nome: String) {
        if (nome.isBlank()) return
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("perfil_nome_usuario", nome.trim()).apply()
    }

    fun obterNomePerfilLocal(context: Context): String? {
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("perfil_nome_usuario", null)?.takeIf { it.isNotBlank() }
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
     * e no SharedPreferences, segregando entidades pré-definidas (imutáveis/somente leitura)
     * das entidades personalizadas (mutáveis e persistentes).
     */
    fun salvarBotoesCategoriaLocalmente(context: Context, categoriaId: String, lista: List<CategoriaItem>) {
        if (categoriaId.isBlank()) return
        try {
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            val jsonArray = JSONArray()
            val acoesPersonalizadas = mutableListOf<CategoriaItem>()

            for (rawItem in lista) {
                val isPredef = if (rawItem.isCustom && !rawItem.isPredefined) {
                    false
                } else {
                    rawItem.isPredefined || CategoriaRepository.isAcaoPredefinida(rawItem)
                }
                val item = if (isPredef) {
                    CategoriaRepository.blindarAcaoPredefinida(rawItem)
                } else {
                    rawItem.copy(isPredefined = false, isCustom = true).apply {
                        this.imageUri = rawItem.imageUri
                        this.audioPath = rawItem.audioPath
                    }
                }

                if (item.isCustom) {
                    acoesPersonalizadas.add(item)
                }

                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("rotulo", item.rotulo)
                    put("nome", item.rotulo)
                    put("significado", item.significado)
                    put("categoriaId", categoriaId)
                    put("categoria_id", categoriaId)
                    put("localImagePath", if (item.isCustom) (item.localPath ?: "") else "")
                    put("localPath", if (item.isCustom) (item.localPath ?: "") else "")
                    put("localAudioPath", if (item.isCustom) (item.audioPath ?: item.audioUrl ?: "") else "")
                    put("audioPath", if (item.isCustom) (item.audioPath ?: item.audioUrl ?: "") else "")
                    put("imageUrl", item.imageUrl ?: "")
                    put("imageUri", item.imageUri ?: item.imageUrl ?: "")
                    put("audioUrl", item.audioUrl ?: "")
                    put("imagem", item.imagem ?: "")
                    put("imagem_id", item.imagem_id ?: "")
                    put("audio", item.audio ?: "")
                    put("audio_id", item.audio_id ?: "")
                    put("ordem", item.ordem)
                    put("defaultGender", item.defaultGender.name)
                    put("tipo_audio", if (item.defaultGender == GeneroAudio.MALE) "masculino" else "feminino")
                    put("isPredefined", item.isPredefined)
                    put("isCustom", item.isCustom)
                }
                jsonArray.put(obj)

                if (item.id.isNotBlank()) {
                    editor.putBoolean("botao_${item.id}_is_predefined", item.isPredefined)
                    editor.putBoolean("botao_${item.id}_is_custom", item.isCustom)
                    editor.putString("botao_${item.id}_genero", item.defaultGender.name)
                    if (item.isCustom) {
                        editor.putString("botao_${item.id}_rotulo", item.rotulo)
                        if (!item.localPath.isNullOrBlank()) {
                            editor.putString("botao_${item.id}_imagem_path", item.localPath)
                        }
                        val audioLocal = item.audioPath ?: item.audioUrl
                        if (!audioLocal.isNullOrBlank()) {
                            editor.putString("botao_${item.id}_audio_path", audioLocal)
                        }
                    } else {
                        // Ações pré-definidas são somente leitura: não permite sobrescrita local de título ou imagem
                        editor.remove("botao_${item.id}_rotulo")
                        editor.remove("botao_${item.id}_imagem_path")
                        editor.remove("midia_img_path_${item.id}")
                    }
                }
            }
            val jsonStr = jsonArray.toString()

            // 1. Persistência em SharedPreferences
            editor.putString("botoes_categoria_$categoriaId", jsonStr).apply()

            // 2. Persistência física em arquivo JSON no filesDir
            val arquivoJson = File(context.filesDir, "botoes_${categoriaId}.json")
            arquivoJson.writeText(jsonStr)

            // 3. Sincroniza coleção dedicada de ações personalizadas do usuário
            sincronizarColecaoAcoesPersonalizadas(context, categoriaId, acoesPersonalizadas)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sincronizarColecaoAcoesPersonalizadas(
        context: Context,
        categoriaId: String,
        acoesDaCategoria: List<CategoriaItem>
    ) {
        try {
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            val arquivoCustom = File(context.filesDir, "acoes_personalizadas.json")
            val mapaExistente = linkedMapOf<String, JSONObject>()

            val jsonExistente = if (arquivoCustom.exists() && arquivoCustom.length() > 0) {
                arquivoCustom.readText()
            } else {
                prefs.getString("custom_actions_json", null)
            }

            if (!jsonExistente.isNullOrBlank()) {
                val arrAntigo = JSONArray(jsonExistente)
                for (i in 0 until arrAntigo.length()) {
                    val obj = arrAntigo.getJSONObject(i)
                    val catObj = obj.optString("categoriaId", "")
                    val idObj = obj.optString("id", "")
                    if (idObj.isNotBlank() && !catObj.equals(categoriaId, ignoreCase = true)) {
                        mapaExistente[idObj] = obj
                    }
                }
            }

            for (acao in acoesDaCategoria) {
                if (acao.id.isNotBlank() && acao.isCustom && !acao.isPredefined) {
                    mapaExistente[acao.id] = JSONObject().apply {
                        put("id", acao.id)
                        put("rotulo", acao.rotulo)
                        put("significado", acao.significado)
                        put("categoriaId", categoriaId)
                        put("localPath", acao.localPath ?: "")
                        put("imageUri", acao.imageUri ?: acao.localPath ?: "")
                        put("audioPath", acao.audioPath ?: acao.audioUrl ?: "")
                        put("ordem", acao.ordem)
                        put("defaultGender", acao.defaultGender.name)
                        put("isPredefined", false)
                        put("isCustom", true)
                    }
                }
            }

            val novoArr = JSONArray()
            mapaExistente.values.forEach { novoArr.put(it) }
            val novoJson = novoArr.toString()
            prefs.edit().putString("custom_actions_json", novoJson).apply()
            arquivoCustom.writeText(novoJson)
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
     * Carrega a lista de botões salvos localmente para uma categoria (filesDir ou SharedPreferences),
     * preservando a imutabilidade das ações pré-definidas (`isPredefined = true`) e hidratando
     * integralmente os metadados locais das ações personalizadas (`isCustom = true`).
     */
    fun carregarBotoesCategoriaLocalmente(context: Context, categoriaId: String): MutableList<CategoriaItem> {
        if (categoriaId.isBlank()) return mutableListOf()
        try {
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            var jsonStr: String? = null
            val arquivoJson = File(context.filesDir, "botoes_${categoriaId}.json")
            if (arquivoJson.exists() && arquivoJson.length() > 0) {
                jsonStr = arquivoJson.readText()
            }
            if (jsonStr.isNullOrBlank()) {
                jsonStr = prefs.getString("botoes_categoria_$categoriaId", null)
            }
            if (!jsonStr.isNullOrBlank()) {
                val jsonArray = JSONArray(jsonStr)
                val lista = mutableListOf<CategoriaItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id")
                    val ordem = obj.optInt("ordem", i)

                    val genStr = prefs.getString("botao_${id}_genero", null)
                        ?: obj.optString("defaultGender", "FEMALE")
                    val genero = try { GeneroAudio.valueOf(genStr) } catch (_: Exception) { GeneroAudio.FEMALE }

                    val hasExplicitFlags = obj.has("isPredefined") || obj.has("isCustom")
                    val isCustomFlag = if (hasExplicitFlags) {
                        obj.optBoolean("isCustom", false) && !obj.optBoolean("isPredefined", false)
                    } else {
                        prefs.getBoolean("botao_${id}_is_custom", false)
                    }

                    val rotuloJson = obj.optString("rotulo", obj.optString("nome", "Botão"))
                    val tempItem = CategoriaItem(
                        id = id,
                        rotulo = rotuloJson,
                        significado = obj.optString("significado", rotuloJson).ifBlank { rotuloJson },
                        imagem = obj.optString("imagem").takeIf { it.isNotBlank() },
                        imagem_id = obj.optString("imagem_id").takeIf { it.isNotBlank() },
                        ordem = ordem,
                        defaultGender = genero,
                        localPath = obj.optString("localPath", obj.optString("localImagePath", "")).takeIf { it.isNotBlank() },
                        isPredefined = if (hasExplicitFlags) obj.optBoolean("isPredefined", false) else !isCustomFlag,
                        isCustom = isCustomFlag
                    )

                    val ehPredefinida = !isCustomFlag && CategoriaRepository.isAcaoPredefinida(tempItem)

                    if (ehPredefinida) {
                        // Entidade pré-definida do sistema: estritamente somente leitura e imutável
                        val blindada = CategoriaRepository.blindarAcaoPredefinida(tempItem).copy(
                            ordem = ordem,
                            defaultGender = genero,
                            tipo_audio = if (genero == GeneroAudio.MALE) "masculino" else "feminino",
                            isPredefined = true,
                            isCustom = false
                        )
                        lista.add(blindada)
                    } else {
                        // Entidade personalizada criada pelo usuário: hidrata título, imagem permanente e áudio gravado
                        val rotuloPref = if (id.isNotBlank()) prefs.getString("botao_${id}_rotulo", null) else null
                        val rotulo = rotuloPref?.takeIf { it.isNotBlank() } ?: rotuloJson
                        val significado = obj.optString("significado", rotulo).ifBlank { rotulo }

                        val arqImgLocal = obterArquivoLocal(context, id)
                        val localPath = arqImgLocal?.absolutePath
                            ?: obj.optString("localPath", obj.optString("localImagePath", "")).takeIf { it.isNotBlank() }

                        val arqAudioLocal = obterAudioLocal(context, id)
                        val audioPath = arqAudioLocal?.absolutePath
                            ?: obj.optString("audioPath", obj.optString("localAudioPath", "")).takeIf { it.isNotBlank() }

                        val imageUrl = localPath ?: obj.optString("imageUrl").takeIf { it.isNotBlank() }
                        val imageUri = localPath ?: obj.optString("imageUri").takeIf { it.isNotBlank() } ?: imageUrl
                        val audioUrl = audioPath ?: obj.optString("audioUrl").takeIf { it.isNotBlank() }
                        val imagem = obj.optString("imagem").takeIf { it.isNotBlank() } ?: imageUrl
                        val imagemId = obj.optString("imagem_id").takeIf { it.isNotBlank() } ?: imagem
                        val audio = audioPath ?: obj.optString("audio").takeIf { it.isNotBlank() } ?: audioUrl
                        val audioId = audioPath ?: obj.optString("audio_id").takeIf { it.isNotBlank() } ?: audio

                        lista.add(
                            CategoriaItem(
                                id = id,
                                rotulo = rotulo,
                                significado = significado,
                                imagem = imagem,
                                imagem_id = imagemId,
                                audio = audio,
                                audio_id = audioId,
                                tipo_audio = if (genero == GeneroAudio.MALE) "masculino" else "feminino",
                                ordem = ordem,
                                defaultGender = genero,
                                imageUrl = imageUrl,
                                audioUrl = audioUrl,
                                localPath = localPath,
                                isPredefined = false,
                                isCustom = true
                            ).apply {
                                this.imageUri = imageUri
                                this.audioPath = audioPath
                            }
                        )
                    }
                }
                return lista
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return mutableListOf()
    }

    /**
     * Persiste a lista de categorias ativas localmente para consulta global e hidratação no boot.
     */
    fun salvarCategoriasAtivasLocalmente(context: Context, categorias: List<Categoria>) {
        try {
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            val jsonArray = JSONArray()
            for ((idx, cat) in categorias.withIndex()) {
                val generoCat = obterGeneroCategoria(context, cat.id)
                val localImg = obterArquivoLocal(context, cat.id)?.absolutePath ?: cat.localPath ?: ""
                val localAud = obterAudioLocal(context, cat.id)?.absolutePath ?: cat.audioPath ?: cat.audioUrl ?: ""
                val obj = JSONObject().apply {
                    put("id", cat.id)
                    put("nome", cat.nome)
                    put("slug", cat.slug)
                    put("cor", cat.cor)
                    put("ordem", if (cat.ordem >= 0) cat.ordem else idx)
                    put("imageUrl", localImg.ifBlank { cat.imageUrl ?: cat.imageUri ?: "" })
                    put("audioUrl", localAud.ifBlank { cat.audioUrl ?: cat.audioPath ?: "" })
                    put("localPath", localImg)
                    put("audioPath", localAud)
                    put("defaultGender", generoCat.name)
                }
                jsonArray.put(obj)

                editor.putString("cat_${cat.id}_nome", cat.nome)
                editor.putString("cat_${cat.id}_slug", cat.slug)
                if (localImg.isNotBlank()) editor.putString("cat_${cat.id}_localPath", localImg)
                if (localAud.isNotBlank()) editor.putString("cat_${cat.id}_audioPath", localAud)
            }
            val jsonStr = jsonArray.toString()
            editor.putString("categorias_ativas_json", jsonStr).apply()
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
            val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
            var jsonStr: String? = null
            val arq = File(context.filesDir, "categorias_ativas.json")
            if (arq.exists() && arq.length() > 0) {
                jsonStr = arq.readText()
            }
            if (jsonStr.isNullOrBlank()) {
                jsonStr = prefs.getString("categorias_ativas_json", null)
            }
            if (!jsonStr.isNullOrBlank()) {
                val arr = JSONArray(jsonStr)
                val lista = mutableListOf<Categoria>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    val nomeSalvo = prefs.getString("cat_${id}_nome", null)?.takeIf { it.isNotBlank() }
                        ?: obj.optString("nome", id)
                    val slugSalvo = prefs.getString("cat_${id}_slug", null)?.takeIf { it.isNotBlank() }
                        ?: obj.optString("slug", nomeSalvo.lowercase().replace(" ", "_"))
                    val cor = obj.optString("cor", "#4A90E2")
                    val ordem = obj.optInt("ordem", i)

                    val arqImgLocal = obterArquivoLocal(context, id)
                    val localPath = arqImgLocal?.absolutePath
                        ?: prefs.getString("cat_${id}_localPath", null)?.takeIf { it.isNotBlank() }
                        ?: obj.optString("localPath").takeIf { it.isNotBlank() }

                    val arqAudioLocal = obterAudioLocal(context, id)
                    val audioPath = arqAudioLocal?.absolutePath
                        ?: prefs.getString("cat_${id}_audioPath", null)?.takeIf { it.isNotBlank() }
                        ?: obj.optString("audioPath").takeIf { it.isNotBlank() }
                        ?: obj.optString("audioUrl").takeIf { it.isNotBlank() }

                    val imageUrl = localPath ?: obj.optString("imageUrl").takeIf { it.isNotBlank() }
                    val audioUrl = audioPath ?: obj.optString("audioUrl").takeIf { it.isNotBlank() }

                    val genObj = obj.optString("defaultGender", "")
                    if (genObj.isNotBlank() && !prefs.contains("cat_${id}_genero")) {
                        prefs.edit().putString("cat_${id}_genero", genObj).apply()
                    }

                    val botoesLocais = if (temBotoesSalvosLocalmente(context, id)) {
                        carregarBotoesCategoriaLocalmente(context, id)
                    } else if (slugSalvo.isNotBlank() && temBotoesSalvosLocalmente(context, slugSalvo)) {
                        carregarBotoesCategoriaLocalmente(context, slugSalvo)
                    } else {
                        mutableListOf()
                    }

                    lista.add(
                        Categoria(
                            id = id,
                            nome = nomeSalvo,
                            slug = slugSalvo,
                            cor = cor,
                            ordem = ordem,
                            imageUrl = imageUrl,
                            imageUri = imageUrl,
                            audioUrl = audioUrl,
                            audioPath = audioPath,
                            localPath = localPath,
                            botoes = botoesLocais
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
                editor.remove("cat_${idAlvo}_genero")
                editor.remove("midia_img_path_$idAlvo")
                editor.remove("midia_audio_path_$idAlvo")
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
