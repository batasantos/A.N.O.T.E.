package br.unesp.anote

import android.view.Window
import android.view.WindowManager

/**
 * Protecao contra captura de tela (FLAG_SECURE) das telas do app.
 *
 * MODO_CAPTURA = true libera prints e gravacao de tela, so para tirar imagens
 * da documentacao e dos slides. Volte para false antes de entregar o app.
 */
object Privacy {
    const val MODO_CAPTURA = false

    fun protect(window: Window?) {
        if (MODO_CAPTURA) return
        window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }
}
