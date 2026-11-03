package br.unesp.anote

import android.content.Context
import java.io.File

/** Onde ficam os audios gravados: pasta privada do app (outros apps nao enxergam). */
object Recordings {

    private const val DIR = "evidence"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Gravacoes da mais recente para a mais antiga. Arquivos vazios (gravacao que nao comecou) ficam de fora. */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.extension == "m4a" && f.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
