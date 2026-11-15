package br.unesp.anote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException

enum class CloudResult { OK, EXISTS, NETWORK, ERROR }

/**
 * Servico de recuperacao de senha por e-mail (Firebase Authentication).
 *
 * O servidor guarda SO o e-mail e a senha (o Firebase guarda um hash). Notas, contatos, gravacoes e o
 * titulo secreto NUNCA saem do aparelho. O login normal continua sendo local (funciona sem internet);
 * a nuvem so entra em dois momentos: enviar o link de redefinicao e, depois que a pessoa define a senha
 * nova pelo link, aceitar essa senha no login e atualizar o hash local.
 *
 * Tudo aqui fica desligado enquanto os tres valores de res/values/firebase.xml estiverem vazios.
 * Os resultados chegam na thread principal.
 */
object Cloud {

    fun isConfigured(context: Context): Boolean =
        context.getString(R.string.firebase_api_key).isNotBlank() &&
            context.getString(R.string.firebase_app_id).isNotBlank() &&
            context.getString(R.string.firebase_project_id).isNotBlank()

    private fun auth(context: Context): FirebaseAuth? {
        if (!isConfigured(context)) return null
        val app = context.applicationContext
        return try {
            val firebase = FirebaseApp.getApps(app).firstOrNull() ?: FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setApiKey(app.getString(R.string.firebase_api_key))
                    .setApplicationId(app.getString(R.string.firebase_app_id))
                    .setProjectId(app.getString(R.string.firebase_project_id))
                    .build()
            )
            FirebaseAuth.getInstance(firebase).also { it.setLanguageCode("pt-BR") }
        } catch (e: Exception) {
            null
        }
    }

    private fun classify(e: Exception?): CloudResult = when (e) {
        is FirebaseNetworkException -> CloudResult.NETWORK
        is FirebaseAuthUserCollisionException -> CloudResult.EXISTS
        else -> CloudResult.ERROR
    }

    /** Cria a conta no servidor (mesmo e-mail e senha do app). EXISTS = esse e-mail ja tem conta la. */
    fun register(context: Context, email: String, password: String, done: (CloudResult) -> Unit) {
        val auth = auth(context) ?: return done(CloudResult.ERROR)
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                auth.signOut()
                done(CloudResult.OK)
            } else {
                done(classify(task.exception))
            }
        }
    }

    /**
     * Cadastra a conta no servidor se ainda nao foi feito (ex.: o cadastro foi sem internet).
     * Silencioso: se falhar, tenta de novo no proximo login.
     */
    fun linkIfNeeded(context: Context, accountId: String, email: String, password: String) {
        val app = context.applicationContext
        if (!isConfigured(app)) return
        val prefs = runCatching { SecurePrefs(app, accountId) }.getOrNull() ?: return
        if (prefs.cloudLinked) return
        register(app, email, password) { result ->
            // EXISTS: o e-mail ja esta la (reinstalacao, por exemplo); o link de redefinicao funciona do mesmo jeito.
            if (result == CloudResult.OK || result == CloudResult.EXISTS) {
                runCatching { prefs.cloudLinked = true }
            }
        }
    }

    /**
     * Pede ao servidor que envie o link de redefinicao. Se nao existe conta com esse e-mail o resultado
     * e o mesmo (OK), para nao revelar quem esta cadastrado.
     */
    fun sendReset(context: Context, email: String, done: (CloudResult) -> Unit) {
        val auth = auth(context) ?: return done(CloudResult.ERROR)
        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            done(
                when {
                    task.isSuccessful -> CloudResult.OK
                    task.exception is FirebaseAuthInvalidUserException -> CloudResult.OK
                    else -> classify(task.exception)
                }
            )
        }
    }

    /** Confere e-mail e senha no servidor (usado depois de a senha ser redefinida pelo link). */
    fun signIn(context: Context, email: String, password: String, done: (Boolean) -> Unit) {
        val auth = auth(context) ?: return done(false)
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) auth.signOut()
            done(task.isSuccessful)
        }
    }

    /** Melhor esforco: mantem a senha do servidor igual a do app depois de uma troca de senha. */
    fun changePassword(context: Context, email: String, old: String, new: String) {
        val auth = auth(context) ?: return
        auth.signInWithEmailAndPassword(email, old).addOnCompleteListener { task ->
            val user = if (task.isSuccessful) task.result?.user else null // ler o resultado de uma tarefa que falhou lanca excecao
            if (user != null) user.updatePassword(new).addOnCompleteListener { auth.signOut() }
        }
    }

    /** Melhor esforco: apaga tambem a conta do servidor quando a pessoa exclui a conta no app. */
    fun deleteAccount(context: Context, email: String, password: String) {
        val auth = auth(context) ?: return
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            val user = if (task.isSuccessful) task.result?.user else null
            if (user != null) user.delete().addOnCompleteListener { auth.signOut() }
        }
    }
}
