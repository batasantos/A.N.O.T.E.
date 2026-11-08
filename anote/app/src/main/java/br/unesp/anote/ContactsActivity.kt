package br.unesp.anote

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Gerenciar contatos de confianca (menu do painel de emergencia): adicionar, editar e remover.
 * Herda a protecao do painel: sem captura de tela e fecha ao apagar a tela ou sair do app.
 */
class ContactsActivity : GuardedActivity() {

    private lateinit var prefs: SecurePrefs
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.isActive(this)) { // sessao encerrada: nao ha conta para mostrar
            finishAndRemoveTask()
            return
        }
        setContentView(R.layout.activity_contacts)

        prefs = SecurePrefs(this)
        list = findViewById(R.id.contacts_list)
        empty = findViewById(R.id.tv_empty)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_add).setOnClickListener { showEditDialog(-1) }
        render()
    }

    private fun render() {
        list.removeAllViews()
        val contacts = prefs.contacts()
        empty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
        contacts.forEachIndexed { index, contact ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_contact, list, false)
            row.findViewById<TextView>(R.id.contact_initial).text =
                contact.name.firstOrNull()?.uppercase() ?: "?"
            row.findViewById<TextView>(R.id.contact_name).text = contact.name
            row.findViewById<TextView>(R.id.contact_phone).text = contact.phone
            row.findViewById<View>(R.id.contact_dispatch).visibility = View.GONE // aqui so se edita
            row.setOnClickListener { showEditDialog(index) }
            list.addView(row)
        }
    }

    /** index < 0 = novo contato; senao edita o contato daquela posicao (com opcao de remover). */
    private fun showEditDialog(index: Int) {
        val builder = Ui.dialog(this, dark = true)
        // Infla com o contexto do dialogo para os campos usarem as cores escuras.
        val view = LayoutInflater.from(builder.context).inflate(R.layout.dialog_contact, null)
        val nameField = view.findViewById<EditText>(R.id.dialog_name)
        val phoneField = view.findViewById<EditText>(R.id.dialog_phone)
        val editing = index >= 0
        if (editing) {
            val current = prefs.contacts()[index]
            nameField.setText(current.name)
            phoneField.setText(current.phone)
        }

        builder
            .setTitle(if (editing) R.string.edit_contact else R.string.add_contact)
            .setView(view)
            .setPositiveButton(R.string.save, null) // tratado abaixo, para nao fechar com erro
            .setNegativeButton(R.string.cancel, null)
        if (editing) {
            builder.setNeutralButton(R.string.remove) { _, _ ->
                prefs.removeContact(index)
                render()
            }
        }
        val dialog = builder.create()
        Ui.prepare(dialog, dark = true)
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                val phone = phoneField.text.toString().filter { it.isDigit() || it == '+' }
                if (name.isEmpty() || phone.length < 10) {
                    Toast.makeText(this, R.string.error_contact, Toast.LENGTH_SHORT).show()
                } else {
                    if (editing) prefs.updateContact(index, TrustedContact(name, phone))
                    else prefs.addContact(TrustedContact(name, phone))
                    render()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
