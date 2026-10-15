package br.unesp.anote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Lista central das permissoes de que o painel de emergencia precisa. */
object Permissions {

    /**
     * Localizacao precisa e aproximada devem ser pedidas juntas (exigencia do Android 12+),
     * alem de microfone, envio de SMS e, no Android 13+, notificacoes.
     */
    fun all(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun missing(context: Context, wanted: List<String> = all()): List<String> =
        wanted.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
}
