package br.unesp.anote

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat

/**
 * Atalho de disparo rapido: apertar "volume para baixo" 3 vezes em ate 2 segundos, nas telas de notas,
 * envia o SOS sem abrir o painel. Confirma com uma vibracao curta (nada na tela).
 * Sem contatos ou sem permissao de SMS, abre o painel para a pessoa resolver.
 */
object QuickSos {

    private const val WINDOW_MS = 2000L
    private var count = 0
    private var windowStart = 0L

    /** Chame no onKeyDown da tela. Devolve true se a tecla foi consumida (3o aperto). */
    fun onKeyDown(activity: Activity, keyCode: Int, event: KeyEvent, enabled: Boolean): Boolean {
        if (!enabled || keyCode != KeyEvent.KEYCODE_VOLUME_DOWN || event.repeatCount != 0) return false
        val now = SystemClock.elapsedRealtime()
        if (now - windowStart > WINDOW_MS) {
            count = 0
            windowStart = now
        }
        count++
        if (count < 3) return false
        count = 0
        trigger(activity)
        return true
    }

    private fun trigger(activity: Activity) {
        if (SosService.isSosActive) { // ja ha um SOS em andamento: nao dispara outro
            vibrate(activity, 2)
            return
        }
        val hasContacts = SecurePrefs(activity).contacts().isNotEmpty()
        val hasSms = ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasContacts || !hasSms) {
            activity.startActivity(Intent(activity, EmergencyActivity::class.java))
            return
        }
        vibrate(activity, 1)
        ContextCompat.startForegroundService(
            activity,
            Intent(activity, SosService::class.java).setAction(SosService.ACTION_SOS)
        )
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context, times: Int) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = if (times == 1) longArrayOf(0, 250) else longArrayOf(0, 120, 100, 120)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
