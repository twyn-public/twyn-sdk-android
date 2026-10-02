package com.twyn.sample

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import com.t4isb.t4fastid.helpers.EnrollBackendError
import com.t4isb.t4fastid.helpers.EnrollBackendResult
import com.t4isb.t4fastid.helpers.EnrollFaceError
import com.t4isb.t4fastid.helpers.EnrollFaceResult
import com.t4isb.t4fastid.helpers.SDKStatusResult
import com.t4isb.t4fastid.listeners.BackendTransactionListener
import com.t4isb.t4fastid.listeners.EnrollListener
import com.t4isb.t4fastid.listeners.SDKStatusListener
import com.t4isb.t4fastid.ui.T4FastID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Cores do messagebox de resultado (verde / vermelho / âmbar / cinza).
private val COLOR_APPROVED = Color.parseColor("#16A34A")
private val COLOR_REJECTED = Color.parseColor("#DC2626")
private val COLOR_REVIEW = Color.parseColor("#D97706")
private val COLOR_UNKNOWN = Color.parseColor("#6B7280")

/**
 * Minimal host / test client for the T4FastID SDK.
 *
 * It demonstrates the full public contract:
 *   1. Register the static listeners (enroll / status / backend).
 *   2. Build the launch Intent with T4FastID.createIntent(...) plus optional extras.
 *   3. Start the SDK Activity and render every callback in an on-screen log.
 *
 * The SDK runs as a separate Activity and reports results through the static
 * listeners set below — there is no startActivityForResult round-trip to handle.
 */
