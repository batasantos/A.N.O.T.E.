package br.unesp.anote

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock

/**
 * Aplicacao: cuida do bloqueio ao reabrir. Se a pessoa ligou o bloqueio (menu da tela de notas) e o app
 * ficou fora da tela por mais de [GRACE_MS], ao voltar aparece a [LockActivity], que pede a senha,
 * o PIN, o padrao ou a biometria que ja desbloqueia o aparelho.
 */
class AnoteApp : Application(), Application.ActivityLifecycleCallbacks {

    private var started = 0
    private var leftAt = 0L

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (started == 0 && leftAt > 0 && SystemClock.elapsedRealtime() - leftAt > GRACE_MS && lockEnabled()) {
            AppLock.locked = true
        }
        started++
    }

    override fun onActivityStopped(activity: Activity) {
        started--
        if (started <= 0) {
            started = 0
            leftAt = SystemClock.elapsedRealtime()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!AppLock.locked || activity is LockActivity) return
        if (!Session.isActive(this)) { // sem sessao nao ha o que proteger
            AppLock.locked = false
            return
        }
        activity.startActivity(Intent(activity, LockActivity::class.java))
    }

    private fun lockEnabled(): Boolean {
        val id = Session.activeId(this) ?: return false
        return runCatching { SecurePrefs(this, id).appLock }.getOrDefault(false)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        /** Tempo fora do app sem pedir o desbloqueio de novo. */
        private const val GRACE_MS = 30_000L
    }
}

/** Estado do bloqueio ao reabrir. */
object AppLock {
    @Volatile
    var locked = false

    /** O aparelho tem senha, PIN, padrao ou biometria configurados? Sem isso nao ha como desbloquear. */
    fun deviceIsSecure(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
}
