package com.twyn.sample

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.t4isb.t4fastid.helpers.EnrollFaceError
import com.t4isb.t4fastid.helpers.EnrollFaceResult
import com.t4isb.t4fastid.helpers.SDKStatusResult
import com.t4isb.t4fastid.listeners.EnrollListener
import com.t4isb.t4fastid.listeners.SDKStatusListener
import com.t4isb.t4fastid.ui.T4FastID

/**
 * Minimal integration sample for the Twyn Android SDK.
 *
 * Flow:
 *   1. Register the SDK listeners (companion object).
 *   2. Build the launch Intent with [T4FastID.createIntent].
 *   3. startActivity(intent).
 *   4. Receive the result through the listeners below.
 *
 * Enter the `personId` and `sdkKey` provided by Twyn for your integration.
 */
class MainActivity : AppCompatActivity(), EnrollListener, SDKStatusListener {

    private lateinit var inPersonId: EditText
    private lateinit var inSdkKey: EditText
    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        inPersonId = EditText(this).apply {
            hint = "personId"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        inSdkKey = EditText(this).apply {
            hint = "sdkKey"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        out = TextView(this)
        val btn = Button(this).apply {
            text = "Start enrollment"
            setOnClickListener { launchSdk() }
        }

        root.addView(inPersonId)
        root.addView(inSdkKey)
        root.addView(btn)
        root.addView(out)
        setContentView(root)

        // 1) Register listeners once (they live on the SDK's companion object).
        T4FastID.setEnrListener(this)
        T4FastID.setSDKStatusListener(this)

        out.text = "SDK version: ${T4FastID.getSDKVersion()}"
    }

    private fun launchSdk() {
        val personId = inPersonId.text.toString().trim()
        val sdkKey = inSdkKey.text.toString().trim() // optional
        if (personId.isEmpty()) {
            Toast.makeText(this, "personId is required", Toast.LENGTH_SHORT).show()
            return
        }
        val canal = "SICTM"
        val env = "dev" // "dev" or "prod"

        // 2) Base intent from the SDK factory + optional extras.
        val intent = T4FastID.createIntent(this, sdkKey, personId, canal, env).apply {
            putExtra("language", "en")
            putExtra("requestSteps", arrayOf("T4_FACE"))
            putExtra("iBeta", true)
        }

        // 3) Launch. Results arrive via the listeners.
        startActivity(intent)
    }

    // ── EnrollListener ────────────────────────────────────────────────

    override fun onEnrollFaceCompleted(result: EnrollFaceResult?) {
        out.text = "Liveness: ${result?.livenessStatus}\nTCN: ${result?.tcn}"
        Toast.makeText(this, "Enrollment completed", Toast.LENGTH_SHORT).show()
    }

    override fun onEnrollFaceError(error: EnrollFaceError?) {
        out.text = "Enrollment error ${error?.error_code}: ${error?.error_message}"
    }

    override fun onEnrollFingerCompleted(isAlive: Boolean?) {}
    override fun onEnrollFingerError(error: String?) {}
    override fun onEnrollDocumentCompleted(isValid: Boolean?) {}
    override fun onEnrollDocumentError(error: String?) {}
    override fun onUserInfoCompleted(type: String?) {}
    override fun onUserInfoError(error: String?, type: String?) {}

    // ── SDKStatusListener ─────────────────────────────────────────────

    override fun onSDKStatusChanged(status: SDKStatusResult?) {
        out.append("\nstatus: ${status?.status_code} ${status?.status_message ?: ""}")
    }
}
