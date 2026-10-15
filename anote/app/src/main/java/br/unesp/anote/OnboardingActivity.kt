package br.unesp.anote

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/**
 * Primeiro acesso, em 3 passos:
 *  1. cadastro basico (nome e e-mail);
 *  2. contatos de confianca (pelo menos um);
 *  3. permissoes de localizacao, microfone, SMS e notificacoes.
 * Quando termina, grava onboardingDone e o app passa a abrir direto na lista de notas.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var prefs: SecurePrefs
    private lateinit var steps: List<View>
    private lateinit var dots: List<View>
    private lateinit var stepLabel: TextView
    private lateinit var contactsList: android.widget.LinearLayout
    private lateinit var nameField: EditText
    private lateinit var emailField: EditText
    private lateinit var contactNameField: EditText
    private lateinit var contactPhoneField: EditText
    private var step = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SecurePrefs(this)
        if (prefs.onboardingDone) {
            goHome()
            return
        }
        setContentView(R.layout.activity_onboarding)

        steps = listOf(findViewById(R.id.step1), findViewById(R.id.step2), findViewById(R.id.step3))
        dots = listOf(findViewById(R.id.dot1), findViewById(R.id.dot2), findViewById(R.id.dot3))
        stepLabel = findViewById(R.id.tv_step)
        contactsList = findViewById(R.id.onb_contacts)
        nameField = findViewById(R.id.edit_name)
        emailField = findViewById(R.id.edit_email)
        contactNameField = findViewById(R.id.edit_contact_name)
        contactPhoneField = findViewById(R.id.edit_contact_phone)

        // Se o app foi fechado no meio do cadastro, os campos voltam preenchidos.
        nameField.setText(prefs.userName)
        emailField.setText(prefs.userEmail)

        findViewById<View>(R.id.btn_next1).setOnClickListener { submitStep1() }
        findViewById<View>(R.id.btn_add_contact).setOnClickListener { addContact() }
        findViewById<View>(R.id.btn_next2).setOnClickListener { submitStep2() }
        findViewById<View>(R.id.btn_back2).setOnClickListener { showStep(1) }
        findViewById<View>(R.id.btn_allow).setOnClickListener { askPermissions() }
        findViewById<View>(R.id.btn_skip).setOnClickListener { finishOnboarding() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step > 1) showStep(step - 1) else finish()
            }
        })

        renderContacts()
        showStep(1)
    }

    private fun showStep(n: Int) {
        step = n
        steps.forEachIndexed { i, v -> v.visibility = if (i == n - 1) View.VISIBLE else View.GONE }
        dots.forEachIndexed { i, v ->
            v.setBackgroundResource(if (i < n) R.drawable.bg_dot_on else R.drawable.bg_dot_off)
        }
        stepLabel.text = getString(R.string.step_indicator, n)
    }

    // ---------- Passo 1: cadastro ----------

    private fun submitStep1() {
        val name = nameField.text.toString().trim()
        val email = emailField.text.toString().trim()
        when {
            name.isEmpty() -> nameField.error = getString(R.string.error_name)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> emailField.error = getString(R.string.error_email)
            else -> {
                prefs.userName = name
                prefs.userEmail = email
                showStep(2)
            }
        }
    }

    // ---------- Passo 2: contatos ----------

    private fun addContact() {
        val name = contactNameField.text.toString().trim()
        val phone = contactPhoneField.text.toString().filter { it.isDigit() || it == '+' }
        if (name.isEmpty() || phone.length < 10) {
            Toast.makeText(this, R.string.error_contact, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.addContact(TrustedContact(name, phone))
        contactNameField.text.clear()
        contactPhoneField.text.clear()
        renderContacts()
    }

    private fun renderContacts() {
        contactsList.removeAllViews()
        prefs.contacts().forEachIndexed { index, contact ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_contact_onboarding, contactsList, false)
            row.findViewById<TextView>(R.id.contact_initial).text =
                contact.name.firstOrNull()?.uppercase() ?: "?"
            row.findViewById<TextView>(R.id.contact_name).text = contact.name
            row.findViewById<TextView>(R.id.contact_phone).text = contact.phone
            row.findViewById<View>(R.id.contact_remove).setOnClickListener {
                prefs.removeContact(index)
                renderContacts()
            }
            contactsList.addView(row)
        }
    }

    private fun submitStep2() {
        // Se a pessoa digitou um contato e esqueceu de tocar em "Adicionar", aproveita.
        if (contactNameField.text.isNotBlank() && contactPhoneField.text.isNotBlank()) addContact()
        if (prefs.contacts().isEmpty()) {
            Toast.makeText(this, R.string.onb2_need_one, Toast.LENGTH_LONG).show()
            return
        }
        showStep(3)
    }

    // ---------- Passo 3: permissoes ----------

    private fun askPermissions() {
        val missing = Permissions.missing(this)
        if (missing.isEmpty()) {
            finishOnboarding()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Conceder ou negar, o cadastro termina; o painel pede de novo o que faltar na hora de usar.
        if (requestCode == REQ_PERMS) finishOnboarding()
    }

    private fun finishOnboarding() {
        prefs.onboardingDone = true
        goHome()
    }

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val REQ_PERMS = 7
    }
}
