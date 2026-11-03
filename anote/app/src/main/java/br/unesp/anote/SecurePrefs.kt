package br.unesp.anote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class TrustedContact(val name: String, val phone: String)

/**
 * Guarda de forma criptografada o cadastro, o titulo-gatilho e os contatos de confianca.
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "anote_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var triggerTitle: String
        get() = prefs.getString(KEY_TRIGGER, DEFAULT_TRIGGER) ?: DEFAULT_TRIGGER
        set(value) = prefs.edit().putString(KEY_TRIGGER, value.trim()).apply()

    /** Dados do cadastro basico (primeiro acesso). */
    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value.trim()).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value.trim()).apply()

    /** true depois que os 3 passos do primeiro acesso foram concluidos; dai em diante o app abre direto nas notas. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

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
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val DEFAULT_TRIGGER = "..."

        /** Compara títulos sem depender de maiúsculas, espaços nas pontas ou do "…" (um caractere) do teclado. */
        fun normalize(s: String): String = s.trim().replace("…", "...").lowercase()
    }
}
