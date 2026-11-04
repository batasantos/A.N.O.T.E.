package br.unesp.anote

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
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
        val prefs = SecurePrefs(activity)
        val builder = Ui.dialog(activity, dark = false)
        val view = LayoutInflater.from(builder.context).inflate(R.layout.dialog_profile, null)

        val avatar = view.findViewById<TextView>(R.id.profile_avatar)
        val name = view.findViewById<EditText>(R.id.profile_name)
        val email = view.findViewById<EditText>(R.id.profile_email)
        val nameError = view.findViewById<TextView>(R.id.profile_name_error)
        val emailError = view.findViewById<TextView>(R.id.profile_email_error)
        name.setText(prefs.userName)
        email.setText(prefs.userEmail)

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
            emailError.visibility = if (emailOk) View.GONE else View.VISIBLE
            if (!nameOk) name.setBackgroundResource(R.drawable.bg_input_error)
            if (!emailOk) email.setBackgroundResource(R.drawable.bg_input_error)
            if (nameOk && emailOk) {
                prefs.userName = n
                prefs.userEmail = e
                Toast.makeText(activity, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
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