class DemoLauncherActivity : AppCompatActivity(),
    EnrollListener, SDKStatusListener, BackendTransactionListener {

    private lateinit var inPersonId: EditText
    private lateinit var inCanal: EditText
    private lateinit var inSteps: EditText
    private lateinit var inSdkKey: EditText
    private lateinit var inTcn: EditText
    private lateinit var inTot: EditText
    private lateinit var spEnv: Spinner
    private lateinit var spLang: Spinner
    private lateinit var inGatewayUrl: EditText
    private lateinit var inSentinelUrl: EditText

    private lateinit var txtLog: TextView
    private lateinit var lblResult: TextView
    private lateinit var imgFace: ImageView
    private lateinit var resultCard: LinearLayout

    // Endpoints are configured in the form (see the "Endpoints" section). Placeholders by
    // default — set them to YOUR deployment. The SDK's own liveness transport is baked into
    // the SDK binary; these are only for the audit read + the Sentinel scan.
    private fun gatewayBase(): String =
        inGatewayUrl.text.toString().trim().trimEnd('/').ifEmpty { "https://your-gateway.example.com" }
    private fun sentinelBase(): String =
        inSentinelUrl.text.toString().trim().trimEnd('/').ifEmpty { "https://your-sentinel.example.com" }
    // Marca-d'água da última decisão existente ANTES do liveness. Usada para esperar a decisão
    // NOVA desta sessão (evita mostrar a decisão de uma transação anterior).
    @Volatile private var baselineDecisionId: String? = null
    @Volatile private var baselineCaptured = false
    private var resultDialog: AlertDialog? = null
    private val io = Executors.newSingleThreadExecutor()

    private val ts get() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo_launcher)

        inPersonId = findViewById(R.id.inPersonId)
        inCanal = findViewById(R.id.inCanal)
        inSteps = findViewById(R.id.inSteps)
        inSdkKey = findViewById(R.id.inSdkKey)
        inTcn = findViewById(R.id.inTcn)
        inTot = findViewById(R.id.inTot)
        spEnv = findViewById(R.id.spEnv)
        spLang = findViewById(R.id.spLang)
        inGatewayUrl = findViewById(R.id.inGatewayUrl)
        inSentinelUrl = findViewById(R.id.inSentinelUrl)
        txtLog = findViewById(R.id.txtLog)
        lblResult = findViewById(R.id.lblResult)
        imgFace = findViewById(R.id.imgFace)
        resultCard = findViewById(R.id.resultCard)

        // env accepted by the SDK: "dev" or "prod"
        spEnv.adapter = simpleAdapter(listOf("dev", "prod"))
        spLang.adapter = simpleAdapter(listOf("pt", "en"))

        findViewById<TextView>(R.id.lblSdkVersion).text = "SDK: " + safeSdkVersion()

        // Register listeners once. They live in the SDK's companion object and are
        // invoked while the SDK Activity is in the foreground.
        T4FastID.setEnrListener(this)
        T4FastID.setSDKStatusListener(this)
        T4FastID.setEnrBackendListener(this)

        findViewById<Button>(R.id.btnStart).setOnClickListener { launchSdk() }
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            txtLog.text = ""
            resultCard.visibility = LinearLayout.GONE
        }

        log("Pronto. Preencha os parâmetros e toque em Iniciar.")

        // Sentinel (camada extra · device integrity): dispara o scan+submit logo no launcher
        // para validar o fluxo end-to-end sem depender do liveness. O report fica em
        // GlobalVars.sentinelReport e o liveness também anexa no extend. Init e scan em
        // background (não travam a tela).
        try {
            val sentinelUrl = sentinelBase()
            com.t4isb.t4fastid.helpers.SentinelIntegrator.initAsync(applicationContext, sentinelUrl, allowCleartext = false)
            com.t4isb.t4fastid.helpers.SentinelIntegrator.scanAndSubmit(this, deepScan = false)
            log("→ Sentinel scan disparado (device integrity)")
        } catch (e: Exception) {
            log("✖ Sentinel falhou: ${e.message}")
        }
    }

    private fun launchSdk() {
        val personId = inPersonId.text.toString().trim()
        if (personId.isEmpty()) {
            Toast.makeText(this, "Person ID é obrigatório", Toast.LENGTH_SHORT).show()
            return
        }
        val canal = inCanal.text.toString().trim().ifEmpty { "TWYN" }
        val env = spEnv.selectedItem.toString()
        val language = spLang.selectedItem.toString()
        val sdkKey = inSdkKey.text.toString().trim()
        val steps = inSteps.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("T4_FACE") }
            .toTypedArray()

        // 1) Base intent from the SDK factory.
        val intent = T4FastID.createIntent(this, sdkKey, personId, canal, env).apply {
            // 2) Optional extras the SDK reads from its bundle.
            putExtra("language", language)
            putExtra("requestSteps", steps)
            putExtra("iBeta", true)
            inTcn.text.toString().trim().takeIf { it.isNotEmpty() }?.let { putExtra("tcn", it) }
            inTot.text.toString().trim().takeIf { it.isNotEmpty() }?.let { putExtra("tot", it) }
        }

        log("→ Iniciando SDK | personId=$personId canal=$canal env=$env lang=$language steps=${steps.joinToString()}")
        // Guarda a decisão atual antes do liveness para, no fim, esperar a decisão nova.
        captureBaselineDecision()
        try {
            startActivity(intent)
        } catch (e: Exception) {
            log("‼ Falha ao iniciar Activity do SDK: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------
    // EnrollListener
    // ---------------------------------------------------------------------

    override fun onEnrollFaceCompleted(enrollFaceResult: EnrollFaceResult?) = onUi {
        val tcn = enrollFaceResult?.tcn
        val alive = enrollFaceResult?.livenessStatus
        val face = enrollFaceResult?.croppedFace
        log("✔ onEnrollFaceCompleted | tcn=$tcn isAlive=$alive faceLen=${face?.length ?: 0}")

        resultCard.visibility = LinearLayout.VISIBLE
        lblResult.text = "TCN: ${tcn ?: "-"}\nLiveness (isAlive): ${alive ?: "-"}"
        showFace(face)

        // Messagebox com a decisão do gateway (busca assíncrona na API de auditoria).
        fetchDecisionAndShow(tcn, alive == true)
    }

    override fun onEnrollFaceError(enrollFaceError: EnrollFaceError?) = onUi {
        val code = enrollFaceError?.error_code
        val msg = enrollFaceError?.error_message
        log("✖ onEnrollFaceError | code=$code msg=$msg")
        showResultDialog(
            status = "REPROVADO",
            subtitle = "Não foi possível concluir o liveness",
            headerColor = COLOR_REJECTED,
            symbol = "✕",
            rows = listOf(
                "Resultado" to "Erro no liveness",
                "Código" to (code?.toString() ?: "-"),
                "Detalhe" to (msg ?: "-")
            )
        )
    }

    override fun onEnrollFingerCompleted(isAlive: Boolean?) = onUi {
        log("✔ onEnrollFingerCompleted | isAlive=$isAlive")
    }

    override fun onEnrollFingerError(error: String?) = onUi {
        log("✖ onEnrollFingerError | $error")
    }

    override fun onEnrollDocumentCompleted(isValid: Boolean?) = onUi {
        log("✔ onEnrollDocumentCompleted | isValid=$isValid")
    }

    override fun onEnrollDocumentError(error: String?) = onUi {
        log("✖ onEnrollDocumentError | $error")
    }

    override fun onUserInfoCompleted(type: String?) = onUi {
        log("✔ onUserInfoCompleted | step=$type")
    }

    override fun onUserInfoError(error: String?, type: String?) = onUi {
        log("✖ onUserInfoError | step=$type msg=$error")
    }

    // ---------------------------------------------------------------------
    // SDKStatusListener
    // ---------------------------------------------------------------------

    override fun onSDKStatusChanged(statusResult: SDKStatusResult?) = onUi {
        log("● onSDKStatusChanged | code=${statusResult?.status_code} msg=${statusResult?.status_message}")
    }

    // ---------------------------------------------------------------------
    // BackendTransactionListener
    // ---------------------------------------------------------------------

    override fun onTransactionCompleted(enrollBackendResult: EnrollBackendResult?) = onUi {
        log("✔ onTransactionCompleted | wkf=${enrollBackendResult?.workflowInstanceId} tot=${enrollBackendResult?.tot}")
    }

    override fun onTransactionFailed(enrollBackendError: EnrollBackendError?) = onUi {
        log("✖ onTransactionFailed | code=${enrollBackendError?.error_code} msg=${enrollBackendError?.error_message}")
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun showFace(base64: String?) {
        if (base64.isNullOrBlank()) {
            imgFace.visibility = ImageView.GONE
            return
        }
        try {
            // Tolerate a data-URI prefix if present.
            val clean = base64.substringAfterLast(",")
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                imgFace.setImageBitmap(bmp)
                imgFace.visibility = ImageView.VISIBLE
            } else {
                imgFace.visibility = ImageView.GONE
            }
        } catch (e: Exception) {
            imgFace.visibility = ImageView.GONE
            log("(não consegui decodificar a face: ${e.message})")
        }
    }

    // ---------------------------------------------------------------------
    // Decisão do gateway (API de auditoria) + messagebox de resultado
    // ---------------------------------------------------------------------

    /** Captura a decisão existente antes do liveness, para não confundir com a nova. */
    private fun captureBaselineDecision() {
        baselineCaptured = false
        baselineDecisionId = null
        io.execute {
            try {
                baselineDecisionId = fetchLatestDecision()?.optString("_id")
            } catch (_: Exception) {
            } finally {
                baselineCaptured = true
            }
        }
    }

    /** Espera a decisão NOVA desta sessão e abre o messagebox. */
    private fun fetchDecisionAndShow(tcn: String?, alive: Boolean) {
        io.execute {
            var dec: JSONObject? = null
            val deadline = System.currentTimeMillis() + 12_000
            while (System.currentTimeMillis() < deadline) {
                try {
                    val latest = fetchLatestDecision()
                    if (latest != null) {
                        val id = latest.optString("_id")
                        val isNew = !baselineCaptured || baselineDecisionId == null || id != baselineDecisionId
                        if (isNew) {
                            dec = latest
                            break
                        }
                    }
                } catch (_: Exception) {
                }
                Thread.sleep(1000)
            }
            val finalDec = dec
            onUi { showDecisionDialog(tcn, alive, finalDec) }
        }
    }

    private fun fetchLatestDecision(): JSONObject? {
        val conn = URL("${gatewayBase()}/api/audit/decisions?limit=1").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(body).optJSONArray("items") ?: return null
            if (items.length() == 0) return null
            return items.optJSONObject(0)
        } finally {
            conn.disconnect()
        }
    }

    private fun showDecisionDialog(tcn: String?, alive: Boolean, dec: JSONObject?) {
        val decision = dec?.optString("decision", "")?.takeIf { it.isNotEmpty() } ?: "INDEFINIDA"
        val risk = dec?.optDouble("riskScore", Double.NaN)?.takeIf { !it.isNaN() }
        val sentinel = dec?.optJSONObject("signals")?.optJSONObject("sentinel")?.optString("decision", "")
        val reasons = dec?.optJSONArray("reasonCodes")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
        }.orEmpty()

        var status = "INDEFINIDO"
        var symbol = "?"
        var color = COLOR_UNKNOWN
        var subtitle = "Não foi possível ler a decisão do gateway"
        when (decision) {
            "APPROVED" -> { status = "APROVADO"; symbol = "✓"; color = COLOR_APPROVED; subtitle = "Transação autorizada" }
            "REJECTED" -> { status = "REPROVADO"; symbol = "✕"; color = COLOR_REJECTED; subtitle = "Transação bloqueada" }
            "CHALLENGE" -> { status = "EM ANÁLISE"; symbol = "!"; color = COLOR_REVIEW; subtitle = "Revisão necessária" }
        }

        val rows = mutableListOf<Pair<String, String>>()
        rows += "Liveness (SDK)" to if (alive) "Sim" else "Não"
        rows += "Decisão do gateway" to decision
        if (risk != null) rows += "Risco" to String.format(Locale.US, "%.3f", risk)
        if (!sentinel.isNullOrEmpty()) rows += "Sentinel" to sentinel
        rows += "Motivos" to if (reasons.isEmpty()) "—" else reasons.joinToString(", ")
        if (!tcn.isNullOrBlank()) rows += "TCN" to tcn

        showResultDialog(status, subtitle, color, symbol, rows)
    }

    private fun showResultDialog(
        status: String,
        subtitle: String,
        headerColor: Int,
        symbol: String,
        rows: List<Pair<String, String>>
    ) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_result, null)
        view.findViewById<View>(R.id.dlgHeader).background?.setTint(headerColor)
        view.findViewById<TextView>(R.id.dlgSymbol).text = symbol
        view.findViewById<TextView>(R.id.dlgStatus).text = status
        view.findViewById<TextView>(R.id.dlgSubtitle).text = subtitle

        val details = view.findViewById<LinearLayout>(R.id.dlgDetails)
        details.removeAllViews()
        for ((label, value) in rows) details.addView(buildResultRow(label, value))

        resultDialog?.dismiss()
        resultDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        resultDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<Button>(R.id.dlgClose).setOnClickListener { resultDialog?.dismiss() }
        resultDialog?.show()
    }

    private fun buildResultRow(label: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, dp(7))
        }
        val lbl = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valTxt = TextView(this).apply {
            text = value
            setTextColor(Color.parseColor("#111827"))
            textSize = 13f
            gravity = Gravity.END
            setTypeface(typeface, Typeface.BOLD)
        }
        row.addView(lbl)
        row.addView(valTxt)
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun safeSdkVersion(): String = try {
        T4FastID.getSDKVersion()
    } catch (e: Throwable) {
        "?"
    }

    private fun simpleAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

    private inline fun onUi(crossinline block: () -> Unit) {
        runOnUiThread { block() }
    }

    private fun log(line: String) {
        val existing = txtLog.text?.toString().orEmpty()
        txtLog.text = "[$ts] $line\n$existing"
    }
}
