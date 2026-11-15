package br.unesp.anote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class TrustedContact(val name: String, val phone: String)

/** Resultado de um envio de SOS: quem recebeu e quem nao (o SMS foi recusado ou falhou). */
data class SosReport(
    val time: Long,
    val sent: List<TrustedContact>,
    val failed: List<TrustedContact>,
    val update: Boolean
)

/**
 * Preferencias criptografadas de UMA conta: titulo-gatilho, contatos de confianca, ordem das notas
 * e se o cadastro foi concluido. Cada conta tem o seu proprio arquivo, entao uma conta nunca enxerga
 * os dados de outra. A senha NAO fica aqui (ver [Accounts]).
 *
 * `SecurePrefs(context)` usa a conta com sessao aberta; `SecurePrefs(context, id)` uma conta especifica
 * (cadastro em andamento, servico de SOS).
 */
class SecurePrefs(context: Context, val accountId: String) {

    constructor(context: Context) : this(context, Session.requireActive(context))

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        fileName(accountId),
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Nome e e-mail ficam no registro de contas; aqui so a leitura. */
    val userName: String
        get() = Accounts.get(appContext, accountId)?.name ?: ""

    val userEmail: String
        get() = Accounts.get(appContext, accountId)?.email ?: ""

    var triggerTitle: String
        get() = prefs.getString(KEY_TRIGGER, DEFAULT_TRIGGER) ?: DEFAULT_TRIGGER
        set(value) = prefs.edit().putString(KEY_TRIGGER, value.trim()).apply()

    /** true depois que os 3 passos do cadastro foram concluidos. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /** Atalho de disparo rapido (volume para baixo 3 vezes nas telas de notas). Ligado por padrao. */
    var quickShortcut: Boolean
        get() = prefs.getBoolean(KEY_QUICK, true)
        set(value) = prefs.edit().putBoolean(KEY_QUICK, value).apply()

    /** true depois que a conta foi cadastrada no servidor de recuperacao de senha (ver [Cloud]). */
    var cloudLinked: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_LINKED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_LINKED, value).apply()

    /** Bloqueio ao reabrir o app (pede o desbloqueio do aparelho). Desligado por padrao. */
    var appLock: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK, value).apply()

    /** Texto do SMS de SOS escolhido pela pessoa; vazio = texto padrao. */
    var customMessage: String
        get() = prefs.getString(KEY_CUSTOM_MESSAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_MESSAGE, value.trim()).apply()

    /** Pasta escolhida pela pessoa para o backup das gravacoes (endereco SAF), ou null se desligado. */
    var backupFolder: String?
        get() = prefs.getString(KEY_BACKUP_FOLDER, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_BACKUP_FOLDER) else putString(KEY_BACKUP_FOLDER, value) }.apply()

    /** Nomes das gravacoes que ja foram copiadas para a pasta de backup. */
    fun backedUpNames(): Set<String> {
        val arr = JSONArray(prefs.getString(KEY_BACKED_UP, "[]") ?: "[]")
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun markBackedUp(name: String) {
        val arr = JSONArray()
        (backedUpNames() + name).forEach { arr.put(it) }
        prefs.edit().putString(KEY_BACKED_UP, arr.toString()).apply()
    }

    /** Ultimo envio de SOS. [reportUnseen] fica true enquanto houver falha que o painel ainda nao mostrou. */
    fun saveReport(report: SosReport) {
        fun list(items: List<TrustedContact>) = JSONArray().also { a ->
            items.forEach { a.put(JSONObject().put("name", it.name).put("phone", it.phone)) }
        }
        val json = JSONObject()
            .put("time", report.time)
            .put("update", report.update)
            .put("sent", list(report.sent))
            .put("failed", list(report.failed))
        prefs.edit()
            .putString(KEY_REPORT, json.toString())
            .putBoolean(KEY_REPORT_UNSEEN, report.failed.isNotEmpty())
            .apply()
    }

    fun lastReport(): SosReport? {
        val raw = prefs.getString(KEY_REPORT, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            fun read(key: String): List<TrustedContact> {
                val a = o.getJSONArray(key)
                return (0 until a.length()).map {
                    val c = a.getJSONObject(it)
                    TrustedContact(c.getString("name"), c.getString("phone"))
                }
            }
            SosReport(o.getLong("time"), read("sent"), read("failed"), o.optBoolean("update"))
        }.getOrNull()
    }

    var reportUnseen: Boolean
        get() = prefs.getBoolean(KEY_REPORT_UNSEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_REPORT_UNSEEN, value).apply()

    fun contacts(): List<TrustedContact> {
        val json = prefs.getString(KEY_CONTACTS, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            TrustedContact(o.getString("name"), o.getString("phone"))
        }
    }

    fun addContact(contact: TrustedContact) {
        saveContacts(contacts() + contact)
    }

    fun updateContact(index: Int, contact: TrustedContact) {
        saveContacts(contacts().mapIndexed { i, c -> if (i == index) contact else c })
    }

    /** Preferencia da lista de notas: false = mais recentes primeiro, true = ordem alfabetica. */
    var sortAlphabetical: Boolean
        get() = prefs.getBoolean(KEY_SORT_ALPHA, false)
        set(value) = prefs.edit().putBoolean(KEY_SORT_ALPHA, value).apply()

    fun removeContact(index: Int) {
        saveContacts(contacts().filterIndexed { i, _ -> i != index })
    }

    private fun saveContacts(list: List<TrustedContact>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("phone", it.phone)) }
        prefs.edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_TRIGGER = "trigger_title"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_SORT_ALPHA = "sort_alpha"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_CLOUD_LINKED = "cloud_linked"
        private const val KEY_APP_LOCK = "app_lock"
        private const val KEY_CUSTOM_MESSAGE = "custom_message"
        private const val KEY_QUICK = "quick_shortcut"
        private const val KEY_BACKUP_FOLDER = "backup_folder"
        private const val KEY_BACKED_UP = "backed_up"
        private const val KEY_REPORT = "last_report"
        private const val KEY_REPORT_UNSEEN = "report_unseen"
        const val DEFAULT_TRIGGER = "..."

        /** Nome do arquivo de preferencias de uma conta. */
        fun fileName(accountId: String) = "anote_acc_$accountId"

        /** Compara títulos sem depender de maiúsculas, espaços nas pontas ou do "…" (um caractere) do teclado. */
        fun normalize(s: String): String = s.trim().replace("…", "...").lowercase()
    }
}
