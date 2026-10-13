package app.actualbudget.wrapper

import java.net.URI
import java.net.HttpURLConnection
import javax.net.ssl.SSLException

data class Server(val url: String) {
    val uri: URI get() = URI(url)
    val origin: String get() = "${uri.scheme.lowercase()}://${uri.host.lowercase()}:${if(uri.port >= 0) uri.port else if(uri.scheme == "https") 443 else 80}"
    companion object {
        fun parse(text: String): Server {
            val u = URI(text.trim())
            require(u.scheme?.lowercase() in listOf("http", "https") && !u.host.isNullOrBlank()) { "Enter an HTTP or HTTPS URL with a valid host (bracket IPv6 addresses)." }
            require(u.userInfo == null && u.rawQuery == null && u.rawFragment == null && (u.port == -1 || u.port in 1..65535)) { "Use a server URL without credentials, query or fragment, and a valid port." }
            return Server(u.scheme.lowercase() + "://" + u.rawAuthority + u.rawPath.orEmpty().ifEmpty { "/" })
        }
        fun origin(url: String): String? = try { parse(url.substringBefore('#').substringBefore('?')).origin } catch (_: Exception) { null }
    }
}
enum class HealthKind { REACHABLE, AUTH, REDIRECT, HTTP_ERROR, TLS_ERROR, UNREACHABLE }
data class Health(val kind: HealthKind, val detail: String, val coop: String? = null, val coep: String? = null) {
    val usable get() = kind in listOf(HealthKind.REACHABLE, HealthKind.AUTH, HealthKind.REDIRECT)
}
object ServerProbe {
    fun check(server: Server): Health {
        var url = server.uri
        repeat(5) {
            var c: HttpURLConnection? = null
            try {
                c = url.toURL().openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 4000; c.readTimeout = 4000
                c.setRequestProperty("Accept", "text/html")
                val status = c.responseCode
                if(status in 300..399) {
                    val next = c.getHeaderField("Location")?.let { url.resolve(it) }
                    if(next == null) return Health(HealthKind.HTTP_ERROR, "Redirect has no destination.")
                    if(Server.origin(next.toString()) != server.origin) return Health(HealthKind.REDIRECT, "Server responds with an authentication or cross-origin redirect. Review the destination in the browser approval prompt.")
                    url = next
                } else if(status == 401 || status == 403) return Health(HealthKind.AUTH, "Server is reachable; authentication or proxy authorization is required (HTTP $status).")
                else if(status !in 200..299) return Health(HealthKind.HTTP_ERROR, "Server returned HTTP $status.")
                else return Health(HealthKind.REACHABLE, "Server reachable. Identity is not verified; only connect to a server you trust.", c.getHeaderField("Cross-Origin-Opener-Policy"), c.getHeaderField("Cross-Origin-Embedder-Policy"))
            } catch(_: SSLException) { return Health(HealthKind.TLS_ERROR, "TLS certificate or handshake failed. Certificates are never bypassed.") }
            catch(_: Exception) { return Health(HealthKind.UNREACHABLE, "Host unreachable, DNS failed, or connection timed out.") }
            finally { c?.disconnect() }
        }
        return Health(HealthKind.HTTP_ERROR, "Too many redirects.")
    }
}
