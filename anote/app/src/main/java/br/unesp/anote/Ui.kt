package br.unesp.anote

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Visual dos menus e dialogos, para combinar com o resto do app:
 * claro e roxo na tela de notas (dark = false), escuro com detalhes em ambar no painel (dark = true).
 */
object Ui {

    class MenuEntry(
        @DrawableRes val icon: Int,
        @StringRes val label: Int,
        val danger: Boolean = false,
        val onClick: () -> Unit
    )

    // ---------- Menu (⋮) ----------

    /** Abre um cartao arredondado, com sombra e icones, preso ao canto do [anchor]. */
    fun showMenu(anchor: View, dark: Boolean, entries: List<MenuEntry>) {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (dark) R.drawable.bg_popup_dark else R.drawable.bg_popup_light)
            setPadding(0, dp(8), 0, dp(8))
            elevation = dp(10).toFloat()
        }
        // Margem em volta do cartao para a sombra caber dentro da janela do popup.
        val root = FrameLayout(context).apply {
            setPadding(dp(12), dp(6), dp(12), dp(14))
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val popup = PopupWindow(root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true

        val textColor = ContextCompat.getColor(context, if (dark) R.color.dark_text else R.color.ink)
        val iconColor = ContextCompat.getColor(context, if (dark) R.color.amber else R.color.purple_primary)
        val dangerColor = ContextCompat.getColor(context, if (dark) R.color.danger_soft else R.color.danger_dark)

        entries.forEach { entry ->
            val row = LayoutInflater.from(context).inflate(R.layout.item_menu, card, false)
            val color = if (entry.danger) dangerColor else textColor
            row.findViewById<ImageView>(R.id.menu_icon).apply {
                setImageResource(entry.icon)
                setColorFilter(if (entry.danger) dangerColor else iconColor)
            }
            row.findViewById<TextView>(R.id.menu_label).apply {
                setText(entry.label)
                setTextColor(color)
            }
            row.setOnClickListener {
                popup.dismiss()
                entry.onClick()
            }
            card.addView(row)
        }

        // Largura minima agradavel e alinhamento da borda direita do menu com a do botao.
        card.minimumWidth = dp(230)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        popup.showAsDropDown(anchor, anchor.width - root.measuredWidth + dp(12), -dp(4))
    }

    // ---------- Dialogos ----------

    fun dialog(context: Context, dark: Boolean): MaterialAlertDialogBuilder =
        MaterialAlertDialogBuilder(context, if (dark) R.style.Anote_Dialog_Dark else R.style.Anote_Dialog_Light)

    /**
     * Fundo arredondado nas cores do app. No painel (dark) tambem bloqueia a captura de tela do
     * dialogo, ja que ele e uma janela separada da tela protegida.
     */
    fun prepare(dialog: Dialog, dark: Boolean) {
        dialog.window?.apply {
            setBackgroundDrawableResource(if (dark) R.drawable.bg_dialog_dark else R.drawable.bg_dialog_light)
            if (dark) setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun show(builder: MaterialAlertDialogBuilder, dark: Boolean): AlertDialog {
        val dialog = builder.create()
        prepare(dialog, dark)
        dialog.show()
        return dialog
    }
}
