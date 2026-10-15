package br.unesp.anote

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Painel oculto de emergencia (Figura 11). So e aberto pelo gatilho da "nota falsa".
 *
 * Medidas de discricao:
 *  - FLAG_SECURE: impede captura de tela e esconde o conteudo na lista de recentes;
 *  - fecha sozinho quando a tela do celular e apagada (ACTION_SCREEN_OFF);
 *  - fecha em onStop (ao sair do app) e nao aparece nos recentes (excludeFromRecents).
 */
class EmergencyActivity : AppCompatActivity() {

    private enum class Pending { SOS, RECORD, ONE }

    private lateinit var prefs: SecurePrefs
    private lateinit var contactsContainer: LinearLayout
    private lateinit var noContacts: TextView
    private lateinit var recordTitle: TextView
    private lateinit var recordSubtitle: TextView
    private lateinit var recordIcon: ImageView

    private var askingPermission = false
    private var pending: Pending? = null
    private var pendingPhone: String? = null

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
        setContentView(R.layout.activity_emergency)

        prefs = SecurePrefs(this)
        contactsContainer = findViewById(R.id.contacts_container)
        noContacts = findViewById(R.id.tv_no_contacts)
        recordTitle = findViewById(R.id.tv_record_title)
        recordSubtitle = findViewById(R.id.tv_record_subtitle)
        recordIcon = findViewById(R.id.img_record)

        findViewById<View>(R.id.btn_close).setOnClickListener { finishAndRemoveTask() }
        findViewById<View>(R.id.card_sos).setOnClickListener { requestThen(Pending.SOS) }
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
        findViewById<View>(R.id.tv_change_trigger).setOnClickListener { showTriggerDialog() }

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        renderContacts()
    }

    override fun onResume() {
        super.onResume()
        renderRecording()
    }

    override fun onStop() {
        super.onStop()
        // Se o app sair da frente (por exemplo, o agressor pegou o celular), o painel some.
        // Excecao: o pedido de permissoes do sistema tambem dispara onStop.
        if (!askingPermission) finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOffReceiver) }
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
                AlertDialog.Builder(this)
                    .setMessage(R.string.remove_contact)
                    .setPositiveButton(R.string.remove) { _, _ ->
                        prefs.removeContact(index)
                        renderContacts()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            contactsContainer.addView(row)
        }
    }

    private fun showAddContactDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null)
        val nameField = view.findViewById<EditText>(R.id.dialog_name)
        val phoneField = view.findViewById<EditText>(R.id.dialog_phone)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_contact)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameField.text.toString().trim()
                val phone = phoneField.text.toString().filter { it.isDigit() || it == '+' }
                if (name.isNotEmpty() && phone.length >= 8) {
                    prefs.addContact(TrustedContact(name, phone))
                    renderContacts()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTriggerDialog() {
        val field = EditText(this).apply {
            setText(prefs.triggerTitle)
            hint = getString(R.string.trigger_dialog_hint)
            setSingleLine()
        }
        val container = LinearLayout(this).apply {
            setPadding(64, 24, 64, 0)
            addView(field, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.trigger_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val t = field.text.toString().trim()
                if (t.isNotEmpty()) prefs.triggerTitle = t
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
            askingPermission = true
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        askingPermission = false
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
