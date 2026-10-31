package fatec.labweb.projetocasa.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.view.NotificacoesActivity

class NotificacaoService : FirebaseMessagingService() {

    /**
     * Chamado quando um novo token FCM é gerado para o aplicativo.
     * Ideal para salvar esse token no Firestore vinculado ao usuário logado.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: Se precisar salvar o token no Firestore, faça isso aqui
    }

    /**
     * Chamado quando uma mensagem/notificação chega e o app está em primeiro plano (foreground)
     * ou se a mensagem contiver dados.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Verifica se a mensagem contém uma carga útil de notificação
        remoteMessage.notification?.let {
            val title = it.title ?: "Projeto Casa"
            val body = it.body ?: "Você recebeu uma nova notificação."
            showNotification(title, body)
        }
    }

    /**
     * Cria e exibe a notificação na barra de status do celular.
     */
    private fun showNotification(title: String, message: String) {
        val channelId = "projeto_casa_channel_id"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // A partir do Android 8.0 (Oreo), é obrigatório criar um Canal de Notificação
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Avisos do Projeto Casa",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Canal para notificações gerais do aplicativo"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent para abrir a tela de notificações quando o usuário clicar no alerta
        val intent = Intent(this, NotificacoesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Constrói a notificação
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Certifique-se de ter esse ícone ou troque pelo seu
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Exibe a notificação (usando o timestamp atual como ID para não sobrescrever as anteriores)
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}