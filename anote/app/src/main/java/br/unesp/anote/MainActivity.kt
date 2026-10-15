package br.unesp.anote

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Lista de notas (Figura 8). Para quem olha, e um bloco de notas comum. */
class MainActivity : AppCompatActivity() {

    private lateinit var db: NotesDbHelper
    private lateinit var adapter: NotesAdapter
    private lateinit var empty: TextView
    private lateinit var search: EditText
    private var alphabetical = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = NotesDbHelper(this)
        empty = findViewById(R.id.empty)
        search = findViewById(R.id.edit_search)

        adapter = NotesAdapter { note -> openEditor(note.id) }
        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<View>(R.id.fab).setOnClickListener { openEditor(0L) }
        findViewById<View>(R.id.btn_sort).setOnClickListener {
            alphabetical = !alphabetical
            refresh()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refresh()
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val q = search.text.toString().trim().lowercase()
        var notes = db.listAll().filter {
            q.isEmpty() || it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
        }
        if (alphabetical) notes = notes.sortedBy { it.title.lowercase() }

        val pinned = notes.filter { it.pinned }
        val recent = notes.filter { !it.pinned }
        val rows = mutableListOf<Row>()
        if (pinned.isNotEmpty()) {
            rows.add(Row.Header(getString(R.string.section_pinned)))
            pinned.forEach { rows.add(Row.Item(it)) }
        }
        if (recent.isNotEmpty()) {
            rows.add(Row.Header(getString(R.string.section_recent)))
            recent.forEach { rows.add(Row.Item(it)) }
        }
        adapter.submit(rows)
        empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openEditor(noteId: Long) {
        startActivity(Intent(this, NoteEditorActivity::class.java).putExtra(NoteEditorActivity.EXTRA_ID, noteId))
    }

    private fun formatDate(ts: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ts }
        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        if (sameDay(now, then)) return getString(R.string.today)
        now.add(Calendar.DAY_OF_YEAR, -1)
        if (sameDay(now, then)) return getString(R.string.yesterday)
        return SimpleDateFormat("d MMM", Locale("pt", "BR")).format(then.time).replace(".", "")
    }

    private sealed class Row {
        data class Header(val text: String) : Row()
        data class Item(val note: Note) : Row()
    }

    private inner class NotesAdapter(private val onClick: (Note) -> Unit) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var rows: List<Row> = emptyList()

        fun submit(list: List<Row>) {
            rows = list
            notifyDataSetChanged()
        }

        inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.header_text)
        }

        inner class NoteHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.note_title)
            val date: TextView = view.findViewById(R.id.note_date)
            val preview: TextView = view.findViewById(R.id.note_preview)
        }

        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                HeaderHolder(inflater.inflate(R.layout.item_header, parent, false))
            } else {
                NoteHolder(inflater.inflate(R.layout.item_note, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).text.text = row.text
                is Row.Item -> {
                    val h = holder as NoteHolder
                    h.title.text = row.note.title.ifBlank { getString(R.string.untitled) }
                    h.date.text = formatDate(row.note.updatedAt)
                    h.preview.text = row.note.content
                    h.itemView.setOnClickListener { onClick(row.note) }
                }
            }
        }

        override fun getItemCount() = rows.size
    }
}
