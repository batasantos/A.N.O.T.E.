package br.unesp.anote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Base das telas do painel de emergencia (o painel e a tela de contatos).
 *
 *  - FLAG_SECURE: sem captura de tela e conteudo escondido nos apps recentes;
 *  - fecha tudo quando a tela do celular apaga;
 *  - fecha tudo quando a pessoa sai do app (Home, outro app...).
 *
 * Quando o app abre uma tela interna ou um pedido de permissao de proposito,
 * a tela marca [keepOnStop] = true para nao se fechar nessa troca.
 */
abstract class GuardedActivity : AppCompatActivity() {

    protected var keepOnStop = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.parseColor("#0E0A1F")
        window.navigationBarColor = Color.parseColor("#1B1236")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onResume() {
        super.onResume()
        keepOnStop = false
    }

    override fun onStop() {
        super.onStop()
        // isFinishing: se a tela esta fechando normalmente (botao voltar), nao derruba as outras.
        if (!keepOnStop && !isFinishing) finishAndRemoveTask()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }
}
