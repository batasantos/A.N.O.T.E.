package br.unesp.anote

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File

/**
 * Servico em primeiro plano com tres acoes, todas vindas do painel de emergencia:
 *  - ACTION_SOS:       obtem a localizacao e envia SMS com o link do mapa a TODOS os contatos;
 *  - ACTION_SOS_ONE:   o mesmo, mas so para um contato (botao "Disparar");
 *  - ACTION_RECORD:    grava o audio do ambiente em armazenamento privado do app;
 *  - ACTION_STOP_RECORD: para a gravacao.
 * A notificacao e discreta e com aparencia neutra ("Notas - Sincronizando...").
 */
class SosService : Service() {

    private var recorder: MediaRecorder? = null
    private var pendingSms = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_RECORD -> {
                stopRecording()
                finishIfIdle()
            }
            ACTION_RECORD -> {
                enterForeground()
                startRecording()
                if (recorder == null) finishIfIdle()
            }
            ACTION_SOS -> {
                enterForeground()
                sendAlert(null)
            }
            ACTION_SOS_ONE -> {
                enterForeground()
                sendAlert(intent.getStringExtra(EXTRA_PHONE))
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    private fun finishIfIdle() {
        if (pendingSms <= 0 && recorder == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ---------- Notificacao / foreground ----------

    private fun enterForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        var types = 0
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, types)
        } catch (e: Exception) {
            // Sem permissao para nenhum tipo de servico: segue assim mesmo (o SMS e rapido).
        }
    }

    // ---------- Localizacao + SMS ----------

    @SuppressLint("MissingPermission")
    private fun sendAlert(onlyPhone: String?) {
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            finishIfIdle()
            return
        }
        pendingSms++

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            sendSms(null, onlyPhone)
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    sendSms(loc, onlyPhone)
                } else {
                    // Sem fix de GPS agora: usa a ultima posicao conhecida.
                    client.lastLocation
                        .addOnSuccessListener { last -> sendSms(last, onlyPhone) }
                        .addOnFailureListener { sendSms(null, onlyPhone) }
                }
            }
            .addOnFailureListener { sendSms(null, onlyPhone) }
    }

    private fun sendSms(location: Location?, onlyPhone: String?) {
        val userName = SecurePrefs(this).userName
        val text = buildString {
            append(
                if (userName.isNotBlank()) getString(R.string.sos_message_named, userName)
                else getString(R.string.sos_message)
            )
            if (location != null) {
                append(" https://maps.google.com/?q=${location.latitude},${location.longitude}")
            } else {
                append(" indisponível.")
            }
        }
        val smsManager = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        val targets = if (onlyPhone != null) listOf(onlyPhone) else SecurePrefs(this).contacts().map { it.phone }
        targets.forEach { phone ->
            runCatching {
                val parts = smsManager.divideMessage(text)
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            }
        }
        pendingSms--
        finishIfIdle()
    }

    // ---------- Gravacao de audio ----------

    private fun startRecording() {
        if (recorder != null || !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            isRecording = recorder != null
            return
        }
        val dir = File(filesDir, "evidence").apply { mkdirs() }
        val file = File(dir, "audio_${System.currentTimeMillis()}.m4a")

        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.setMaxDuration(MAX_RECORDING_MS)
            r.prepare()
            r.start()
            recorder = r
        }.onFailure {
            runCatching { r.release() }
        }
        isRecording = recorder != null
    }

    private fun stopRecording() {
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
        isRecording = false
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_SOS = "br.unesp.anote.SOS"
        const val ACTION_SOS_ONE = "br.unesp.anote.SOS_ONE"
        const val ACTION_RECORD = "br.unesp.anote.RECORD"
        const val ACTION_STOP_RECORD = "br.unesp.anote.STOP_RECORD"
        const val EXTRA_PHONE = "phone"

        /** Estado lido pelo painel para mostrar "Iniciar" ou "Parar" gravacao. */
        @Volatile
        var isRecording = false

        private const val CHANNEL_ID = "sync"
        private const val NOTIF_ID = 7
        private const val MAX_RECORDING_MS = 30 * 60 * 1000 // 30 minutos
    }
}
