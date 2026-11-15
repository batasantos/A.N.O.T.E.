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
 * "Esqueci minha senha": com o codigo de recuperacao (mostrado uma vez no cadastro) a pessoa define uma
 * senha nova sem perder nada. Sem o codigo, a unica saida e apagar a conta daquele e-mail, com tudo
 * que ela guarda, e criar uma nova (os dados ficam so no aparelho).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var error: TextView
    private lateinit var loginButton: TextView
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

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
        val cloud = Cloud.isConfigured(this)
        val items = if (cloud) {
            arrayOf(getString(R.string.forgot_send_link), getString(R.string.forgot_use_code), getString(R.string.forgot_erase))
        } else {
            arrayOf(getString(R.string.forgot_use_code), getString(R.string.forgot_erase))
        }
        Ui.show(
            Ui.dialog(this, dark = false)
                .setTitle(R.string.forgot_title)
                .setItems(items) { _, which ->
                    when (items[which]) {
                        getString(R.string.forgot_send_link) -> sendResetLink(mail)
                        getString(R.string.forgot_use_code) -> showCodeDialog(mail)
                        else -> confirmErase(mail)
                    }
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

    /** Redefine a senha com o codigo de recuperacao: as notas e os contatos continuam ali. */
    private fun showCodeDialog(mail: String) {
        val builder = Ui.dialog(this, dark = false)
        val codeField = EditText(builder.context).apply {
            hint = getString(R.string.forgot_code_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val newField = EditText(builder.context).apply {
            hint = getString(R.string.forgot_new_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val box = android.widget.LinearLayout(builder.context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 24, 64, 0)
            addView(codeField, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(newField, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val dialog = builder
            .setTitle(R.string.forgot_use_code)
            .setMessage(R.string.forgot_code_message)
            .setView(box)
            .setPositiveButton(R.string.forgot_code_confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        Ui.prepare(dialog, dark = false)
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val code = codeField.text.toString()
                val fresh = newField.text.toString()
                if (RecoveryCode.normalize(code).length != RecoveryCode.LENGTH) {
                    codeField.error = getString(R.string.forgot_code_invalid)
                    return@setOnClickListener
                }
                if (fresh.length < Accounts.MIN_PASSWORD) {
                    newField.error = getString(R.string.error_password_short)
                    return@setOnClickListener
                }
                dialog.dismiss()
                setChecking(true)
                inBackground({ Accounts.resetWithCode(this, mail, code, fresh) }) { (result, newCode) ->
                    setChecking(false)
                    when (result) {
                        is Accounts.AuthResult.Success -> {
                            password.text.clear()
                            error.visibility = View.GONE
                            Toast.makeText(this, R.string.forgot_reset_done, Toast.LENGTH_LONG).show()
                            if (newCode != null) MenuDialogs.showRecoveryCode(this, newCode) {}
                        }
                        is Accounts.AuthResult.Locked -> showError(getString(R.string.login_locked, result.seconds))
                        // Mesma resposta para e-mail inexistente, conta sem codigo e codigo errado.
                        Accounts.AuthResult.Wrong -> showError(getString(R.string.forgot_reset_failed))
                    }
                }
            }
        }
        dialog.show()
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
