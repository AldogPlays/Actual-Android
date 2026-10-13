package app.actualbudget.wrapper

import android.app.Application
import org.mozilla.geckoview.*
import java.io.File

class BudgetApplication : Application() {
    var session: GeckoSession? = null
    var active: Server? = null
    var location: String? = null
    var canBack = false
    var state: GeckoSession.SessionState? = null
    var diagnostics: org.json.JSONObject? = null
    var extension: WebExtension? = null
    val runtime: GeckoRuntime by lazy {
        // Private configuration file prevents external debug configuration injection.
        val config = File(noBackupFilesDir, "gecko.yaml")
        assets.open("gecko.yaml").use { input -> config.outputStream().use { input.copyTo(it) } }
        GeckoRuntime.create(this, GeckoRuntimeSettings.Builder()
            .configFilePath(config.absolutePath).javaScriptEnabled(true)
            .remoteDebuggingEnabled(false).aboutConfigEnabled(false)
            .extensionsWebAPIEnabled(false).consoleOutput(false).build()).also { it.notifyTelemetryPrefChanged(false) }
    }
}
