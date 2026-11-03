package br.unesp.anote

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Dialogos dos menus: os da tela de notas (perfil, sobre) e os do painel (permissoes, ajuda). */
object MenuDialogs {

    const val REQ_MENU_PERMS = 77

    // ---------- Tela de notas ----------

    /** Edita o cadastro feito no primeiro acesso (nome e e-mail). */
    fun showProfile(activity: AppCompatActivity) {
        val prefs = SecurePrefs(activity)
        val pad = (24 * activity.resources.displayMetrics.density).toInt()

        val name = EditText(activity).apply {
            hint = activity.getString(R.string.hint_name)
            setText(prefs.userName)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }
        val email = EditText(activity).apply {
            hint = activity.getString(R.string.hint_email)
            setText(prefs.userEmail)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine()
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(name)
            addView(email)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.profile_title)
            .setView(container)
            .setPositiveButton(R.string.save, null) // tratado abaixo, para o dialogo nao fechar com erro
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim()
                val e = email.text.toString().trim()
                when {
                    n.isEmpty() -> name.error = activity.getString(R.string.error_name)
                    !Patterns.EMAIL_ADDRESS.matcher(e).matches() -> email.error = activity.getString(R.string.error_email)
                    else -> {
                        prefs.userName = n
                        prefs.userEmail = e
                        Toast.makeText(activity, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
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
        AlertDialog.Builder(activity)
            .setTitle(R.string.brand)
            .setMessage(
                activity.getString(R.string.about_text) + "\n\n" +
                    activity.getString(R.string.about_version, version)
            )
            .setPositiveButton(R.string.close, null)
            .show()
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

        val builder = AlertDialog.Builder(activity)
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
        builder.show()
    }

    /** O que o app faz e os telefones de apoio. Abre o discador (nao liga sozinho). */
    fun showHelp(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_text)
            .setPositiveButton(R.string.call_180) { _, _ -> dial(activity, "180") }
            .setNegativeButton(R.string.call_190) { _, _ -> dial(activity, "190") }
            .setNeutralButton(R.string.close, null)
            .show()
    }

    private fun dial(activity: AppCompatActivity, number: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    }
}
