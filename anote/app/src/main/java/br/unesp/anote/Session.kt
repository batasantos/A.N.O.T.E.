package br.unesp.anote

import android.content.Context
import android.content.Intent

/**
 * Qual conta esta com a sessao aberta neste aparelho. Guarda apenas o identificador
 * aleatorio da conta (nao revela e-mail, nome nem senha). Sair da conta apaga esse registro.
 */
object Session {

    private const val FILE = "anote_session"
    private const val KEY_ACTIVE = "active_account"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun activeId(context: Context): String? = prefs(context).getString(KEY_ACTIVE, null)

    fun isActive(context: Context): Boolean = activeId(context) != null

    /** Usado pelas classes que sempre trabalham com a conta ativa (preferencias, banco, gravacoes). */
    fun requireActive(context: Context): String =
        activeId(context) ?: throw IllegalStateException("Nenhuma conta com sessao aberta")

    fun login(context: Context, accountId: String) {
        prefs(context).edit().putString(KEY_ACTIVE, accountId).apply()
    }

    /** Fecha a sessao. Uma gravacao em andamento e encerrada (e salva) para nao "vazar" para a proxima conta. */
    fun logout(context: Context) {
        prefs(context).edit().remove(KEY_ACTIVE).apply()
        if (SosService.isRecording) {
            context.startService(
                Intent(context, SosService::class.java).setAction(SosService.ACTION_STOP_RECORD)
            )
        }
    }
}
