package br.unesp.anote

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Registro das contas deste aparelho.
 *
 * Seguranca das senhas:
 *  - a senha nunca e guardada; so um hash PBKDF2-HMAC-SHA256 com sal aleatorio proprio de cada conta
 *    e o numero de iteracoes usado (permite aumentar o custo no futuro sem invalidar contas antigas);
 *  - o registro inteiro fica em EncryptedSharedPreferences (AES-256, chave no Android Keystore),
 *    entao mesmo o hash nao fica legivel em arquivo;
 *  - a comparacao e em tempo constante e, se o e-mail nao existe, o mesmo calculo e feito
 *    (o tempo de resposta nao revela quais e-mails estao cadastrados);
 *  - depois de [FREE_ATTEMPTS] erros seguidos o login e bloqueado por um tempo que dobra a cada erro
 *    (o contador fica salvo: fechar o app nao zera).
 *
 * Os dados de cada conta (notas, contatos, gravacoes) ficam separados; ver [SecurePrefs],
 * [NotesDbHelper] e [Recordings].
 */
object Accounts {

    const val MIN_PASSWORD = 8

    private const val FILE = "anote_accounts"
    private const val KEY_LIST = "accounts"
    private const val KEY_FAILS = "failed_attempts"
    private const val KEY_LOCK_UNTIL = "lock_until"

    private const val ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val FREE_ATTEMPTS = 5
    private const val BASE_LOCK_MS = 30_000L
    private const val MAX_LOCK_MS = 15 * 60_000L

    private val DUMMY_SALT = ByteArray(SALT_BYTES)

    /** O que as telas enxergam de uma conta (nunca o hash). */
    data class Account(val id: String, val name: String, val email: String)

    sealed class AuthResult {
        data class Success(val accountId: String) : AuthResult()
        object Wrong : AuthResult()
        data class Locked(val seconds: Long) : AuthResult()
    }

    private data class Record(
        val id: String,
        val name: String,
        val email: String,
        val salt: String,
        val hash: String,
        val iterations: Int
    )

    @Volatile
    private var cached: SharedPreferences? = null

    @Synchronized
    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        val app = context.applicationContext
        return EncryptedSharedPreferences.create(
            app,
            FILE,
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { cached = it }
    }

    // ---------- Consultas ----------

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun hasAccounts(context: Context): Boolean = synchronized(this) { records(context).isNotEmpty() }

    fun findIdByEmail(context: Context, email: String): String? = synchronized(this) {
        val mail = normalizeEmail(email)
        records(context).firstOrNull { it.email == mail }?.id
    }

    fun get(context: Context, id: String): Account? = synchronized(this) {
        records(context).firstOrNull { it.id == id }?.let { Account(it.id, it.name, it.email) }
    }

    // ---------- Cadastro ----------

    /** Cria a conta e devolve o id; devolve null se ja existe uma conta com esse e-mail. */
    fun create(context: Context, name: String, email: String, password: String): String? {
        val mail = normalizeEmail(email)
        if (synchronized(this) { records(context).any { it.email == mail } }) return null
        val salt = newSalt()
        val hash = hash(password, salt, ITERATIONS) // o calculo lento fica fora do bloqueio
        return synchronized(this) {
            val list = records(context)
            if (list.any { it.email == mail }) {
                null
            } else {
                val id = UUID.randomUUID().toString()
                list.add(Record(id, name.trim(), mail, b64(salt), b64(hash), ITERATIONS))
                save(context, list)
                id
            }
        }
    }

    /** Atualiza nome e e-mail; devolve false se o e-mail ja pertence a outra conta. */
    fun updateProfile(context: Context, id: String, name: String, email: String): Boolean =
        synchronized(this) {
            val mail = normalizeEmail(email)
            val list = records(context)
            val index = list.indexOfFirst { it.id == id }
            if (index < 0 || list.any { it.id != id && it.email == mail }) {
                false
            } else {
                list[index] = list[index].copy(name = name.trim(), email = mail)
                save(context, list)
                true
            }
        }

