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
    val diagnostics = PageDiagnostics<GeckoSession, WebExtension>()
    val runtime: GeckoRuntime by lazy {
        // Private configuration file prevents external debug configuration injection.
        val config = File(noBackupFilesDir, "gecko.yaml")
        assets.open("gecko.yaml").use { input -> config.outputStream().use { input.copyTo(it) } }
        GeckoRuntime.create(this, GeckoRuntimeSettings.Builder()
            .configFilePath(config.absolutePath).javaScriptEnabled(true)
            // Gecko has its own root store; Android's network security XML covers native probes.
            // Import installed Android CAs without granting certificate or hostname exceptions.
            .enterpriseRootsEnabled(true)
            .remoteDebuggingEnabled(false).aboutConfigEnabled(false)
            .extensionsWebAPIEnabled(false).consoleOutput(false).build()).also { it.notifyTelemetryPrefChanged(false) }
    }
}
