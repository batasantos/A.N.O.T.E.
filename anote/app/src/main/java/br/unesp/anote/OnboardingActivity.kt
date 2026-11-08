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
 * Cadastro de uma conta, em 3 passos:
 *  1. dados basicos (nome, e-mail e senha) - aqui a conta e criada;
 *  2. contatos de confianca (pelo menos um);
 *  3. permissoes de localizacao, microfone, SMS e notificacoes.
 * Ao terminar, a conta e marcada como pronta, a sessao e aberta e o app vai para as notas.
 *
 * Serve para o primeiro acesso e para "Criar conta" na tela de login (outras contas ja existentes
 * nao sao tocadas). Se o app for fechado no meio, o login retoma a conta de onde parou
 * ([EXTRA_RESUME_ID]).
 */
class OnboardingActivity : AppCompatActivity() {

    private var accountId: String? = null
    private var prefsCache: SecurePrefs? = null

    /** Preferencias da conta em cadastro (so existem depois do passo 1). */
    private val prefs: SecurePrefs
        get() = prefsCache ?: SecurePrefs(this, accountId!!).also { prefsCache = it }

    private lateinit var steps: List<View>
    private lateinit var dots: List<View>
    private lateinit var stepLabel: TextView
    private lateinit var contactsList: android.widget.LinearLayout
    private lateinit var nameField: EditText
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var password2Field: EditText
    private lateinit var contactNameField: EditText
    private lateinit var contactPhoneField: EditText
    private lateinit var next1Button: View
    private var step = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_onboarding)

        accountId = savedInstanceState?.getString(STATE_ACCOUNT) ?: intent.getStringExtra(EXTRA_RESUME_ID)
        if (accountId != null && Accounts.get(this, accountId!!) == null) accountId = null

        steps = listOf(findViewById(R.id.step1), findViewById(R.id.step2), findViewById(R.id.step3))
        dots = listOf(findViewById(R.id.dot1), findViewById(R.id.dot2), findViewById(R.id.dot3))
        stepLabel = findViewById(R.id.tv_step)
        contactsList = findViewById(R.id.onb_contacts)
        nameField = findViewById(R.id.edit_name)
        emailField = findViewById(R.id.edit_email)
        passwordField = findViewById(R.id.edit_password)
        password2Field = findViewById(R.id.edit_password2)
        contactNameField = findViewById(R.id.edit_contact_name)
        contactPhoneField = findViewById(R.id.edit_contact_phone)
        next1Button = findViewById(R.id.btn_next1)

        // Conta ja criada (cadastro retomado ou tela recriada): os campos voltam preenchidos.
        accountId?.let { id ->
            Accounts.get(this, id)?.let {
                nameField.setText(it.name)
                emailField.setText(it.email)
            }
        }

        next1Button.setOnClickListener { submitStep1() }
        setupGoogle()
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

        if (accountId != null) renderContacts()
        showStep(savedInstanceState?.getInt(STATE_STEP) ?: if (accountId != null) 2 else 1)
    }

    // ---------- Continuar com o Google (so preenche nome e e-mail) ----------

    private fun setupGoogle() {
        // So ao criar a conta e so se o Client ID foi configurado (res/values/google.xml).
        if (accountId != null || !GoogleSignIn.isConfigured(this)) return
        findViewById<View>(R.id.google_group).visibility = View.VISIBLE
        findViewById<View>(R.id.btn_google).setOnClickListener {
            GoogleSignIn.request(this) { result ->
                when (result) {
                    is GoogleSignIn.Result.Ok -> {
                        if (result.profile.name.isNotBlank()) nameField.setText(result.profile.name)
                        emailField.setText(result.profile.email)
                        findViewById<View>(R.id.google_note).visibility = View.VISIBLE
                        passwordField.requestFocus() // falta so criar a senha do app
                    }
                    GoogleSignIn.Result.Cancelled -> Unit
                    GoogleSignIn.Result.Failed ->
                        Toast.makeText(this, R.string.google_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ACCOUNT, accountId)
        outState.putInt(STATE_STEP, step)
    }

    private fun showStep(n: Int) {
        step = n
        steps.forEachIndexed { i, v -> v.visibility = if (i == n - 1) View.VISIBLE else View.GONE }
        dots.forEachIndexed { i, v ->
            v.setBackgroundResource(if (i < n) R.drawable.bg_dot_on else R.drawable.bg_dot_off)
        }
        stepLabel.text = getString(R.string.step_indicator, n)
    }

    // ---------- Passo 1: dados da conta ----------

    private fun submitStep1() {
        val name = nameField.text.toString().trim()
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()
        val existing = accountId
        // Se a conta ja foi criada (a pessoa voltou ao passo 1), a senha so e trocada se digitar outra.
        val checkPassword = existing == null || password.isNotEmpty()
        when {
            name.isEmpty() -> nameField.error = getString(R.string.error_name)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> emailField.error = getString(R.string.error_email)
            checkPassword && password.length < Accounts.MIN_PASSWORD ->
                passwordField.error = getString(R.string.error_password_short)
            checkPassword && password != password2Field.text.toString() ->
                password2Field.error = getString(R.string.error_password_mismatch)
            else -> {
                next1Button.isEnabled = false
                // O hash da senha e lento de proposito: roda fora da thread da tela.
                inBackground({ saveAccount(existing, name, email, password) }) { id ->
                    next1Button.isEnabled = true
                    if (id == null) {
                        emailField.error = getString(R.string.error_email_taken)
                    } else {
                        accountId = id
                        passwordField.text.clear()
                        password2Field.text.clear()
                        renderContacts()
                        showStep(2)
                    }
                }
            }
        }
    }

    /** Cria a conta (ou atualiza a ja criada neste cadastro). Devolve o id, ou null se o e-mail ja esta em uso. */
    private fun saveAccount(existing: String?, name: String, email: String, password: String): String? {
        if (existing == null) return Accounts.create(this, name, email, password)
        if (!Accounts.updateProfile(this, existing, name, email)) return null
        if (password.isNotEmpty()) Accounts.setPassword(this, existing, password)
        return existing
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
        if (accountId == null) return
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
        val id = accountId ?: return
        prefs.onboardingDone = true
        Session.login(this, id) // quem acabou de se cadastrar ja entra logada
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    companion object {
        /** Id de uma conta cujo cadastro foi interrompido; o cadastro continua de onde parou. */
        const val EXTRA_RESUME_ID = "resume_account_id"
        private const val STATE_ACCOUNT = "account_id"
        private const val STATE_STEP = "step"
        private const val REQ_PERMS = 7
    }
}
