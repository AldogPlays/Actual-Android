package app.actualbudget.wrapper

import org.junit.Assert.*
import org.junit.Test
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

class ServerTest {
    @Test fun preservesPortsPathsAndIpv6() {
        assertEquals("https://actual.example.com:5006/budget/", Server.parse(" https://actual.example.com:5006/budget/ ").url)
        assertEquals("https://[fd00::1234]:5006/actual", Server.parse("https://[fd00::1234]:5006/actual").url)
        assertEquals("http://node.tail123.ts.net:5006/", Server.parse("http://node.tail123.ts.net:5006").url)
        assertEquals("https://actual.example.com:443", Server.parse("https://actual.example.com").origin)
        assertEquals(Server.parse("https://EXAMPLE.com:443/a").origin, Server.parse("https://example.com/b").origin)
        assertNotEquals(Server.parse("https://example.com:5006").origin, Server.parse("https://example.com").origin)
        assertEquals("https://example.com/a%20b/", Server.parse("https://example.com/a%20b/").url)
    }
    @Test fun rejectsDangerousOrAmbiguousUrls() {
        listOf("file:///etc/passwd", "javascript:alert(1)", "https://u:p@host/", "https://host?token=x", "https://host/#token", "https://host:0/", "https://host:65536/", "https://::1:5006", "host:5006", "https://").forEach {
            try { Server.parse(it); fail("Accepted $it") } catch(_: IllegalArgumentException) {} catch(_: java.net.URISyntaxException) {}
        }
    }
    @Test fun healthDoesNotRequireActualHtmlOrFollowExternalRedirects() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        fun endpoint(path: String, code: Int, location: String? = null) {
            server.createContext(path) { exchange ->
                if(location != null) exchange.responseHeaders.add("Location",location)
                exchange.responseHeaders.add("Cross-Origin-Opener-Policy","same-origin")
                exchange.responseHeaders.add("Cross-Origin-Embedder-Policy","require-corp")
                exchange.sendResponseHeaders(code,-1); exchange.close()
            }
        }
        endpoint("/",200); endpoint("/auth",401); endpoint("/bad",503)
        endpoint("/redirect",302,"/"); endpoint("/external",302,"https://identity.invalid/login")
        endpoint("/loop",302,"/loop")
        server.start()
        try {
            fun check(path: String) = ServerProbe.check(Server.parse("http://127.0.0.1:${server.address.port}$path"))
            assertEquals(HealthKind.REACHABLE, check("/").kind)
            assertEquals("same-origin", check("/").coop)
            assertEquals(HealthKind.AUTH, check("/auth").kind)
            assertEquals(HealthKind.HTTP_ERROR, check("/bad").kind)
            assertEquals(HealthKind.REACHABLE, check("/redirect").kind)
            assertEquals(HealthKind.REDIRECT, check("/external").kind)
            assertEquals(HealthKind.HTTP_ERROR, check("/loop").kind)
        } finally { server.stop(0) }
    }
}
