package br.unesp.anote

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Atalho nas configuracoes rapidas (ao puxar a barra de notificacoes). Com nome e icone neutros ("Notas").
 * Um toque dispara o SOS pela [TriggerActivity], que funciona com o app fechado e, se o Android deixar
 * o atalho aparecer com o aparelho bloqueado, tambem na tela de bloqueio. A pessoa adiciona o atalho
 * sozinha, editando as configuracoes rapidas.
 */
class SosTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            label = getString(R.string.tile_label)
            icon = Icon.createWithResource(this@SosTileService, R.drawable.ic_note)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        val intent = TriggerActivity.intent(this, needConfirm = false)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
