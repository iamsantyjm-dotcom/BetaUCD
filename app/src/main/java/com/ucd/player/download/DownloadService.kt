package com.ucd.player.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ucd.player.ui.DownloadActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Servicio en primer plano: mantiene viva la descarga (con notificación y botón Cancelar) aunque salgas de la app. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var notifJob: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()

        // Botón "Cancelar" de la notificación
        if (intent?.action == ACTION_CANCEL) {
            Downloader.cancel()
            if (job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }

        // Obligatorio: pasar a primer plano enseguida (si no, Android cierra la app)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Video"
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotif(title, -1f, "Preparando…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null || job?.isActive == true) {
            if (job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        val req = Downloader.Request(
            url = url,
            title = title,
            height = intent.getIntExtra(EXTRA_HEIGHT, 0),
            audioOnly = intent.getBooleanExtra(EXTRA_AUDIO, false),
            parts = intent.getIntExtra(EXTRA_PARTS, 1),
        )

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ucd:download").apply { acquire(3 * 60 * 60 * 1000L) }

        // Refresca la notificación con el progreso (solo cuando cambia el porcentaje o la fase)
        notifJob = scope.launch {
            var last = ""
            Downloads.state.collect { s ->
                if (s.status == DlStatus.Running) {
                    val key = (s.progress * 100).toInt().toString() + "|" + s.phase
                    if (key != last) {
                        last = key
                        val text = if (s.detail.isNotBlank()) s.phase + " · " + s.detail else s.phase
                        notify(NOTIF_ID, buildNotif(s.title, s.progress, text))
                    }
                }
            }
        }

        job = scope.launch {
            var finalTitle = ""
            var finalText = ""
            try {
                val where = Downloader.download(applicationContext, req)
                finalTitle = "Descarga completa"
                finalText = req.title + " · " + where
            } catch (e: Throwable) {
                if (e is CancellationException || Downloader.cancelRequested) {
                    Downloads.set(DlState(DlStatus.Cancelled, req.title))
                    finalTitle = "Descarga cancelada"
                    finalText = req.title
                } else {
                    val msg = friendlyError(e)
                    Downloads.set(DlState(DlStatus.Failed, req.title, message = msg))
                    finalTitle = "No se pudo descargar"
                    finalText = msg
                }
            } finally {
                notifJob?.cancel()
                runCatching { if (wake?.isHeld == true) wake?.release() }
            }
            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            notify(NOTIF_ID + 1, buildFinalNotif(finalTitle, finalText))
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (job?.isActive == true) Downloader.cancel()
        runCatching { if (wake?.isHeld == true) wake?.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun friendlyError(e: Throwable): String {
        val msg = (e.message ?: e.toString()).lines().lastOrNull { it.isNotBlank() } ?: "Error desconocido"
        return msg.take(300)
    }

    private fun notify(id: Int, n: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(id, n) }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Descargas", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, DownloadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotif(title: String, progress: Float, text: String): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancel)
        if (progress >= 0f) b.setProgress(100, (progress * 100).toInt(), false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun buildFinalNotif(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent())
            .setAutoCancel(true)
            .build()

    companion object {
        const val ACTION_CANCEL = "com.ucd.player.download.CANCEL"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_HEIGHT = "height"
        private const val EXTRA_AUDIO = "audio"
        private const val EXTRA_PARTS = "parts"
        private const val CHANNEL = "ucd_downloads"
        private const val NOTIF_ID = 4101

        fun start(ctx: Context, url: String, title: String, height: Int, audioOnly: Boolean, parts: Int) {
            val i = Intent(ctx, DownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_HEIGHT, height)
                .putExtra(EXTRA_AUDIO, audioOnly)
                .putExtra(EXTRA_PARTS, parts)
            ContextCompat.startForegroundService(ctx, i)
        }
    }
}
