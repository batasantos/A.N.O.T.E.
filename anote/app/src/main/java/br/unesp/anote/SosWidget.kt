package br.unesp.anote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/**
 * Widget da tela inicial, com nome e icone neutros ("Notas"). Dois toques em ate 3 segundos disparam o
 * SOS (ver [TriggerActivity]); um toque so vibra de leve. A pessoa adiciona o widget sozinha.
 */
class SosWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = PendingIntent.getActivity(
            context, 0, TriggerActivity.intent(context, needConfirm = true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_sos)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
