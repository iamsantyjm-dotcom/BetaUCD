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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio en primer plano: procesa la cola de descargas de a una, con notificación y botón Cancelar
 * (cancela la que está bajando y sigue con la siguiente), aunque salgas de la app.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var notifJob: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DlQueue.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()

        // Botón "Cancelar" de la notificación: cancela la descarga actual; la cola sigue
        if (intent?.action == ACTION_CANCEL) {
            Downloader.cancel()
            if (job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }

        // Obligatorio: pasar a primer plano enseguida (si no, Android cierra la app)
        val first = DlQueue.nextQueued()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotif(first?.title ?: "Descargas", -1f, "Preparando…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (first == null && job?.isActive != true) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        pump()
        return START_NOT_STICKY
    }

    /** Arranca el procesamiento de la cola si no está en marcha. */
    private fun pump() {
        if (job?.isActive == true) return
        DlQueue.setActive(true)
        if (wake?.isHeld != true) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ucd:download").apply { acquire(3 * 60 * 60 * 1000L) }
        }

        // Refresca la notificación con el progreso (solo cuando cambia el porcentaje o la fase)
        if (notifJob?.isActive != true) {
            notifJob = scope.launch {
                var last = ""
                Downloads.state.collect { s ->
                    if (s.status == DlStatus.Running) {
                        val waiting = DlQueue.items.value.count { it.status == JobStatus.Queued }
                        val key = (s.progress * 100).toInt().toString() + "|" + s.phase + "|" + waiting
                        if (key != last) {
                            last = key
                            var text = if (s.detail.isNotBlank()) s.phase + " · " + s.detail else s.phase
                            if (waiting > 0) text += " · +$waiting en cola"
                            notify(NOTIF_ID, buildNotif(s.title, s.progress, text))
                        }
                    }
                }
            }
        }

        job = scope.launch {
            while (isActive) {
                val next = DlQueue.nextQueued() ?: break
                runOne(next)
            }
            withContext(Dispatchers.Main) { finishOrRestart() }
        }
    }

    /** Corre en el hilo principal, igual que onStartCommand, así no se pierde ninguna descarga nueva. */
    private fun finishOrRestart() {
        if (DlQueue.nextQueued() != null) {
            job = null
            pump()
            return
        }
        notifJob?.cancel()
        runCatching { if (wake?.isHeld == true) wake?.release() }
        DlQueue.setActive(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runOne(e: DlEntry) {
        DlQueue.markRunning(e.id)
        val req = Downloader.Request(e.url, e.title, e.height, e.audioOnly, e.parts, e.tree)
        var finalTitle: String
        var finalText: String
        try {
            val where = Downloader.download(applicationContext, req)
            DlQueue.finish(e.id, JobStatus.Done, where, Downloads.state.value.uri)
            finalTitle = "Descarga completa"
            finalText = e.title + " · " + where
        } catch (t: Throwable) {
            if (t is CancellationException || Downloader.cancelRequested) {
                Downloads.set(DlState(DlStatus.Cancelled, e.title))
                DlQueue.finish(e.id, JobStatus.Cancelled)
                finalTitle = "Descarga cancelada"
                finalText = e.title
            } else {
                val msg = friendlyError(t)
                Downloads.set(DlState(DlStatus.Failed, e.title, message = msg))
                DlQueue.finish(e.id, JobStatus.Failed, msg)
                finalTitle = "No se pudo descargar"
                finalText = e.title + " · " + msg.lines().first()
            }
        }
        notify(NOTIF_ID + 1 + (e.id % 1000).toInt(), buildFinalNotif(finalTitle, finalText))
    }

    override fun onDestroy() {
        if (job?.isActive == true) Downloader.cancel()
        runCatching { if (wake?.isHeld == true) wake?.release() }
        DlQueue.setActive(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun friendlyError(e: Throwable): String = Errors.explain(e.message ?: e.toString())

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
        private const val CHANNEL = "ucd_downloads"
        private const val NOTIF_ID = 4101

        /** Añade una descarga a la cola y pone el servicio a trabajar. Devuelve false si ya estaba en la cola. */
        fun enqueue(
            ctx: Context, url: String, title: String, height: Int, audioOnly: Boolean, parts: Int, tree: String?,
        ): Boolean {
            DlQueue.init(ctx)
            if (!DlQueue.add(url, title, height, audioOnly, parts, tree)) return false
            kick(ctx)
            return true
        }

        /** Arranca (o reanuda) el procesamiento de la cola. */
        fun kick(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        }
    }
}
