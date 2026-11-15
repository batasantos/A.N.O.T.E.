package br.unesp.anote

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File

/**
 * Servico em primeiro plano com as acoes vindas do painel (e do atalho rapido):
 *  - ACTION_SOS:         envia SMS com o link do mapa a TODOS os contatos e continua enviando a
 *                        localizacao atualizada a cada [REPEAT_MS] ate [MAX_REPEATS] vezes ou ate a pessoa parar;
 *  - ACTION_SOS_ONE:     um unico envio, so para um contato (botao "Disparar"), sem repeticao;
 *  - ACTION_STOP_SOS:    para o envio repetido;
 *  - ACTION_RECORD / ACTION_STOP_RECORD: grava o audio do ambiente em armazenamento privado do app
 *                        e, se houver pasta de backup, copia o audio quando a gravacao termina.
 * Cada SMS e acompanhado: o resultado (enviado ou falhou) de cada contato vira um [SosReport] e,
 * se algum falhar, aparece uma notificacao neutra e um aviso no painel.
 * A notificacao e discreta e com aparencia neutra ("Notas - Sincronizando...").
 */
class SosService : Service() {

    /** Um envio (a rodada) para os contatos, com o resultado de cada SMS. */
    private class Round(val id: Int, val account: String, val update: Boolean, val contacts: List<TrustedContact>) {
        val parts = mutableMapOf<String, Int>()
        val ok = mutableMapOf<String, Int>()
        val failed = mutableMapOf<String, Int>()
        var finished = false

        fun allReported() = parts.keys.all { (ok[it] ?: 0) + (failed[it] ?: 0) >= (parts[it] ?: 0) }
        fun succeeded(phone: String) = (failed[phone] ?: 0) == 0 && (ok[phone] ?: 0) >= (parts[phone] ?: 0)
    }

    private val main = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    /** Rodadas que esperam a localizacao (ainda nao mandaram os SMS). */
    private var pendingLocation = 0
    private var backups = 0
    private val rounds = mutableMapOf<Int, Round>()
    private var nextRound = 1
    private var nextRequest = 1000
    private var repeatsDone = 0
    private var receiverRegistered = false
    private var destroyed = false

