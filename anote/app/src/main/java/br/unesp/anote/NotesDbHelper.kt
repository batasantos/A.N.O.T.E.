package br.unesp.anote

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Banco SQLite local com as notas (CRUD).
 * O esquema abaixo e o mesmo do script database/schema.sql.
 * v1: notas simples | v2: coluna "pinned" (notas fixadas).
 */
class NotesDbHelper(context: Context) :
    SQLiteOpenHelper(context, "notes.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun Cursor.toNote() = Note(
        id = getLong(0),
        title = getString(1),
        content = getString(2),
        updatedAt = getLong(3),
        pinned = getInt(4) == 1
    )

    fun listAll(): List<Note> {
        val result = mutableListOf<Note>()
        readableDatabase.rawQuery(
            "SELECT id, title, content, updated_at, pinned FROM notes ORDER BY updated_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) result.add(c.toNote())
        }
        return result
    }

    fun getById(id: Long): Note? {
        readableDatabase.rawQuery(
            "SELECT id, title, content, updated_at, pinned FROM notes WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.toNote() else null
        }
    }

    /** Insere (id == 0) ou atualiza. Retorna o id da nota. */
    fun save(note: Note): Long {
        val values = ContentValues().apply {
            put("title", note.title)
            put("content", note.content)
            put("updated_at", System.currentTimeMillis())
            put("pinned", if (note.pinned) 1 else 0)
        }
        return if (note.id == 0L) {
            writableDatabase.insert("notes", null, values)
        } else {
            writableDatabase.update("notes", values, "id = ?", arrayOf(note.id.toString()))
            note.id
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete("notes", "id = ?", arrayOf(id.toString()))
    }
}
