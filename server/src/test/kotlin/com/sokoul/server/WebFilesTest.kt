package com.sokoul.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

/** web-client 1.1: the static client at `/`, its cache headers, its CSP, and nothing outside its folder. */
class WebFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun site(): File {
        val parent = tmp.newFolder()
        File(parent, "secret.txt").writeText("TOP SECRET")
        val web = File(parent, "web").apply { mkdirs() }
        File(web, "index.html").writeText("<!doctype html><title>Sokoul</title>")
        File(web, "js").mkdirs()
        File(web, "js/app.js").writeText("console.log('app')")
        File(web, "vendor").mkdirs()
        File(web, "vendor/hls.min.js").writeText("/* hls */")
        File(web, "css").mkdirs()
        File(web, "css/app.3f2a9c1d7e.css").writeText("body{}")
        File(web, ".env").writeText("HIDDEN")
        File(web, "manifest.webmanifest").writeText("{}")
        return web
    }

    private fun server(web: File) = TestServer(tmp, ServerConfig.from(Properties().apply { setProperty("web.dir", web.path) }))

    @Test fun `the shell is served without a session with a strict CSP and no cache`() = testApplication {
        server(site()).install(this)
        val r = client.get("/")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("<title>Sokoul</title>"))
        assertEquals("no-cache", r.headers[HttpHeaders.CacheControl])
        val csp = r.headers["Content-Security-Policy"].orEmpty()
        assertTrue(csp, csp.contains("default-src 'none'"))
        assertTrue(csp, csp.contains("script-src 'self';"))
        assertTrue(csp, csp.contains("media-src 'self' blob:"))
        assertTrue(csp, csp.contains("frame-ancestors 'none'"))
        assertFalse(csp, csp.contains("unsafe-inline"))
        assertEquals("nosniff", r.headers["X-Content-Type-Options"])
        assertTrue(r.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/html"))
    }

    @Test fun `vendor and hashed files are cached a year, the rest revalidates`() = testApplication {
        server(site()).install(this)
        val vendor = client.get("/vendor/hls.min.js")
        assertEquals(HttpStatusCode.OK, vendor.status)
        assertEquals("public, max-age=31536000, immutable", vendor.headers[HttpHeaders.CacheControl])
        assertTrue(vendor.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/javascript"))
        assertEquals("public, max-age=31536000, immutable", client.get("/css/app.3f2a9c1d7e.css").headers[HttpHeaders.CacheControl])
        val app = client.get("/js/app.js")
        assertEquals("no-cache", app.headers[HttpHeaders.CacheControl])
        val etag = app.headers[HttpHeaders.ETag]!!
        assertEquals(HttpStatusCode.NotModified, client.get("/js/app.js") { header(HttpHeaders.IfNoneMatch, etag) }.status)
        assertEquals("application/manifest+json", client.get("/manifest.webmanifest").headers[HttpHeaders.ContentType])
    }

    @Test fun `client routes get the shell, missing files and folders do not`() = testApplication {
        server(site()).install(this)
        val route = client.get("/title/movie/603")
        assertEquals(HttpStatusCode.OK, route.status)
        assertTrue(route.bodyAsText().contains("<title>Sokoul</title>"))
        assertEquals(HttpStatusCode.NotFound, client.get("/js/missing.js").status)
        // No listing: a folder is not found, with or without its slash.
        assertEquals(HttpStatusCode.NotFound, client.get("/js/").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/js").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/.env").status)
        // An unknown API route stays JSON.
        val api = client.get("/api/nothing")
        assertEquals(HttpStatusCode.NotFound, api.status)
        assertEquals("""{"error":"not_found"}""", api.bodyAsText())
    }

    @Test fun `no path leaves the web folder`() = testApplication {
        server(site()).install(this)
        for (path in listOf(
            "/%2e%2e/secret.txt", "/js/%2e%2e/%2e%2e/secret.txt", "/..%2fsecret.txt", "/..%5csecret.txt",
            "/js/..%2f..%2fsecret.txt", "/%2e%2e%2f%2e%2e%2fsecret.txt", "/C:%5cWindows%5cwin.ini", "/js/app.js%00.html",
            "/%252e%252e/secret.txt",
        )) {
            val r = client.get(path)
            assertFalse("$path leaked", r.bodyAsText().contains("TOP SECRET"))
            assertTrue("$path answered ${r.status}", r.status == HttpStatusCode.NotFound || r.status == HttpStatusCode.BadRequest)
        }
    }

    @Test fun `the network lock still applies to the static files`() = testApplication {
        server(site()).install(this)
        assertEquals(HttpStatusCode.Forbidden, client.get("/") { header("Cf-Ray", "abc") }.status)
    }

    @Test fun `segments that could escape are refused`() {
        listOf("..", ".", ".git", "a\\b", "c:", "x\u0000").forEach { assertFalse(it, WebFiles.safeSegment(it)) }
        listOf("app.js", "hls.min.js", "index.html").forEach { assertTrue(it, WebFiles.safeSegment(it)) }
    }
}
