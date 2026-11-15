package br.unesp.anote

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity

/**
 * Guia "como o app funciona": aparece uma vez, logo depois do cadastro, e pode ser reaberto
 * pelo menu do painel de emergencia. Explica as notas, o titulo secreto, o SOS, a gravacao e a privacidade.
 * Mostra o titulo de acesso real da conta no passo do painel.
 */
class GuideActivity : AppCompatActivity() {

    private class Step(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val body: Int)

    private val steps = listOf(
        Step(R.drawable.ic_note, R.string.guide1_title, R.string.guide1_body),
        Step(R.drawable.ic_pin, R.string.guide2_title, R.string.guide2_body),
        Step(R.drawable.ic_key, R.string.guide3_title, R.string.guide3_body),
        Step(R.drawable.ic_message, R.string.guide4_title, R.string.guide4_body),
        Step(R.drawable.ic_mic, R.string.guide5_title, R.string.guide5_body),
        Step(R.drawable.ic_lock, R.string.guide6_title, R.string.guide6_body)
    )

    private var index = 0
    private var fromMenu = false
    private lateinit var trigger: String

    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var counter: TextView
    private lateinit var dots: LinearLayout
    private lateinit var back: View
    private lateinit var next: TextView
    private lateinit var skip: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.isActive(this)) { // sem sessao aberta nao ha o que mostrar
            finish()
            return
        }
        // O guia cita o titulo secreto: sem print e fora dos apps recentes.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_guide)

        fromMenu = intent.getBooleanExtra(EXTRA_FROM_MENU, false)
        trigger = SecurePrefs(this).triggerTitle
        index = savedInstanceState?.getInt(STATE_INDEX) ?: 0

        icon = findViewById(R.id.img_icon)
        title = findViewById(R.id.tv_title)
        body = findViewById(R.id.tv_body)
        counter = findViewById(R.id.tv_counter)
        dots = findViewById(R.id.dots)
        back = findViewById(R.id.btn_back)
        next = findViewById(R.id.btn_next)
        skip = findViewById(R.id.btn_skip)

        icon.setColorFilter(Color.WHITE)
        buildDots()

        next.setOnClickListener {
            if (index < steps.size - 1) { index++; render() } else finishGuide()
        }
        back.setOnClickListener { if (index > 0) { index--; render() } }
        skip.setOnClickListener { finishGuide() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (index > 0) { index--; render() } else finishGuide()
            }
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_INDEX, index)
    }

    private fun buildDots() {
        val d = resources.displayMetrics.density
        repeat(steps.size) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams((8 * d).toInt(), (6 * d).toInt())
            lp.marginStart = (3 * d).toInt()
            lp.marginEnd = (3 * d).toInt()
            dots.addView(dot, lp)
        }
    }

    private fun render() {
        val step = steps[index]
        icon.setImageResource(step.icon)
        title.setText(step.title)
        body.text = getString(step.body, trigger)
        counter.text = getString(R.string.guide_counter, index + 1, steps.size)
        for (i in 0 until dots.childCount) {
            dots.getChildAt(i).setBackgroundResource(
                if (i == index) R.drawable.bg_dot_on else R.drawable.bg_dot_off
            )
        }
        back.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        val last = index == steps.size - 1
        skip.visibility = if (last) View.INVISIBLE else View.VISIBLE
        next.setText(if (last) R.string.guide_done else R.string.guide_next)
    }

    private fun finishGuide() {
        if (fromMenu) { // reaberto pelo menu: so volta para o painel
            finish()
            return
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_FROM_MENU = "from_menu"
        private const val STATE_INDEX = "guide_index"
    }
}
