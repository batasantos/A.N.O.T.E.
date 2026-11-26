package br.unesp.anote

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Porta de entrada do app:
 *  - sessao aberta  -> vai direto para as notas;
 *  - nenhuma conta  -> primeiro cadastro;
 *  - senao          -> login com e-mail e senha, com opcao de criar outra conta.
 *
 * "Esqueci minha senha": envia um link de redefinicao por e-mail. Sem a recuperacao por e-mail,
 * a unica saida e apagar a conta daquele e-mail, com tudo que ela guarda, e criar uma nova.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var error: TextView
    private lateinit var loginButton: TextView
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Privacy.protect(window)

        if (Session.isActive(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        if (!Accounts.hasAccounts(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_login)

        email = findViewById(R.id.login_email)
        password = findViewById(R.id.login_password)
        error = findViewById(R.id.login_error)
        loginButton = findViewById(R.id.btn_login)

        loginButton.setOnClickListener { submit() }
        findViewById<View>(R.id.btn_forgot).setOnClickListener { confirmForgot() }
        findViewById<View>(R.id.btn_signup).setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    private fun submit() {
        if (checking) return
        val mail = email.text.toString()
        val pw = password.text.toString()
        if (mail.isBlank() || pw.isEmpty()) {
            showError(getString(R.string.login_wrong))
            return
        }
        setChecking(true)
        inBackground({ Accounts.authenticate(this, mail, pw) }) { result ->
            setChecking(false)
            when (result) {
                is Accounts.AuthResult.Success -> {
                    Cloud.linkIfNeeded(this, result.accountId, mail.trim().lowercase(), pw)
                    enter(result.accountId)
                }
                is Accounts.AuthResult.Locked -> {
                    password.text.clear()
                    showError(getString(R.string.login_locked, result.seconds))
                }
                Accounts.AuthResult.Wrong -> {
                    // A senha pode ter sido redefinida pelo link do e-mail: o servidor a conhece, o aparelho ainda nao.
                    if (Cloud.isConfigured(this) && Accounts.findIdByEmail(this, mail) != null) {
                        tryCloudPassword(mail, pw)
                    } else {
                        password.text.clear()
                        showError(getString(R.string.login_wrong))
                    }
                }
            }
        }
    }

    /** Aceita a senha nova definida pelo link do e-mail e atualiza o hash guardado no aparelho. */
    private fun tryCloudPassword(mail: String, pw: String) {
        setChecking(true)
        Cloud.signIn(this, mail.trim().lowercase(), pw) { ok ->
            if (!ok) {
                setChecking(false)
                password.text.clear()
                showError(getString(R.string.login_wrong))
                return@signIn
            }
            inBackground({
                val id = Accounts.findIdByEmail(this, mail)
                if (id != null) {
                    Accounts.setPassword(this, id, pw)
                    Accounts.clearFailures(this)
                }
                id
            }) { id ->
                setChecking(false)
                if (id != null) enter(id) else showError(getString(R.string.login_wrong))
            }
        }
    }

    private fun setChecking(value: Boolean) {
        checking = value
        loginButton.isEnabled = !value
        loginButton.alpha = if (value) 0.6f else 1f
    }

    private fun showError(text: String) {
        error.text = text
        error.visibility = View.VISIBLE
    }

    private fun enter(accountId: String) {
        val next = if (SecurePrefs(this, accountId).onboardingDone) {
            Session.login(this, accountId)
            Intent(this, MainActivity::class.java)
        } else {
            // Cadastro interrompido no meio: continua de onde parou (contatos e permissoes).
            Intent(this, OnboardingActivity::class.java)
                .putExtra(OnboardingActivity.EXTRA_RESUME_ID, accountId)
        }
        startActivity(next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun confirmForgot() {
        val mail = email.text.toString().trim()
        if (mail.isEmpty()) {
            showError(getString(R.string.forgot_need_email))
            return
        }
        if (!Cloud.isConfigured(this)) { // sem recuperacao por e-mail, so resta apagar a conta
            confirmErase(mail)
            return
        }
        val items = arrayOf(getString(R.string.forgot_send_link), getString(R.string.forgot_erase))
        Ui.show(
            Ui.dialog(this, dark = false)
                .setTitle(R.string.forgot_title)
                .setItems(items) { _, which ->
                    if (which == 0) sendResetLink(mail) else confirmErase(mail)
                }
                .setNegativeButton(R.string.cancel, null),
            dark = false
        )
    }

    /** Envia o link de redefinicao para o e-mail. A resposta e a mesma exista ou nao conta com esse e-mail. */
    private fun sendResetLink(mail: String) {
        setChecking(true)
        Cloud.sendReset(this, mail.trim().lowercase()) { result ->
            setChecking(false)
            when (result) {
                CloudResult.NETWORK -> showError(getString(R.string.forgot_link_offline))
                CloudResult.ERROR -> showError(getString(R.string.forgot_link_error))
                else -> Toast.makeText(this, R.string.forgot_link_sent, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmErase(mail: String) {
        Ui.show(
            Ui.dialog(this, dark = false)
                .setTitle(R.string.forgot_erase)
                .setMessage(R.string.forgot_message)
                .setPositiveButton(R.string.forgot_confirm) { _, _ -> deleteAndSignUp(mail) }
                .setNegativeButton(R.string.cancel, null),
            dark = false
        )
    }

    private fun deleteAndSignUp(mail: String) {
        // A resposta e a mesma exista ou nao conta com esse e-mail (nao revela quem esta cadastrado).
        Accounts.findIdByEmail(this, mail)?.let { Accounts.delete(this, it) }
        Toast.makeText(this, R.string.forgot_done, Toast.LENGTH_LONG).show()
        // A tela de entrada decide o proximo passo: sem contas, vai direto ao cadastro.
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
