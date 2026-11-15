package br.unesp.anote

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Backup das gravacoes: copia cada audio para uma pasta que a PESSOA escolhe (seletor do Android).
 * Se ela escolher uma pasta sincronizada com a nuvem (Google Drive, OneDrive...), o audio sai do
 * aparelho e continua existindo mesmo que o celular seja tomado. O app nao tem servidor proprio.
 * A copia e feita automaticamente quando uma gravacao termina e tambem sob demanda.
 */
object Backup {

    /** Uma fila unica: copias nao se atropelam. */
    val executor = Executors.newSingleThreadExecutor()

    fun enabled(context: Context, accountId: String): Boolean =
        SecurePrefs(context, accountId).backupFolder != null

    /** Gravacoes da conta que ainda nao foram copiadas. */
    fun pending(context: Context, accountId: String): List<File> {
        val done = SecurePrefs(context, accountId).backedUpNames()
        return Recordings.dir(context, accountId)
            .listFiles { f -> f.isFile && f.extension == "m4a" && f.length() > 0 }
            ?.filter { it.name !in done }
            ?: emptyList()
    }

    /** Copia um audio para a pasta de backup. Devolve false se nao houver pasta ou se a copia falhar. */
    fun copy(context: Context, accountId: String, file: File): Boolean {
        val prefs = SecurePrefs(context, accountId)
        val folder = prefs.backupFolder ?: return false
        if (file.name in prefs.backedUpNames()) return true
        return try {
            val tree = Uri.parse(folder)
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val millis = file.nameWithoutExtension.removePrefix("audio_").toLongOrNull() ?: file.lastModified()
            // Nome neutro: quem olhar a pasta nao deve ler "SOS" nem o nome do app.
            val name = "Nota de voz " + SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date(millis)) + ".m4a"
            val destination = DocumentsContract.createDocument(
                context.contentResolver, parent, "audio/mp4", name
            ) ?: return false
            val out = context.contentResolver.openOutputStream(destination) ?: return false
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            prefs.markBackedUp(file.name)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Copia todas as pendentes. Devolve quantas deram certo e quantas falharam. */
    fun copyAllPending(context: Context, accountId: String): Pair<Int, Int> {
        var ok = 0
        var failed = 0
        pending(context, accountId).forEach { if (copy(context, accountId, it)) ok++ else failed++ }
        return ok to failed
    }
}