    fun setPassword(context: Context, id: String, password: String) {
        val salt = newSalt()
        val hash = hash(password, salt, ITERATIONS)
        synchronized(this) {
            val list = records(context)
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(salt = b64(salt), hash = b64(hash), iterations = ITERATIONS)
                save(context, list)
            }
        }
    }

    /**
     * Confere a senha de uma conta ja conhecida (ex.: antes de excluir ou trocar a senha).
     * Conta como tentativa: erros seguidos bloqueiam, igual ao login.
     */
    fun verifyPassword(context: Context, id: String, password: String): AuthResult {
        val record = synchronized(this) { records(context).firstOrNull { it.id == id } }
        return attempt(context, record, password)
    }

    /** Troca a senha se a atual estiver certa. Devolve Success, Wrong (atual errada) ou Locked. */
    fun changePassword(context: Context, id: String, current: String, new: String): AuthResult {
        val result = verifyPassword(context, id, current)
        if (result is AuthResult.Success) setPassword(context, id, new)
        return result
    }

    // ---------- Login ----------

    fun authenticate(context: Context, email: String, password: String): AuthResult {
        val mail = normalizeEmail(email)
        val record = synchronized(this) { records(context).firstOrNull { it.email == mail } }
        return attempt(context, record, password)
    }

    /**
     * Uma tentativa de senha: respeita o bloqueio, gasta o mesmo tempo exista a conta ou nao
     * ([record] nulo) e atualiza o contador de erros.
     */
    private fun attempt(context: Context, record: Record?, password: String): AuthResult {
        val remaining = lockRemainingSeconds(context)
        if (remaining > 0) return AuthResult.Locked(remaining)

        val ok = if (record != null) {
            matches(record, password)
        } else {
            hash(password, DUMMY_SALT, ITERATIONS) // mesmo custo, exista a conta ou nao
            false
        }

        return synchronized(this) {
            val p = prefs(context)
            if (ok && record != null) {
                p.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
                AuthResult.Success(record.id)
            } else {
                val fails = p.getInt(KEY_FAILS, 0) + 1
                val lockMs = if (fails >= FREE_ATTEMPTS) {
                    minOf(BASE_LOCK_MS shl minOf(fails - FREE_ATTEMPTS, 5), MAX_LOCK_MS)
                } else 0L
                p.edit()
                    .putInt(KEY_FAILS, fails)
                    .putLong(KEY_LOCK_UNTIL, if (lockMs > 0) System.currentTimeMillis() + lockMs else 0L)
                    .apply()
                if (lockMs > 0) AuthResult.Locked(lockMs / 1000) else AuthResult.Wrong
            }
        }
    }

    /** Segundos que faltam para o login voltar a aceitar tentativas (0 = liberado). */
    fun lockRemainingSeconds(context: Context): Long = synchronized(this) {
        val left = prefs(context).getLong(KEY_LOCK_UNTIL, 0L) - System.currentTimeMillis()
        if (left > 0) (left + 999) / 1000 else 0L
    }

    // ---------- Exclusao ----------

    /** Remove a conta e TODOS os dados dela (preferencias, notas e gravacoes). Nao tem volta. */
    fun delete(context: Context, id: String) {
        val app = context.applicationContext
        // Encerra o servico de SOS/gravacao, que pode estar usando os arquivos desta conta.
        app.stopService(Intent(app, SosService::class.java))
        synchronized(this) {
            save(context, records(context).filter { it.id != id }.toMutableList())
        }
        app.deleteSharedPreferences(SecurePrefs.fileName(id))
        app.deleteDatabase(NotesDbHelper.dbName(id))
        Recordings.dir(app, id).deleteRecursively()
    }

    // ---------- Internos ----------

    private fun matches(record: Record, password: String): Boolean {
        val salt = Base64.decode(record.salt, Base64.NO_WRAP)
        val expected = Base64.decode(record.hash, Base64.NO_WRAP)
        return MessageDigest.isEqual(expected, hash(password, salt, record.iterations))
    }

    private fun hash(password: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, 256)).encoded

    private fun newSalt() = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun records(context: Context): MutableList<Record> {
        val arr = JSONArray(prefs(context).getString(KEY_LIST, "[]") ?: "[]")
        return (0 until arr.length()).mapTo(mutableListOf()) {
            val o = arr.getJSONObject(it)
            Record(
                id = o.getString("id"),
                name = o.getString("name"),
                email = o.getString("email"),
                salt = o.getString("salt"),
                hash = o.getString("hash"),
                iterations = o.getInt("iter")
            )
        }
    }

    private fun save(context: Context, list: MutableList<Record>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("email", it.email)
                    .put("salt", it.salt).put("hash", it.hash).put("iter", it.iterations)
            )
        }
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply()
    }
}

/**
 * Roda um trabalho pesado (o hash da senha leva alguns centenas de ms de proposito)
 * fora da thread da tela e entrega o resultado de volta nela, se a tela ainda existir.
 */
fun <T> AppCompatActivity.inBackground(work: () -> T, done: (T) -> Unit) {
    Thread {
        val result = work()
        runOnUiThread { if (!isFinishing && !isDestroyed) done(result) }
    }.start()
}
