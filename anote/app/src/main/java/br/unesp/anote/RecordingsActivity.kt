package br.unesp.anote

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gravacoes de audio (menu do painel de emergencia). Os arquivos ficam privados no app;
 * daqui a pessoa pode ouvir, exportar para uma pasta que escolher, compartilhar ou apagar.
 * Herda a protecao do painel (sem captura de tela, fecha ao apagar a tela ou sair do app).
 */
class RecordingsActivity : GuardedActivity() {

    private lateinit var list: LinearLayout
    private lateinit var empty: TextView

    private var player: MediaPlayer? = null
    private var playingFile: File? = null
    private var exportFile: File? = null
    private lateinit var backupStatus: TextView
    private lateinit var prefs: SecurePrefs
    private lateinit var account: String

    /** Seletor de pasta do Android: a pessoa escolhe onde o backup das gravacoes sera feito. */
    private val folderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.backupFolder = uri.toString()
                renderBackup()
                runBackupNow()
            }
        }

    /** Abre o seletor do Android para a pessoa escolher onde salvar a copia. */
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { uri ->
            val source = exportFile
            exportFile = null
            if (uri != null && source != null) copyTo(source, uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.isActive(this)) { // sessao encerrada: nao ha conta para mostrar
            finishAndRemoveTask()
            return
        }
        setContentView(R.layout.activity_recordings)

        prefs = SecurePrefs(this)
        account = prefs.accountId
        backupStatus = findViewById(R.id.tv_backup_status)
        findViewById<View>(R.id.card_backup).setOnClickListener { showBackupOptions() }
        list = findViewById(R.id.recordings_list)
        empty = findViewById(R.id.tv_empty)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onStop() {
        stopPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    // ---------- Backup ----------

    private fun renderBackup() {
        if (prefs.backupFolder == null) {
            backupStatus.setText(R.string.backup_off)
        } else {
            val pending = Backup.pending(this, account).size
            backupStatus.text =
                if (pending == 0) getString(R.string.backup_on_done)
                else resources.getQuantityString(R.plurals.backup_on_pending, pending, pending)
        }
    }

    private fun showBackupOptions() {
        val on = prefs.backupFolder != null
        val items = if (on) {
            arrayOf(getString(R.string.backup_now), getString(R.string.backup_choose), getString(R.string.backup_disable))
        } else {
            arrayOf(getString(R.string.backup_choose))
        }
        Ui.show(
            Ui.dialog(this, dark = true)
                .setTitle(R.string.backup_title)
                .setItems(items) { _, which ->
                    when (items[which]) {
                        getString(R.string.backup_now) -> runBackupNow()
                        getString(R.string.backup_choose) -> {
                            keepOnStop = true // o seletor de pastas do Android nao deve fechar esta tela
                            folderLauncher.launch(null)
                        }
                        else -> {
                            prefs.backupFolder = null
                            renderBackup()
                        }
                    }
                },
            dark = true
        )
    }

    private fun runBackupNow() {
        Toast.makeText(this, R.string.backup_running, Toast.LENGTH_SHORT).show()
        Backup.executor.execute {
            val (ok, failed) = Backup.copyAllPending(applicationContext, account)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                Toast.makeText(
                    this,
                    if (failed > 0) getString(R.string.backup_failed, failed)
                    else getString(R.string.backup_done, ok),
                    Toast.LENGTH_LONG
                ).show()
                renderBackup()
            }
        }
    }

    // ---------- Lista ----------

    private fun render() {
        renderBackup()
        list.removeAllViews()
        val files = Recordings.list(this)
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE

        files.forEachIndexed { index, file ->
            // A gravacao mais recente ainda esta sendo escrita se o servico estiver gravando.
            val recordingNow = SosService.isRecording && index == 0
            val row = LayoutInflater.from(this).inflate(R.layout.item_recording, list, false)
            row.findViewById<TextView>(R.id.rec_title).text = formatDate(file)
            row.findViewById<TextView>(R.id.rec_subtitle).text =
                if (recordingNow) getString(R.string.rec_recording_now)
                else getString(R.string.rec_format, formatDuration(file), Formatter.formatShortFileSize(this, file.length()))
            row.findViewById<ImageView>(R.id.rec_icon).setImageResource(
                if (playingFile == file) R.drawable.ic_stop else R.drawable.ic_play
            )
            row.setOnClickListener {
                if (recordingNow) {
                    Toast.makeText(this, R.string.rec_stop_first, Toast.LENGTH_SHORT).show()
                } else {
                    showActions(file)
                }
            }
            list.addView(row)
        }
    }

    private fun formatDate(file: File): String {
        // O nome do arquivo e "audio_<milissegundos>.m4a"; se nao der para ler, usa a data do arquivo.
        val millis = file.nameWithoutExtension.removePrefix("audio_").toLongOrNull() ?: file.lastModified()
        return SimpleDateFormat(getString(R.string.rec_date_format), Locale("pt", "BR")).format(Date(millis))
    }

    private fun formatDuration(file: File): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (ms == null) "—" else String.format(Locale.US, "%02d:%02d", ms / 60000, (ms / 1000) % 60)
        } catch (e: Exception) {
            "—" // arquivo incompleto (a gravacao foi interrompida antes de terminar)
        } finally {
            runCatching { retriever.release() }
        }
    }

    // ---------- Acoes ----------

    private fun showActions(file: File) {
        val items = arrayOf(
            getString(if (playingFile == file) R.string.rec_stop else R.string.rec_play),
            getString(R.string.rec_export),
            getString(R.string.rec_share),
            getString(R.string.rec_delete)
        )
        Ui.show(
            Ui.dialog(this, dark = true)
                .setTitle(formatDate(file))
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> togglePlay(file)
                        1 -> export(file)
                        2 -> share(file)
                        3 -> confirmDelete(file)
                    }
                },
            dark = true
        )
    }

    private fun togglePlay(file: File) {
        if (playingFile == file) {
            stopPlayback()
            render()
            return
        }
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                    render()
                }
                prepare()
                start()
            }
            playingFile = file
        } catch (e: Exception) {
            stopPlayback()
            Toast.makeText(this, R.string.rec_play_fail, Toast.LENGTH_SHORT).show()
        }
        render()
    }

    private fun stopPlayback() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        playingFile = null
    }

    private fun export(file: File) {
        exportFile = file
        keepOnStop = true // o seletor de pastas do Android nao deve fechar esta tela
        val name = "ANOTE_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(file.lastModified())) + ".m4a"
        exportLauncher.launch(name)
    }

    private fun copyTo(source: File, destination: Uri) {
        Thread {
            val ok = try {
                val out = contentResolver.openOutputStream(destination) ?: error("sem destino")
                out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) R.string.rec_export_done else R.string.rec_export_fail,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        keepOnStop = true // a lista de apps para compartilhar nao deve fechar esta tela
        startActivity(Intent.createChooser(send, getString(R.string.rec_share_title)))
    }

    private fun confirmDelete(file: File) {
        Ui.show(
            Ui.dialog(this, dark = true)
                .setMessage(R.string.rec_delete_confirm)
                .setPositiveButton(R.string.rec_delete) { _, _ ->
                    if (playingFile == file) stopPlayback()
                    file.delete()
                    render()
                }
                .setNegativeButton(R.string.cancel, null),
            dark = true
        )
    }
}
