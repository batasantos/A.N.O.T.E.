package br.unesp.anote

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Dialogos dos menus. Os da tela de notas (perfil, sobre) sao claros; os do painel
 * (permissoes, ajuda) sao escuros e bloqueiam captura de tela. Ver [Ui].
 */
object MenuDialogs {

    const val REQ_MENU_PERMS = 77

    // ---------- Tela de notas ----------

    /** Edita o cadastro feito no primeiro acesso (nome e e-mail), com erros ao lado do campo. */
    fun showProfile(activity: AppCompatActivity) {
        val accountId = Session.activeId(activity) ?: return
        val account = Accounts.get(activity, accountId) ?: return
        val builder = Ui.dialog(activity, dark = false)
        val view = LayoutInflater.from(builder.context).inflate(R.layout.dialog_profile, null)

        val avatar = view.findViewById<TextView>(R.id.profile_avatar)
        val name = view.findViewById<EditText>(R.id.profile_name)
        val email = view.findViewById<EditText>(R.id.profile_email)
        val nameError = view.findViewById<TextView>(R.id.profile_name_error)
        val emailError = view.findViewById<TextView>(R.id.profile_email_error)
        name.setText(account.name)
        email.setText(account.email)

        fun updateAvatar() {
            avatar.text = name.text.toString().trim().firstOrNull()?.uppercase() ?: "?"
        }
        updateAvatar()
        name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateAvatar()
                nameError.visibility = View.GONE
                name.setBackgroundResource(R.drawable.bg_input)
            }
        })
        email.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                emailError.visibility = View.GONE
                email.setBackgroundResource(R.drawable.bg_input)
            }
        })

        val dialog = builder.setView(view).create()
        Ui.prepare(dialog, dark = false)
        view.findViewById<View>(R.id.profile_cancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.profile_save).setOnClickListener {
            val n = name.text.toString().trim()
            val e = email.text.toString().trim()
            val nameOk = n.isNotEmpty()
            val emailOk = Patterns.EMAIL_ADDRESS.matcher(e).matches()
            nameError.visibility = if (nameOk) View.GONE else View.VISIBLE
            emailError.setText(R.string.error_email)
            emailError.visibility = if (emailOk) View.GONE else View.VISIBLE
            if (!nameOk) name.setBackgroundResource(R.drawable.bg_input_error)
            if (!emailOk) email.setBackgroundResource(R.drawable.bg_input_error)
            if (nameOk && emailOk) {
                if (Accounts.updateProfile(activity, accountId, n, e)) {
                    Toast.makeText(activity, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else { // o e-mail ja pertence a outra conta
                    emailError.setText(R.string.error_email_taken)
                    emailError.visibility = View.VISIBLE
                    email.setBackgroundResource(R.drawable.bg_input_error)
                }
            }
        }
        dialog.show()
    }

    /** Troca de senha: pede a atual (com o mesmo bloqueio por tentativas do login), a nova e a confirmacao. */
    fun showChangePassword(activity: AppCompatActivity) {
        val accountId = Session.activeId(activity) ?: return
        val builder = Ui.dialog(activity, dark = false)
        val view = LayoutInflater.from(builder.context).inflate(R.layout.dialog_password, null)

        val current = view.findViewById<EditText>(R.id.pw_current)
        val fresh = view.findViewById<EditText>(R.id.pw_new)
        val confirm = view.findViewById<EditText>(R.id.pw_confirm)
        val currentError = view.findViewById<TextView>(R.id.pw_current_error)
        val freshError = view.findViewById<TextView>(R.id.pw_new_error)
        val confirmError = view.findViewById<TextView>(R.id.pw_confirm_error)
        val save = view.findViewById<TextView>(R.id.pw_save)

        // Ao digitar de novo no campo, o erro dele some.
        fun clearOnType(field: EditText, error: TextView) {
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    error.visibility = View.GONE
                    field.setBackgroundResource(R.drawable.bg_input)
                }
            })
        }
        clearOnType(current, currentError)
        clearOnType(fresh, freshError)
        clearOnType(confirm, confirmError)

        fun fail(field: EditText, error: TextView, message: String) {
            error.text = message
            error.visibility = View.VISIBLE
            field.setBackgroundResource(R.drawable.bg_input_error)
        }

        val dialog = builder.setView(view).create()
        Ui.prepare(dialog, dark = false)
        view.findViewById<View>(R.id.pw_cancel).setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            val cur = current.text.toString()
            val new = fresh.text.toString()
            val again = confirm.text.toString()
            var valid = true
            if (cur.isEmpty()) {
                fail(current, currentError, activity.getString(R.string.error_password)); valid = false
            }
            if (new.length < Accounts.MIN_PASSWORD) {
                fail(fresh, freshError, activity.getString(R.string.error_password_short)); valid = false
            } else if (new == cur) {
                fail(fresh, freshError, activity.getString(R.string.error_password_same)); valid = false
            }
            if (new != again) {
                fail(confirm, confirmError, activity.getString(R.string.error_password_mismatch)); valid = false
            }
            if (!valid) return@setOnClickListener

            save.isEnabled = false
            // O hash da senha e lento de proposito: roda fora da thread da tela.
            activity.inBackground({ Accounts.changePassword(activity, accountId, cur, new) }) { result ->
                save.isEnabled = true
                when (result) {
                    is Accounts.AuthResult.Success -> {
                        Toast.makeText(activity, R.string.password_changed, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    is Accounts.AuthResult.Locked -> {
                        current.setText("")
                        fail(current, currentError, activity.getString(R.string.login_locked, result.seconds))
                    }
                    Accounts.AuthResult.Wrong -> {
                        current.setText("")
                        fail(current, currentError, activity.getString(R.string.error_password_current))
                    }
                }
            }
        }
        dialog.show()
    }

    /**
     * Pede a senha antes de excluir a conta (evita exclusao por toque acidental ou por outra
     * pessoa com o celular desbloqueado). [onConfirmed] roda so se a senha estiver certa.
     */
    fun showDeleteAccount(activity: AppCompatActivity, onConfirmed: () -> Unit) {
        val accountId = Session.activeId(activity) ?: return
        val builder = Ui.dialog(activity, dark = false)
        val field = EditText(builder.context).apply {
            hint = activity.getString(R.string.hint_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val container = LinearLayout(builder.context).apply {
            setPadding(64, 24, 64, 0)
            addView(field, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val dialog = builder
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_message)
            .setView(container)
            .setPositiveButton(R.string.delete_account_confirm, null) // tratado abaixo, para nao fechar com erro
            .setNegativeButton(R.string.cancel, null)
            .create()
        Ui.prepare(dialog, dark = false)
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val typed = field.text.toString()
                activity.inBackground({ Accounts.verifyPassword(activity, accountId, typed) }) { result ->
                    when (result) {
                        is Accounts.AuthResult.Success -> {
                            dialog.dismiss()
                            onConfirmed()
                        }
                        is Accounts.AuthResult.Locked -> {
                            field.setText("")
                            field.error = activity.getString(R.string.login_locked, result.seconds)
                        }
                        Accounts.AuthResult.Wrong -> {
                            field.setText("")
                            field.error = activity.getString(R.string.delete_account_wrong)
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    /** "Sobre" neutro: nao menciona a funcao de emergencia. */
    fun showAbout(activity: AppCompatActivity) {
        val version = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
        Ui.show(
            Ui.dialog(activity, dark = false)
                .setTitle(R.string.brand)
                .setMessage(
                    activity.getString(R.string.about_text) + "\n\n" +
                        activity.getString(R.string.about_version, version)
                )
                .setPositiveButton(R.string.close, null),
            dark = false
        )
    }

    // ---------- Painel de emergencia ----------

    /**
     * Mostra o que esta liberado e deixa pedir o que falta ou abrir as configuracoes do Android
     * (necessario quando a pessoa ja negou duas vezes e o sistema nao pergunta mais).
     * [onRequest] roda logo antes do pedido, para a tela nao se fechar durante ele.
     */
    fun showPermissions(activity: AppCompatActivity, onRequest: () -> Unit = {}) {
        val items = mutableListOf(
            R.string.perm_location to android.Manifest.permission.ACCESS_FINE_LOCATION,
            R.string.perm_mic to android.Manifest.permission.RECORD_AUDIO,
            R.string.perm_sms to android.Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            items.add(R.string.perm_notif to android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val lines = items.joinToString("\n") { (label, permission) ->
            val ok = ContextCompat.checkSelfPermission(activity, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            (if (ok) "✓  " else "✗  ") + activity.getString(label)
        }
        val missing = Permissions.missing(activity)
        val footer = activity.getString(
            if (missing.isEmpty()) R.string.permissions_all_ok else R.string.permissions_missing
        )

        val builder = Ui.dialog(activity, dark = true)
            .setTitle(R.string.permissions_title)
            .setMessage("$lines\n\n$footer")
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.open_settings) { _, _ ->
                activity.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${activity.packageName}"))
                )
            }
        if (missing.isNotEmpty()) {
            builder.setPositiveButton(R.string.allow) { _, _ ->
                onRequest()
                ActivityCompat.requestPermissions(activity, missing.toTypedArray(), REQ_MENU_PERMS)
            }
        }
        Ui.show(builder, dark = true)
    }

    /** O que o app faz e os telefones de apoio. Abre o discador (nao liga sozinho). */
    fun showHelp(activity: AppCompatActivity) {
        Ui.show(
            Ui.dialog(activity, dark = true)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_text)
                .setPositiveButton(R.string.call_180) { _, _ -> dial(activity, "180") }
                .setNegativeButton(R.string.call_190) { _, _ -> dial(activity, "190") }
                .setNeutralButton(R.string.close, null),
            dark = true
        )
    }

    private fun dial(activity: AppCompatActivity, number: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    }
}
