package br.unesp.anote

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Tela invisivel que o atalho de configuracoes rapidas (tile) e o widget abrem para disparar o SOS.
 * Ela existe so porque, com o app fechado, o Android so deixa o servico pedir a localizacao se o app
 * tiver uma tela visivel naquele instante. Abre, dispara e fecha na hora, sem desenhar nada.
 *
 * Regras:
 *  - sem sessao aberta: nao ha contatos, so vibra duas vezes curtas;
 *  - ja ha um SOS em andamento: vibra duas vezes curtas e nao dispara outro;
 *  - sem contatos ou sem permissao de SMS: com o aparelho desbloqueado abre o painel; com o aparelho
 *    bloqueado so vibra (o painel mostra contatos e nao deve aparecer na tela de bloqueio);
 *  - widget ([EXTRA_CONFIRM]): precisa de 2 toques em ate 3 segundos, para um toque sem querer na tela
 *    inicial nao disparar. O primeiro toque vibra de leve.
 */
class TriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        runCatching { handle(intent.getBooleanExtra(EXTRA_CONFIRM, false)) }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun handle(needConfirm: Boolean) {
        if (needConfirm && !confirmed()) {
            QuickSos.tick(this)
            return
        }
        val accountId = Session.activeId(this)
        if (accountId == null || SosService.isSosActive) {
            QuickSos.vibrate(this, 2)
            return
        }
        val hasContacts = SecurePrefs(this, accountId).contacts().isNotEmpty()
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasContacts || !hasSms) {
            val locked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
            if (locked) {
                QuickSos.vibrate(this, 2)
            } else {
                startActivity(Intent(this, EmergencyActivity::class.java))
            }
            return
        }
        QuickSos.vibrate(this, 1)
        ContextCompat.startForegroundService(
            this,
            Intent(this, SosService::class.java).setAction(SosService.ACTION_SOS)
        )
    }

    /** true se este e o segundo toque dentro da janela; senao guarda o toque e devolve false. */
    private fun confirmed(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val second = lastTap > 0 && now - lastTap <= CONFIRM_MS
        lastTap = if (second) 0 else now
        return second
    }

    companion object {
        const val EXTRA_CONFIRM = "br.unesp.anote.CONFIRM"
        private const val CONFIRM_MS = 3000L
        private var lastTap = 0L

        fun intent(context: Context, needConfirm: Boolean): Intent =
            Intent(context, TriggerActivity::class.java)
                .putExtra(EXTRA_CONFIRM, needConfirm)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
