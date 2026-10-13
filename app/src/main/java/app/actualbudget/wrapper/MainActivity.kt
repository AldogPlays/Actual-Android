package app.actualbudget.wrapper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.*
import android.os.*
import android.view.*
import android.widget.*
import org.json.JSONObject
import org.mozilla.geckoview.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val app get() = application as BudgetApplication
    private lateinit var store: ConfigStore
    private var servers = emptyList<Server>()
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var busy = false
    private var browsing = false
    private var canBack = false
    private var view: GeckoView? = null
    private var registered = false
    private val approved = mutableSetOf<String>()
    private var filePrompt: GeckoSession.PromptDelegate.FilePrompt? = null
    private var fileResult: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = null
    private var download: WebResponse? = null
    private val networkCheck = Runnable { checkConnection() }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleCheck()
        override fun onLost(network: Network) = scheduleCheck()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = scheduleCheck()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT
        store = ConfigStore(this)
        try { servers = store.read() } catch (_: Exception) {
            settings("Stored configuration could not be decrypted. Re-enter servers; browser data has been preserved."); return
        }
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { back() }
        if (app.session == null && savedInstanceState?.getString("active") != null) {
            val restored = runCatching { Server.parse(savedInstanceState.getString("active")!!) }.getOrNull()
            if (restored != null && restored in servers) {
                app.active = restored
                app.session = GeckoSession().apply { open(app.runtime) }
                val state = GeckoSession.SessionState.fromString(savedInstanceState.getString("session"))
                bindSession()
                if(state != null) app.session!!.restoreState(state) else app.session!!.loadUri(restored.url)
            }
        }
        if (app.session != null) bindSession()
        when {
            intent.action == "app.actualbudget.wrapper.SETTINGS" -> settings()
            servers.isEmpty() -> settings()
            app.session != null -> showBrowser()
            else -> connect()
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if(intent.action == "app.actualbudget.wrapper.SETTINGS") settings() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("active", app.active?.url)
        outState.putString("session", app.state?.toString()?.takeIf { it.toByteArray().size < 256 * 1024 })
        super.onSaveInstanceState(outState)
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun insets(v: View, padding: Int = 0) {
        v.setOnApplyWindowInsetsListener { target, i ->
            if(Build.VERSION.SDK_INT >= 30) {
                val s = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                target.setPadding(s.left + dp(padding), s.top + dp(padding), s.right + dp(padding), s.bottom + dp(padding))
            } else target.setPadding(i.systemWindowInsetLeft + dp(padding), i.systemWindowInsetTop + dp(padding), i.systemWindowInsetRight + dp(padding), i.systemWindowInsetBottom + dp(padding))
            i
        }; v.requestApplyInsets()
    }
    private fun page(title: String, description: String): LinearLayout {
        browsing = false
        view?.releaseSession(); view = null
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        content.addView(TextView(this).apply { text = title; textSize = 28f; setTextColor(Color.rgb(35,35,45)); setPadding(0,dp(16),0,dp(16)) })
        content.addView(TextView(this).apply { text = description; textSize = 16f; setPadding(0,0,0,dp(24)) })
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(250,249,255)); addView(ScrollView(this@MainActivity).apply { isFillViewport = true; addView(content) }) }
        insets(root); setContentView(root)
        return content
    }
    private fun LinearLayout.button(label: String, action: () -> Unit) { addView(Button(this@MainActivity).apply { text = label; setOnClickListener { action() } }) }
    private fun settings(message: String = "Connect to your self-hosted Actual Budget server.") {
        generation++; busy = false
        val p = page("Actual Budget", message)
        fun input(label: String, example: String, value: String): EditText {
            p.addView(TextView(this).apply { text = label })
            return EditText(this).apply { hint = example; setText(value); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI; setSingleLine(true); p.addView(this) }
        }
        val primary = input("Preferred Server URL", "https://actual.example.com:5006", servers.getOrNull(0)?.url.orEmpty())
        val fallback = input("Fallback Server URL (optional)", "https://192.168.1.100:5006", servers.getOrNull(1)?.url.orEmpty())
        val error = TextView(this).apply { setTextColor(Color.rgb(160,35,35)); p.addView(this) }
        p.addView(TextView(this).apply { text = "HTTPS is recommended. HTTP exposes passwords and budget data and usually cannot run Actual. Different origins have separate logins and local budgets.\n\nOpen settings later by long-pressing the launcher icon and choosing Servers & settings, or with a two-finger long press in the app."; setPadding(0,dp(16),0,dp(16)) })
        fun values(): List<Server>? = try {
            listOf(Server.parse(primary.text.toString())) + fallback.text.toString().takeIf { it.isNotBlank() }?.let { listOf(Server.parse(it)) }.orEmpty()
        } catch (e: Exception) { error.text = e.message; null }
        p.button("Connect") {
            val list = values() ?: return@button
            val save = {
                try { store.save(list); servers = list; approved.clear(); connect() }
                catch(_: Exception) { error.text = "Could not securely save configuration." }
            }
            if(list.any { it.uri.scheme == "http" }) confirm("Insecure transport", "HTTP sends passwords and budget data without encryption. Actual normally requires HTTPS. Continue?", save) else save()
        }
        p.button("Test connectivity") {
            val list = values() ?: return@button
            val token = generation; error.text = "Testing…"
            worker.execute { val results = list.map { ServerProbe.check(it) }; runOnUiThread { if(token == generation) error.text = results.mapIndexed { i,h -> "${if(i==0) "Preferred" else "Fallback"}: ${h.detail}\nCOOP: ${h.coop ?: "absent"}; COEP: ${h.coep ?: "absent"}" }.joinToString("\n\n") } }
        }
        p.button("GeckoView diagnostics") { diagnostics() }
        if(app.session != null) p.button("Return to Actual") { showBrowser() }
        p.button("Clear browser data…") {
            confirm("Delete all local browser data?", "This permanently deletes local budgets, unsynced changes, cookies, logins, caches and permissions for every origin. Export and synchronize your budgets first. This cannot be undone.") {
                app.session?.close(); app.session = null; app.active = null; app.location = null; app.state = null; app.diagnostics = null
                app.runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept({ settings("Browser data cleared. Server settings are preserved.") }, { settings("Could not clear all browser data. Try again before reconnecting.") })
            }
        }
        p.addView(TextView(this).apply { text = "App ${BuildConfig.VERSION_NAME}\nGeckoView 158.0.20261007201254\nNo Google Play Services required."; setPadding(0,dp(16),0,0) })
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel", null).setPositiveButton("Continue") { _,_ -> action() }.show()
    }
    private fun connect() {
        val token = ++generation; busy = true
        val p = page("Connecting", "Checking preferred server, then fallback when appropriate…")
        p.addView(ProgressBar(this)); p.button("Configure servers") { settings() }
        worker.execute {
            val results = mutableListOf<Pair<Server,Health>>()
            var selected: Pair<Server,Health>? = null
            for(s in servers) {
                val h = ServerProbe.check(s); results.add(s to h)
                if(h.usable) { selected = s to h; break }
                // Certificate failures require user attention, never silently hide them with failover.
                if(h.kind == HealthKind.TLS_ERROR) break
            }
            runOnUiThread {
                if(token != generation || isDestroyed) return@runOnUiThread
                busy = false
                val target = selected
                if(target == null) unavailable(results.joinToString("\n") { (s,h) -> "${if(s == servers.first()) "Preferred" else "Fallback"}: ${h.detail}" })
                else review(target.first, target.second)
            }
        }
    }
    private fun review(server: Server, health: Health) {
        val old = app.active
        val originChange = old?.origin?.let { it != server.origin } ?: (server.origin != servers.first().origin)
        val warning = if(originChange) "\n\nSwitching origins may require a new login and budget initialization. Each origin retains its own local data. No data is migrated or cleared." else ""
        val p = page(if(originChange) "Switch server?" else "Server reachable", health.detail + "\n\nCOOP: ${health.coop ?: "absent"}\nCOEP: ${health.coep ?: "absent"}\nCompatibility will be measured in the loaded page." + warning)
        p.addView(TextView(this).apply { text = server.url })
        p.button("Open Actual") { load(server) }
        if(app.session != null) p.button("Keep current page") { showBrowser() }
        p.button("Configure servers") { settings() }
    }
    private fun load(server: Server) {
        generation++
        if(app.session == null) {
            app.session = GeckoSession().apply { open(app.runtime) }
            bindSession()
        }
        app.active = server; app.diagnostics = null
        showBrowser()
        val session = app.session!!
        val token = generation
        // Install before first navigation so a fast local page cannot miss its diagnostic script.
        app.runtime.webExtensionController.ensureBuiltIn("resource://android/assets/diagnostics/", "diagnostics@actualbudget.wrapper").accept({ extension ->
            if(token == generation && !isDestroyed && app.session === session) {
                app.extension = extension; bindSession(); session.loadUri(server.url)
            }
        }, { unavailable("Could not initialize the compatibility diagnostic extension. Retry before opening Actual.") })
    }
    private fun bindSession() {
        val session = app.session ?: return
        canBack = app.canBack
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, value: Boolean) { canBack = value; app.canBack = value }
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) { app.location = url; app.diagnostics = null }
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                val origin = Server.origin(request.uri)
                val allowed = origin != null && (origin == app.active?.origin || origin in approved)
                if(allowed && request.target != GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                val result = GeckoResult<AllowOrDeny>()
                if(origin == null) { result.complete(AllowOrDeny.DENY); return result }
                val d = AlertDialog.Builder(this@MainActivity).setTitle(if(allowed) "Open link in Actual?" else "External destination")
                    .setMessage("Destination origin: $origin\n${if(origin.startsWith("http:")) "HTTP sends data without encryption.\n" else ""}\nApprove only a trusted authentication provider. This permits navigation for this app session. Cross-origin login and local storage remain separate.")
                    .setNegativeButton("Cancel") { _,_ -> result.complete(AllowOrDeny.DENY) }
                    .setNeutralButton("Open externally") { _,_ -> result.complete(AllowOrDeny.DENY); openExternal(request.uri) }
                    .setPositiveButton("Allow in app") { _,_ ->
                        approved.add(origin)
                        if(request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) { result.complete(AllowOrDeny.DENY); session.loadUri(request.uri) }
                        else result.complete(AllowOrDeny.ALLOW)
                    }.create()
                d.setOnCancelListener { result.complete(AllowOrDeny.DENY) }; d.show()
                return result
            }
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                handler.post {
                    if(error.category == WebRequestError.ERROR_CATEGORY_NETWORK && Server.origin(uri.orEmpty()) == app.active?.origin) recoverAfterLoadError()
                    else unavailable("Browser could not load the page (category ${error.category}, code ${error.code}). Check connectivity and the server certificate. TLS exceptions are never allowed.")
                }
                return null
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) { app.state = state }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                AlertDialog.Builder(this@MainActivity).setTitle("Save download?")
                    .setMessage("Save using Android's document picker? The file may contain sensitive budget data.")
                    .setNegativeButton("Cancel") { _,_ -> response.body?.close() }
                    .setOnCancelListener { response.body?.close() }
                    .setPositiveButton("Save") { _,_ ->
                        download?.body?.close(); download = response
                        try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "actual-export"), 43) }
                        catch(_: Exception) { response.body?.close(); download = null; Toast.makeText(this@MainActivity, "No document picker available.", Toast.LENGTH_LONG).show() }
                    }.show()
            }
            override fun onCrash(session: GeckoSession) { app.session = null; unavailable("The browser process stopped. Retry to reopen your server. Persistent local browser data is retained.") }
        }
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(session: GeckoSession, perm: GeckoSession.PermissionDelegate.ContentPermission): GeckoResult<Int> {
                val trusted = Server.origin(perm.uri) == app.active?.origin
                val allowed = trusted && perm.permission == GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE
                return GeckoResult.fromValue(if(allowed) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            }
        }
        session.promptDelegate = prompts()
        app.runtime.webExtensionController.ensureBuiltIn("resource://android/assets/diagnostics/", "diagnostics@actualbudget.wrapper").accept({ extension ->
            if(extension == null) { app.diagnostics = JSONObject().put("error", "Diagnostic extension unavailable."); return@accept }
            app.extension = extension
            if(isDestroyed || app.session !== session) return@accept
            session.webExtensionController.setMessageDelegate(extension, object : WebExtension.MessageDelegate {
                override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                    if(sender.session !== session || !sender.isTopLevel || Server.origin(sender.url) != app.active?.origin) return null
                    val data = message as? JSONObject ?: return null
                    if(data.optString("url") != sender.url) return null
                    app.diagnostics = data
                    return null
                }
            }, "actualDiagnostics")
        }, { app.diagnostics = JSONObject().put("error", "Diagnostic extension could not initialize.") })
    }
    private fun showBrowser() {
        val session = app.session ?: return
        browsing = true
        view?.releaseSession()
        val gv = GeckoView(this); view = gv
        gv.setSession(session)
        var start = 0L; var two = false
        gv.setOnTouchListener { _, event ->
            if(event.actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN && event.pointerCount == 2) { start = SystemClock.uptimeMillis(); two = true }
            if(event.actionMasked == android.view.MotionEvent.ACTION_POINTER_UP && two) {
                two = false
                if(SystemClock.uptimeMillis() - start > 1200) { settings(); return@setOnTouchListener true }
            }
            false
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE); addView(gv, FrameLayout.LayoutParams(-1,-1)) }
        insets(root); setContentView(root)
    }
    private fun unavailable(reason: String) {
        busy = false
        val p = page("Connection needs attention", reason + "\n\nLocal budgets and browser data are preserved.")
        p.button("Retry") { connect() }; p.button("Configure servers") { settings() }
        if(app.session != null) p.button("Return to cached page") { showBrowser() }
        p.button("Diagnostics") { diagnostics() }
    }
    private fun diagnostics() {
        val d = app.diagnostics?.takeIf { it.optString("url") == app.location }
        val keys = listOf("secureContext", "crossOriginIsolated", "sharedArrayBufferSupported", "wasmSupported", "workersSupported", "indexedDBSupported", "cacheSupported", "serviceWorkerSupported")
        val result = if(d == null) "No result yet. Open your server and wait for page loading, then return here. Authentication pages may fail isolation checks until Actual is loaded." else keys.joinToString("\n") { "$it: ${if(d.has(it)) d.optBoolean(it).toString() else "unknown"}" } + "\n\nEngine: ${d.optString("engine", "unknown")}\n${d.optString("error") }"
        val required = keys.take(4).all { d?.optBoolean(it) == true }
        val p = page("GeckoView diagnostics", "Bundled GeckoView: 158.0.20261007201254\n\n$result\n\n${if(required) "Required API presence checks pass. Actual initialization and synchronization still need verification." else "Compatibility is unverified or a required check failed. Actual requires a secure context, COOP same-origin, COEP require-corp, SharedArrayBuffer and WebAssembly. No security checks are bypassed."}")
        p.button("Refresh results") { diagnostics() }
        if(app.session != null) p.button("Return to Actual") { showBrowser() }
        p.button("Configure servers") { settings() }
    }
    private fun scheduleCheck() { handler.post { handler.removeCallbacks(networkCheck); if(browsing) handler.postDelayed(networkCheck, 3000) } }
    private fun checkConnection() {
        val current = app.active ?: return
        if(busy || !browsing) return
        busy = true; val token = generation
        worker.execute {
            val h = ServerProbe.check(current)
            // One second probe protects against transient loss; no recurring retry loop.
            val second = if(h.kind == HealthKind.UNREACHABLE) ServerProbe.check(current) else h
            val replacement = if(!second.usable && second.kind != HealthKind.TLS_ERROR) servers.filter { it != current }.map { it to ServerProbe.check(it) }.firstOrNull { it.second.usable } else null
            runOnUiThread {
                if(token != generation || isDestroyed) return@runOnUiThread
                busy = false
                if(!browsing) return@runOnUiThread
                if(replacement != null) review(replacement.first, replacement.second)
                else if(!second.usable) unavailable(second.detail)
                // A healthy page is never reloaded just because network capabilities changed.
            }
        }
    }
    private fun recoverAfterLoadError() {
        val current = app.active ?: return
        val token = ++generation
        page("Connection interrupted", "Checking servers. Local browser data is preserved.").button("Configure servers") { settings() }
        busy = true
        worker.execute {
            val h = ServerProbe.check(current)
            val replacement = if(!h.usable && h.kind != HealthKind.TLS_ERROR) servers.filter { it != current }.map { it to ServerProbe.check(it) }.firstOrNull { it.second.usable } else null
            runOnUiThread {
                if(token != generation || isDestroyed) return@runOnUiThread
                busy = false
                if(replacement != null) review(replacement.first, replacement.second)
                else unavailable(if(h.usable) "Server responds, but the browser page load failed. Retry or review diagnostics." else h.detail)
            }
        }
    }
    private fun openExternal(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch(_: Exception) { Toast.makeText(this, "No app can open this link.", Toast.LENGTH_LONG).show() }
    }
    private fun back() { if(!browsing && app.session != null) showBrowser() else if(browsing && canBack) app.session?.goBack() else finish() }
    @Deprecated("For Android 10–12") override fun onBackPressed() = back()
    override fun onStart() {
        super.onStart()
        try { getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback); registered = true } catch(_: RuntimeException) { }
    }
    override fun onResume() { super.onResume(); app.session?.setActive(true); scheduleCheck() }
    override fun onPause() { app.session?.setActive(false); super.onPause() }
    override fun onStop() {
        handler.removeCallbacks(networkCheck)
        if(registered) { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback); registered = false }
        super.onStop()
    }
    override fun onDestroy() {
        generation++; handler.removeCallbacksAndMessages(null); worker.shutdownNow()
        view?.releaseSession()
        download?.body?.close(); download = null
        fileResult?.complete(filePrompt!!.dismiss()); fileResult = null; filePrompt = null
        app.session?.apply { navigationDelegate = null; progressDelegate = null; contentDelegate = null; promptDelegate = null; permissionDelegate = null
            app.extension?.let { webExtensionController.setMessageDelegate(it, null, "actualDiagnostics") }
        }
        super.onDestroy()
    }
    private fun prompts() = object : GeckoSession.PromptDelegate {
        override fun onFilePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.FilePrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            fileResult?.let { old -> filePrompt?.let { old.complete(it.dismiss()) } }
            filePrompt = prompt; fileResult = result
            try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE), 42) }
            catch(_: Exception) { result.complete(prompt.dismiss()); fileResult = null; filePrompt = null }
            return result
        }
        override fun onAuthPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AuthPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val user = EditText(this@MainActivity).apply { hint = "Username" }
            val password = EditText(this@MainActivity).apply { hint = "Password"; inputType = 129 }
            val inputs = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(user); addView(password) }
            val dialog = AlertDialog.Builder(this@MainActivity).setTitle("Server authentication").setMessage("Origin: ${Server.origin(prompt.authOptions.uri.orEmpty()) ?: "unknown"}\nOnly enter credentials for a trusted server.").setView(inputs)
                .setNegativeButton("Cancel") { _,_ -> result.complete(prompt.dismiss()) }
                .setPositiveButton("Sign in") { _,_ -> result.complete(prompt.confirm(user.text.toString(), password.text.toString())) }.create()
            dialog.setOnCancelListener { result.complete(prompt.dismiss()) }; dialog.show(); return result
        }
        override fun onTextPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.TextPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val input = EditText(this@MainActivity).apply { setText(prompt.defaultValue) }
            AlertDialog.Builder(this@MainActivity).setTitle(prompt.title).setMessage(prompt.message).setView(input)
                .setPositiveButton("OK") { _,_ -> r.complete(prompt.confirm(input.text.toString())) }
                .setNegativeButton("Cancel") { _,_ -> r.complete(prompt.dismiss()) }.setOnCancelListener { r.complete(prompt.dismiss()) }.show()
            return r
        }
        override fun onChoicePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ChoicePrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val choices = mutableListOf<GeckoSession.PromptDelegate.ChoicePrompt.Choice>()
            fun flatten(items: Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>) {
                for(c in items) { if(c.items != null) flatten(c.items!!) else if(!c.disabled && !c.separator) choices.add(c) }
            }
            flatten(prompt.choices)
            val b = AlertDialog.Builder(this@MainActivity).setTitle(prompt.title)
            if(prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
                val selected = choices.map { it.selected }.toBooleanArray()
                b.setMultiChoiceItems(choices.map { it.label }.toTypedArray(), selected) { _, i, checked -> selected[i] = checked }
                    .setPositiveButton("OK") { _,_ -> r.complete(prompt.confirm(choices.filterIndexed { i,_ -> selected[i] }.map { it.id }.toTypedArray())) }
            } else b.setItems(choices.map { it.label }.toTypedArray()) { _,i -> r.complete(prompt.confirm(choices[i])) }
            b.setNegativeButton("Cancel") { _,_ -> r.complete(prompt.dismiss()) }.setOnCancelListener { r.complete(prompt.dismiss()) }.show()
            return r
        }
        override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            AlertDialog.Builder(this@MainActivity).setTitle(prompt.title).setMessage(prompt.message).setPositiveButton("OK") { _,_ -> r.complete(prompt.dismiss()) }.setOnCancelListener { r.complete(prompt.dismiss()) }.show(); return r
        }
        override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val r = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            AlertDialog.Builder(this@MainActivity).setTitle(prompt.title).setMessage(prompt.message).setPositiveButton("OK") { _,_ -> r.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)) }.setNegativeButton("Cancel") { _,_ -> r.complete(prompt.dismiss()) }.setOnCancelListener { r.complete(prompt.dismiss()) }.show(); return r
        }
    }
    @Deprecated("Document picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 42) {
            val prompt = filePrompt ?: return; val result = fileResult ?: return
            val uris = data?.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri }.toTypedArray() } ?: data?.data?.let { arrayOf(it) }
            result.complete(if(resultCode == RESULT_OK && uris != null) prompt.confirm(this, uris) else prompt.dismiss())
            fileResult = null; filePrompt = null
        }
        if(requestCode == 43) {
            val response = download; download = null
            val destination = data?.data
            if(resultCode == RESULT_OK && destination != null && response != null) {
                worker.execute {
                    try { response.body?.use { input -> contentResolver.openOutputStream(destination)?.use { input.copyTo(it) } }
                        runOnUiThread { Toast.makeText(this, "Download saved.", Toast.LENGTH_LONG).show() }
                    } catch(_: Exception) { runOnUiThread { Toast.makeText(this, "Could not save download.", Toast.LENGTH_LONG).show() } }
                }
            } else response?.body?.close()
        }
    }
}
