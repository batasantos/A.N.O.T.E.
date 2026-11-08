package br.unesp.anote

import android.content.Context
import java.io.File

/**
 * Onde ficam os audios gravados: pasta privada do app (outros apps nao enxergam),
 * uma subpasta para cada conta.
 */
object Recordings {

    private const val DIR = "evidence"

    /** Pasta das gravacoes de uma conta (a da conta ativa, se [accountId] nao for informado). */
    fun dir(context: Context, accountId: String = Session.requireActive(context)): File =
        File(File(context.filesDir, DIR), accountId).apply { mkdirs() }

    /** Gravacoes da conta ativa, da mais recente para a mais antiga. Arquivos vazios (gravacao que nao comecou) ficam de fora. */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.extension == "m4a" && f.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
