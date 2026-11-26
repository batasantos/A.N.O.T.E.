package br.unesp.anote

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela de bloqueio ao reabrir o app: usa o desbloqueio do proprio aparelho (senha, PIN, padrao
 * ou biometria). O atalho de SOS (volume para baixo 3 vezes) continua funcionando aqui.
 */
class LockActivity : AppCompatActivity() {

    private val unlockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                AppLock.locked = false
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Privacy.protect(window)
        setContentView(R.layout.activity_lock)

        if (!Session.isActive(this) || !AppLock.deviceIsSecure(this)) {
            // Sem sessao, ou o aparelho perdeu a tela de bloqueio: nao ha como (nem o que) desbloquear.
            AppLock.locked = false
            finish()
            return
        }
        findViewById<View>(R.id.btn_unlock).setOnClickListener { askUnlock() }
        findViewById<View>(R.id.btn_lock_logout).setOnClickListener {
            AppLock.locked = false
            Session.logout(this)
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
        // Voltar nao libera o app: so leva para a tela inicial do Android.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        if (savedInstanceState == null) askUnlock()
    }

    private fun askUnlock() {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            getString(R.string.lock_prompt_title), getString(R.string.lock_prompt_text)
        ) ?: return
        unlockLauncher.launch(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val enabled = runCatching { SecurePrefs(this).quickShortcut }.getOrDefault(false)
        if (QuickSos.onKeyDown(this, keyCode, event, enabled)) return true
        return super.onKeyDown(keyCode, event)
    }
}