    /** Conta que acionou o servico: contatos, nome e gravacoes sao sempre os dela. */
    private var accountId: String? = null

    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val round = rounds[intent.getIntExtra(EXTRA_ROUND, -1)] ?: return
            val phone = intent.getStringExtra(EXTRA_PHONE) ?: return
            val map = if (resultCode == Activity.RESULT_OK) round.ok else round.failed
            map[phone] = (map[phone] ?: 0) + 1
            if (round.allReported()) finishRound(round)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, sentReceiver, IntentFilter(ACTION_SMS_SENT), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_RECORD || action == ACTION_SOS || action == ACTION_SOS_ONE) {
            accountId = Session.activeId(this)
            if (accountId == null) { // sem sessao aberta nao ha contatos nem pasta de gravacao
                enterForeground()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        when (action) {
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
                if (!hasPermission(Manifest.permission.SEND_SMS) || isSosActive) {
                    finishIfIdle()
                } else {
                    isSosActive = true
                    repeatsDone = 0
                    sendAlert(null, update = false)
                    scheduleRepeat()
                }
            }
            ACTION_SOS_ONE -> {
                enterForeground()
                sendAlert(intent.getStringExtra(EXTRA_PHONE), update = false)
            }
            ACTION_SOS_REPEAT -> {
                if (!isSosActive || accountId == null) { // o processo foi reiniciado ou o SOS ja parou
                    isSosActive = false
                    enterForeground() // o alarme exige entrar em primeiro plano antes de encerrar
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    enterForeground()
                    repeatsDone++
                    if (repeatsDone > MAX_REPEATS) {
                        stopSos()
                    } else {
                        sendAlert(null, update = true)
                        scheduleRepeat()
                    }
                }
            }
            ACTION_STOP_SOS -> stopSos()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        stopRecording()
        cancelRepeat()
        isSosActive = false
        if (receiverRegistered) runCatching { unregisterReceiver(sentReceiver) }
        super.onDestroy()
    }

    private fun finishIfIdle() {
        if (destroyed) return
        if (pendingLocation <= 0 && recorder == null && rounds.isEmpty() && backups <= 0 && !isSosActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopSos() {
        isSosActive = false
        cancelRepeat()
        finishIfIdle()
    }

    // ---------- Envio repetido ----------

    private fun repeatIntent(): PendingIntent = PendingIntent.getForegroundService(
        this, REQ_REPEAT,
        Intent(this, SosService::class.java).setAction(ACTION_SOS_REPEAT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** O alarme acorda o servico mesmo com a tela apagada (modo de economia incluso, com pequeno atraso). */
    private fun scheduleRepeat() {
        val alarms = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + REPEAT_MS, repeatIntent()
        )
    }

    private fun cancelRepeat() {
        val alarms = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(repeatIntent())
    }

    // ---------- Notificacoes / foreground ----------

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun enterForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager().createNotificationChannel(
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

    /** Aviso de falha: texto neutro, sem a palavra SOS, e invisivel na tela bloqueada. */
    private fun notifyFailure() {
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager().createNotificationChannel(
                NotificationChannel(CHANNEL_FAIL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_FAIL)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_fail_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setSilent(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        runCatching { notificationManager().notify(NOTIF_FAIL_ID, notification) }
    }

    // ---------- Localizacao + SMS ----------

    @SuppressLint("MissingPermission")
    private fun sendAlert(onlyPhone: String?, update: Boolean) {
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            finishIfIdle()
            return
        }
        pendingLocation++

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            sendSms(null, onlyPhone, update)
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    sendSms(loc, onlyPhone, update)
                } else {
                    // Sem fix de GPS agora: usa a ultima posicao conhecida.
                    client.lastLocation
                        .addOnSuccessListener { last -> sendSms(last, onlyPhone, update) }
                        .addOnFailureListener { sendSms(null, onlyPhone, update) }
                }
            }
            .addOnFailureListener { sendSms(null, onlyPhone, update) }
    }

    private fun sendSms(location: Location?, onlyPhone: String?, update: Boolean) {
        pendingLocation--
        val account = accountId
        if (account == null) {
            finishIfIdle()
            return
        }
        val userPrefs = SecurePrefs(this, account)
        val userName = userPrefs.userName
        val text = buildString {
            if (update) append(getString(R.string.sos_update_prefix)).append(' ')
            val custom = userPrefs.customMessage
            append(
                when {
                    custom.isNotBlank() -> custom // texto escolhido pela pessoa (o link do mapa vem depois)
                    userName.isNotBlank() -> getString(R.string.sos_message_named, userName)
                    else -> getString(R.string.sos_message)
                }
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
        val all = userPrefs.contacts()
        val targets = if (onlyPhone != null) {
            listOf(all.firstOrNull { it.phone == onlyPhone } ?: TrustedContact(onlyPhone, onlyPhone))
        } else all
        if (targets.isEmpty()) {
            finishIfIdle()
            return
        }

        val round = Round(nextRound++, account, update, targets)
        rounds[round.id] = round
        targets.forEach { contact ->
            val phone = contact.phone
            try {
                val parts = smsManager.divideMessage(text)
                round.parts[phone] = parts.size
                // Um PendingIntent por parte: o sistema avisa se cada parte saiu ou falhou.
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                repeat(parts.size) {
                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            this, nextRequest++,
                            Intent(ACTION_SMS_SENT).setPackage(packageName)
                                .putExtra(EXTRA_ROUND, round.id).putExtra(EXTRA_PHONE, phone),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                        )
                    )
                }
                smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            } catch (e: Exception) {
                round.parts[phone] = round.parts[phone] ?: 1
                round.failed[phone] = round.parts[phone] ?: 1 // numero invalido, sem permissao...
            }
        }
        if (round.allReported()) finishRound(round) else main.postDelayed({ finishRound(round) }, ROUND_TIMEOUT_MS)
    }

    /** Fecha uma rodada: quem nao confirmou a tempo conta como falha. Guarda o resultado e avisa. */
    private fun finishRound(round: Round) {
        if (round.finished) return
        round.finished = true
        rounds.remove(round.id)
        val sent = round.contacts.filter { round.succeeded(it.phone) }
        val failed = round.contacts.filter { !round.succeeded(it.phone) }
        val report = SosReport(System.currentTimeMillis(), sent, failed, round.update)
        runCatching { SecurePrefs(this, round.account).saveReport(report) }
        if (failed.isNotEmpty()) notifyFailure()
        onReport?.invoke(report)
        finishIfIdle()
    }

    // ---------- Gravacao de audio ----------

    private fun startRecording() {
        if (recorder != null || !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            isRecording = recorder != null
            return
        }
        val dir = Recordings.dir(this, accountId ?: return)
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
            // No limite de 30 min a gravacao termina sozinha: fecha o arquivo e faz o backup.
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecording()
                    finishIfIdle()
                }
            }
            r.prepare()
            r.start()
            recorder = r
            recordingFile = file
        }.onFailure {
            runCatching { r.release() }
        }
        isRecording = recorder != null
    }

    private fun stopRecording() {
        val file = recordingFile
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
        recordingFile = null
        isRecording = false
        if (file != null) backupAsync(file)
    }

    /** Copia o audio recem-gravado para a pasta de backup (se a pessoa configurou uma). */
    private fun backupAsync(file: File) {
        val account = accountId ?: return
        if (!file.exists() || file.length() == 0L || !Backup.enabled(this, account)) return
        backups++
        Backup.executor.execute {
            Backup.copy(applicationContext, account, file)
            main.post {
                backups--
                finishIfIdle()
            }
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_SOS = "br.unesp.anote.SOS"
        const val ACTION_SOS_ONE = "br.unesp.anote.SOS_ONE"
        const val ACTION_SOS_REPEAT = "br.unesp.anote.SOS_REPEAT"
        const val ACTION_STOP_SOS = "br.unesp.anote.STOP_SOS"
        const val ACTION_RECORD = "br.unesp.anote.RECORD"
        const val ACTION_STOP_RECORD = "br.unesp.anote.STOP_RECORD"
        const val EXTRA_PHONE = "phone"
        private const val ACTION_SMS_SENT = "br.unesp.anote.SMS_SENT"
        private const val EXTRA_ROUND = "round"

        /** Estado lido pelo painel para mostrar "Iniciar" ou "Parar" gravacao. */
        @Volatile
        var isRecording = false

        /** true enquanto o SOS esta repetindo o envio da localizacao. */
        @Volatile
        var isSosActive = false

        /** Chamado (na thread principal) quando um envio termina; o painel aberto usa para avisar na hora. */
        var onReport: ((SosReport) -> Unit)? = null

        /** Intervalo entre os envios de localizacao e o limite de repeticoes (24 x 5 min = 2 h). */
        const val REPEAT_MINUTES = 5
        private const val REPEAT_MS = REPEAT_MINUTES * 60 * 1000L
        private const val MAX_REPEATS = 24
        private const val ROUND_TIMEOUT_MS = 60_000L
        private const val REQ_REPEAT = 71

        private const val CHANNEL_ID = "sync"
        private const val CHANNEL_FAIL = "sync_fail"
        private const val NOTIF_ID = 7
        private const val NOTIF_FAIL_ID = 8
        private const val MAX_RECORDING_MS = 30 * 60 * 1000 // 30 minutos
    }
}
