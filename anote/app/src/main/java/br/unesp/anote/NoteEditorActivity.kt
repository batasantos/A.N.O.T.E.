package br.unesp.anote

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Editor de notas (Figuras 9 e 10).
 *
 * Gatilho: se o titulo for igual ao titulo-gatilho (padrao "..."), o painel de
 * emergencia abre ao tocar em "Concluido", em "Notas" (voltar) ou ao pressionar Enter
 * no titulo. A nota-gatilho nunca e gravada no banco.
 */
class NoteEditorActivity : AppCompatActivity() {

    private lateinit var db: NotesDbHelper
    private lateinit var prefs: SecurePrefs
    private lateinit var title: EditText
    private lateinit var content: EditText
    private var noteId = 0L
    private var pinned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        db = NotesDbHelper(this)
        prefs = SecurePrefs(this)
        title = findViewById(R.id.edit_title)
        content = findViewById(R.id.edit_content)
        noteId = intent.getLongExtra(EXTRA_ID, 0L)

        if (noteId != 0L) {
            db.getById(noteId)?.let {
                title.setText(it.title)
                content.setText(it.content)
                pinned = it.pinned
            }
        }
        renderPin()

        findViewById<View>(R.id.btn_done).setOnClickListener { finishEditing() }
        findViewById<View>(R.id.btn_back).setOnClickListener { finishEditing() }
        findViewById<View>(R.id.btn_delete).setOnClickListener { confirmDelete() }
        findViewById<View>(R.id.btn_pin).setOnClickListener {
            pinned = !pinned
            renderPin()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishEditing()
        })

        // Enter no titulo: se for o gatilho, abre o painel ("Pressione enter para continuar").
        title.setOnEditorActionListener { _, actionId, event ->
            val enter = actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (enter && isTrigger(title.text.toString())) {
                openPanel()
                true
            } else {
                false
            }
        }
    }

    private fun isTrigger(t: String) =
        t.isNotBlank() && SecurePrefs.normalize(t) == SecurePrefs.normalize(prefs.triggerTitle)

    private fun openPanel() {
        startActivity(Intent(this, EmergencyActivity::class.java))
        finish()
    }

    private fun finishEditing() {
        val t = title.text.toString().trim()
        val c = content.text.toString()

        if (isTrigger(t)) {
            openPanel()
            return
        }
        if (t.isEmpty() && c.isBlank()) {
            // Nota vazia: nao grava; se ja existia, mantem como estava.
            finish()
            return
        }
        db.save(Note(id = noteId, title = t, content = c, pinned = pinned))
        finish()
    }

    private fun renderPin() {
        findViewById<TextView>(R.id.tv_pin).setText(if (pinned) R.string.pinned else R.string.pin)
        findViewById<ImageView>(R.id.img_pin).setColorFilter(
            ContextCompat.getColor(this, if (pinned) R.color.purple_primary else R.color.purple_light)
        )
    }

    private fun confirmDelete() {
        if (noteId == 0L) {
            finish()
            return
        }
        Ui.show(
            Ui.dialog(this, dark = false)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    db.delete(noteId)
                    finish()
                }
                .setNegativeButton(R.string.cancel, null),
            dark = false
        )
    }

    companion object {
        const val EXTRA_ID = "note_id"
    }
}
