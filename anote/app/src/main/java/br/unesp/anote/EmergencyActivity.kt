package br.unesp.anote

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Painel oculto de emergencia (Figura 11). So e aberto pelo gatilho da "nota falsa".
 * A protecao (sem captura de tela, fecha ao apagar a tela ou sair do app) vem de [GuardedActivity].
 *
 * O menu (⋮) reune as configuracoes de emergencia: contatos, permissoes, ajuda e titulo de acesso.
 */
class EmergencyActivity : GuardedActivity() {

    private enum class Pending { SOS, RECORD, ONE }

    private lateinit var prefs: SecurePrefs
    private lateinit var contactsContainer: LinearLayout
    private lateinit var noContacts: TextView
    private lateinit var recordTitle: TextView
    private lateinit var recordSubtitle: TextView
    private lateinit var recordIcon: ImageView
    private lateinit var sosTitle: TextView
    private lateinit var sosSubtitle: TextView

    private var pending: Pending? = null
    private var pendingPhone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.isActive(this)) { // sessao encerrada: nao ha conta para mostrar
            finishAndRemoveTask()
            return
        }
        setContentView(R.layout.activity_emergency)

        prefs = SecurePrefs(this)
        contactsContainer = findViewById(R.id.contacts_container)
        noContacts = findViewById(R.id.tv_no_contacts)
        recordTitle = findViewById(R.id.tv_record_title)
        recordSubtitle = findViewById(R.id.tv_record_subtitle)
        recordIcon = findViewById(R.id.img_record)
        sosTitle = findViewById(R.id.tv_sos_title)
        sosSubtitle = findViewById(R.id.tv_sos_subtitle)

        findViewById<View>(R.id.btn_close).setOnClickListener { finishAndRemoveTask() }
        findViewById<View>(R.id.btn_menu).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.card_sos).setOnClickListener {
            if (SosService.isSosActive) confirmStopSos() else requestThen(Pending.SOS)
        }
        findViewById<View>(R.id.card_record).setOnClickListener {
            if (SosService.isRecording) {
                startService(Intent(this, SosService::class.java).setAction(SosService.ACTION_STOP_RECORD))
                SosService.isRecording = false
                renderRecording()
            } else {
                requestThen(Pending.RECORD)
            }
        }
        findViewById<View>(R.id.tv_ddm_open).setOnClickListener { openRoute(null) }
        findViewById<View>(R.id.chip_walk).setOnClickListener { openRoute("walking") }
        findViewById<View>(R.id.chip_car).setOnClickListener { openRoute("driving") }
        findViewById<View>(R.id.chip_bus).setOnClickListener { openRoute("transit") }
        findViewById<View>(R.id.tv_add_contact).setOnClickListener { showAddContactDialog() }

        renderContacts()
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar da tela de contatos a lista pode ter mudado.
        renderContacts()
        renderRecording()
        renderSos()
        showFailureIfAny()
        // Se um envio terminar com o painel aberto, avisa na hora.
        SosService.onReport = { _ ->
            renderSos()
            showFailureIfAny()
        }
    }

    override fun onPause() {
        SosService.onReport = null
        super.onPause()
    }

    // ---------- Menu ----------

    private fun showMenu(anchor: View) {
        Ui.showMenu(
            anchor, dark = true,
            entries = listOf(
                Ui.MenuEntry(R.drawable.ic_people, R.string.menu_contacts) {
                    keepOnStop = true // troca de tela dentro do app: o painel nao deve se fechar
                    startActivity(Intent(this, ContactsActivity::class.java))
                },
                Ui.MenuEntry(R.drawable.ic_mic, R.string.menu_recordings) {
                    keepOnStop = true
                    startActivity(Intent(this, RecordingsActivity::class.java))
                },
                Ui.MenuEntry(R.drawable.ic_shield, R.string.menu_permissions) {
                    MenuDialogs.showPermissions(this) { keepOnStop = true }
                },
                Ui.MenuEntry(R.drawable.ic_send, R.string.menu_shortcut) { showShortcutDialog() },
                Ui.MenuEntry(R.drawable.ic_lock, R.string.menu_app_lock) {
                    MenuDialogs.showAppLock(this, dark = true)
                },
                Ui.MenuEntry(R.drawable.ic_message, R.string.menu_sos_message) { showSosMessageDialog() },
                Ui.MenuEntry(R.drawable.ic_info, R.string.menu_guide) {
                    keepOnStop = true
                    startActivity(
                        Intent(this, GuideActivity::class.java)
                            .putExtra(GuideActivity.EXTRA_FROM_MENU, true)
                    )
                },
                Ui.MenuEntry(R.drawable.ic_help, R.string.menu_help) {
                    MenuDialogs.showHelp(this)
                },
                Ui.MenuEntry(R.drawable.ic_key, R.string.menu_trigger) { showTriggerDialog() }
            )
        )
    }

    // ---------- Interface ----------

    private fun renderRecording() {
        val rec = SosService.isRecording
        recordTitle.setText(if (rec) R.string.record_stop else R.string.record_start)
        recordSubtitle.setText(if (rec) R.string.record_running else R.string.record_subtitle)
        recordIcon.setColorFilter(
            ContextCompat.getColor(this, if (rec) R.color.danger else R.color.purple_light)
        )
    }

    /** Cartao SOS: mostra "ativo" enquanto a localizacao esta sendo reenviada, com o ultimo resultado. */
    private fun renderSos() {
        if (!SosService.isSosActive) {
            sosTitle.setText(R.string.sos_title)
            sosSubtitle.setText(R.string.sos_subtitle)
            return
        }
        sosTitle.setText(R.string.sos_active_title)
        val report = prefs.lastReport()
        sosSubtitle.text = if (report == null) {
            getString(R.string.sos_active_subtitle, SosService.REPEAT_MINUTES)
        } else {
            val time = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(report.time))
            getString(
                R.string.sos_active_last, SosService.REPEAT_MINUTES, time,
                report.sent.size, report.sent.size + report.failed.size
            )
        }
    }

    private fun confirmStopSos() {
        Ui.show(
            Ui.dialog(this, dark = true)
                .setMessage(R.string.sos_stop_confirm)
                .setPositiveButton(R.string.sos_stop) { _, _ ->
                    startService(Intent(this, SosService::class.java).setAction(SosService.ACTION_STOP_SOS))
                    SosService.isSosActive = false
                    renderSos()
                }
                .setNegativeButton(R.string.cancel, null),
            dark = true
        )
    }

    /** Se o ultimo envio teve contatos sem receber, mostra quem falhou e oferece tentar de novo. */
    private fun showFailureIfAny() {
        if (isFinishing || !prefs.reportUnseen) return
        val report = prefs.lastReport() ?: return
        if (report.failed.isEmpty()) return
        prefs.reportUnseen = false
        val message = buildString {
            append(getString(R.string.sos_fail_body, report.failed.joinToString("\n") { "• ${it.name}" }))
            if (report.sent.isNotEmpty()) {
                append("\n\n").append(getString(R.string.sos_fail_sent, report.sent.joinToString(", ") { it.name }))
            }
        }
        Ui.show(
            Ui.dialog(this, dark = true)
                .setTitle(R.string.sos_fail_title)
                .setMessage(message)
                .setPositiveButton(R.string.sos_retry) { _, _ ->
                    report.failed.forEach { contact ->
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, SosService::class.java)
                                .setAction(SosService.ACTION_SOS_ONE)
                                .putExtra(SosService.EXTRA_PHONE, contact.phone)
                        )
                    }
                }
                .setNegativeButton(R.string.close, null),
            dark = true
        )
    }

    /** Texto do SMS de SOS: a pessoa pode trocar a frase; o link do mapa e sempre acrescentado no fim. */
    private fun showSosMessageDialog() {
        val builder = Ui.dialog(this, dark = true)
        val field = EditText(builder.context).apply {
            setText(prefs.customMessage)
            hint = getString(R.string.sos_message_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(100))
            minLines = 2
        }
        val container = LinearLayout(builder.context).apply {
            setPadding(64, 24, 64, 0)
            addView(field, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        Ui.show(
            builder
                .setTitle(R.string.sos_message_title)
                .setMessage(R.string.sos_message_help)
                .setView(container)
                .setPositiveButton(R.string.save) { _, _ -> prefs.customMessage = field.text.toString() }
                .setNeutralButton(R.string.sos_message_reset) { _, _ -> prefs.customMessage = "" }
                .setNegativeButton(R.string.cancel, null),
            dark = true
        )
    }

    private fun showShortcutDialog() {
        val on = prefs.quickShortcut
        Ui.show(
            Ui.dialog(this, dark = true)
                .setTitle(R.string.shortcut_title)
                .setMessage(
                    getString(R.string.shortcut_body) + "\n\n" +
                        getString(if (on) R.string.shortcut_state_on else R.string.shortcut_state_off)
                )
                .setPositiveButton(if (on) R.string.shortcut_turn_off else R.string.shortcut_turn_on) { _, _ ->
                    prefs.quickShortcut = !on
                }
                .setNegativeButton(R.string.close, null),
            dark = true
        )
    }

    private fun renderContacts() {
        contactsContainer.removeAllViews()
        val contacts = prefs.contacts()
        noContacts.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
        contacts.forEachIndexed { index, contact ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_contact, contactsContainer, false)
            row.findViewById<TextView>(R.id.contact_initial).text =
                contact.name.firstOrNull()?.uppercase() ?: "?"
            row.findViewById<TextView>(R.id.contact_name).text = contact.name
            row.findViewById<TextView>(R.id.contact_phone).text = contact.phone
            row.findViewById<View>(R.id.contact_dispatch).setOnClickListener {
                requestThen(Pending.ONE, contact.phone)
            }
            row.setOnLongClickListener {
                Ui.show(
                    Ui.dialog(this, dark = true)
                        .setMessage(R.string.remove_contact)
                        .setPositiveButton(R.string.remove) { _, _ ->
                            prefs.removeContact(index)
                            renderContacts()
                        }
                        .setNegativeButton(R.string.cancel, null),
                    dark = true
                )
                true
            }
            contactsContainer.addView(row)
        }
    }

    private fun showAddContactDialog() {
        val builder = Ui.dialog(this, dark = true)
        // Infla com o contexto do dialogo para os campos usarem as cores escuras.
        val view = LayoutInflater.from(builder.context).inflate(R.layout.dialog_contact, null)
        val nameField = view.findViewById<EditText>(R.id.dialog_name)
        val phoneField = view.findViewById<EditText>(R.id.dialog_phone)
        Ui.show(
            builder
                .setTitle(R.string.add_contact)
                .setView(view)
                .setPositiveButton(R.string.save) { _, _ ->
                    val name = nameField.text.toString().trim()
                    val phone = phoneField.text.toString().filter { it.isDigit() || it == '+' }
                    if (name.isNotEmpty() && phone.length >= 10) {
                        prefs.addContact(TrustedContact(name, phone))
                        renderContacts()
                    } else {
                        Toast.makeText(this, R.string.error_contact, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null),
            dark = true
        )
    }

    private fun showTriggerDialog() {
        val builder = Ui.dialog(this, dark = true)
        val field = EditText(builder.context).apply {
            setText(prefs.triggerTitle)
            hint = getString(R.string.trigger_dialog_hint)
            setSingleLine()
        }
        val container = LinearLayout(builder.context).apply {
            setPadding(64, 24, 64, 0)
            addView(field, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        Ui.show(
            builder
                .setTitle(R.string.trigger_dialog_title)
                .setMessage(R.string.trigger_dialog_help)
                .setView(container)
                .setPositiveButton(R.string.save) { _, _ ->
                    val t = field.text.toString().trim()
                    if (t.isNotEmpty()) {
                        prefs.triggerTitle = t
                        // O padrao "..." e facil de adivinhar: lembra a pessoa de escolher um so dela.
                        if (SecurePrefs.normalize(t) == SecurePrefs.DEFAULT_TRIGGER) {
                            Toast.makeText(this, R.string.trigger_weak, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null),
            dark = true
        )
    }

    // ---------- Permissoes e acoes ----------

    private fun requestThen(action: Pending, phone: String? = null) {
        if (action != Pending.RECORD && prefs.contacts().isEmpty()) {
            Toast.makeText(this, R.string.need_contacts, Toast.LENGTH_LONG).show()
            return
        }
        val needed = mutableListOf<String>()
        if (action == Pending.RECORD) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        } else {
            // No Android 12+ a localizacao precisa e a aproximada sao pedidas juntas.
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            needed.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        pending = action
        pendingPhone = phone
        if (missing.isEmpty()) {
            runPending()
        } else {
            keepOnStop = true // o pedido de permissao do sistema nao deve fechar o painel
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        keepOnStop = false
        if (requestCode == REQ_PERMS) runPending() // o servico usa so o que foi concedido
    }

    private fun runPending() {
        val intent = Intent(this, SosService::class.java)
        when (pending) {
            Pending.SOS -> intent.action = SosService.ACTION_SOS
            Pending.ONE -> {
                intent.action = SosService.ACTION_SOS_ONE
                intent.putExtra(SosService.EXTRA_PHONE, pendingPhone)
            }
            Pending.RECORD -> {
                intent.action = SosService.ACTION_RECORD
                SosService.isRecording = true
                renderRecording()
                // Se o servico nao conseguiu iniciar (ex.: sem permissao), o rotulo volta ao normal.
                window.decorView.postDelayed({ renderRecording() }, 1500)
            }
            null -> return
        }
        ContextCompat.startForegroundService(this, intent)
        pending = null
        pendingPhone = null
    }

    // ---------- Rede de apoio ----------

    /**
     * Abre o app de mapas com a rota ate a Delegacia de Defesa da Mulher mais proxima da
     * posicao atual. [mode] pode ser walking, driving ou transit (null = so mostrar no mapa).
     */
    private fun openRoute(mode: String?) {
        val query = Uri.encode("Delegacia de Defesa da Mulher")
        val uri = if (mode == null) {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
        } else {
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$query&travelmode=$mode")
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.ddm_open_map, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQ_PERMS = 42
    }
}
