package fatec.labweb.projetocasa.service

import android.content.Context
import android.net.Uri
import java.io.File

class StorageService(
    private val processor: Esp32ImageProcessor = Esp32ImageProcessor()
) {

    suspend fun salvarImagemLocalEFirestore(
        context: Context,
        userId: String,
        uri: Uri,
        nomeOriginal: String
    ): Result<ProcessamentoMidiaResult> {
        return processor.processarESalvarImagem(context, userId, uri, nomeOriginal)
    }

    suspend fun obterArquivoLocal(context: Context, nomeArquivo: String): File? {
        return processor.obterArquivoLocalOuRestaurar(context, nomeArquivo)
    }
}
