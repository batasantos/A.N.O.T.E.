package br.unesp.anote

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)
