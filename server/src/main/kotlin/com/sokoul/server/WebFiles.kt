package com.sokoul.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import java.io.File

/**
 * The web client's static files (openspec web-client 1.1), served at `/` behind the network lock like everything else.
 * No session is needed to load the page itself: it signs in through the JSON API.
 *
 * - Only regular files under [root] are served: segments `.`, `..`, empty, hidden (`.x`) or carrying `\`, `:` or NUL
 *   are refused, and the canonical path must stay under the canonical root (symbolic links out are refused too).
 * - No directory listing: a folder is not found.
 * - A path without an extension that is not a file is a client route: it gets `index.html`.
 * - `index.html` is `no-cache`; `vendor/`, `fonts/` and hashed names (`app.3f2a9c1d.js`) are cached a year; the rest
 *   revalidates (`no-cache` with `ETag`).
 * - Every answer carries [CSP]: scripts, styles, fonts and connections from this origin only, media from this origin and
 *   `blob:` (hls.js), images also from TMDB's image CDN (posters carry no key).
 */
class WebFiles(root: File) {
    private val root: File = root.canonicalFile

    val present: Boolean get() = File(root, INDEX).isFile

    suspend fun respond(call: ApplicationCall, segments: List<String>) {
        securityHeaders(call)
        if (segments.any { !safeSegment(it) }) return notFound(call)
        val relative = segments.filter { it.isNotEmpty() }
        val target = if (relative.isEmpty()) File(root, INDEX) else File(root, relative.joinToString(File.separator))
        val canonical = runCatching { target.canonicalFile }.getOrNull() ?: return notFound(call)
        if (!canonical.path.startsWith(root.path + File.separator) && canonical != root) return notFound(call)
        if (canonical.isFile) return serve(call, canonical)
        val last = relative.lastOrNull().orEmpty()
        // A client route ("/title/movie/603"): the app shell. Anything that looks like a file stays not found.
        if (!canonical.isDirectory && '.' !in last) {
            val index = File(root, INDEX)
            if (index.isFile) return serve(call, index)
        }
        notFound(call)
    }

    private suspend fun serve(call: ApplicationCall, file: File) {
        val etag = "\"" + java.lang.Long.toHexString(file.lastModified()) + "-" + java.lang.Long.toHexString(file.length()) + "\""
        call.response.header(HttpHeaders.CacheControl, cacheControl(file))
        call.response.header(HttpHeaders.ETag, etag)
        if (call.request.headers[HttpHeaders.IfNoneMatch]?.split(',')?.any { it.trim() == etag } == true) {
            call.respond(HttpStatusCode.NotModified)
            return
        }
        call.respond(LocalFileContent(file, contentType(file.name)))
    }

    private fun cacheControl(file: File): String {
        val rel = file.path.removePrefix(root.path + File.separator).replace(File.separatorChar, '/')
        return when {
            rel == INDEX -> "no-cache"
            rel.startsWith("vendor/") || rel.startsWith("fonts/") || HASHED.containsMatchIn(file.name) -> "public, max-age=31536000, immutable"
            else -> "no-cache"
        }
    }

    private suspend fun notFound(call: ApplicationCall) {
        call.respond(HttpStatusCode.NotFound, ErrorBody("not_found"))
    }

    companion object {
        const val INDEX = "index.html"

        const val CSP = "default-src 'none'; script-src 'self'; style-src 'self'; font-src 'self'; connect-src 'self'; " +
            "img-src 'self' data: blob: https://image.tmdb.org https://i.ytimg.com https://assets.fanart.tv https://*.anilist.co " +
            "https://*.mangadex.org https://*.mangadex.network; media-src 'self' blob:; worker-src 'self' blob:; " +
            "frame-src https://www.youtube-nocookie.com; " +
            "manifest-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'"

        private val HASHED = Regex("\\.[0-9a-f]{8,}\\.[a-z0-9]+$", RegexOption.IGNORE_CASE)

        fun securityHeaders(call: ApplicationCall) {
            call.response.header("Content-Security-Policy", CSP)
            call.response.header("X-Content-Type-Options", "nosniff")
            call.response.header("Referrer-Policy", "no-referrer")
            call.response.header("X-Frame-Options", "DENY")
        }

        fun safeSegment(s: String): Boolean =
            s != "." && s != ".." && !s.startsWith('.') && s.none { it == '\\' || it == ':' || it == '\u0000' || it == '/' || it.isISOControl() }

        fun contentType(name: String): ContentType = when (name.substringAfterLast('.', "").lowercase()) {
            "html" -> ContentType.Text.Html.withParameter("charset", "utf-8")
            "js", "mjs" -> ContentType.parse("text/javascript; charset=utf-8")
            "css" -> ContentType.parse("text/css; charset=utf-8")
            "json" -> ContentType.Application.Json
            "webmanifest" -> ContentType.parse("application/manifest+json")
            "png" -> ContentType.Image.PNG
            "svg" -> ContentType.Image.SVG
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "webp" -> ContentType.parse("image/webp")
            "ico" -> ContentType.parse("image/x-icon")
            "ttf" -> ContentType.parse("font/ttf")
            "woff2" -> ContentType.parse("font/woff2")
            "woff" -> ContentType.parse("font/woff")
            "txt" -> ContentType.Text.Plain.withParameter("charset", "utf-8")
            "map" -> ContentType.Application.Json
            else -> ContentType.Application.OctetStream
        }

        /**
         * `web/` when it is not configured: in the working folder (repo root), one level up (`gradlew :server:run` runs
         * in `server/`), then beside the install (`<install>/web`, the jar being in `<install>/lib`).
         */
        fun locate(): File? {
            val cwd = File(System.getProperty("user.dir")).absoluteFile
            val jarDir = runCatching { File(WebFiles::class.java.protectionDomain.codeSource.location.toURI()).parentFile }.getOrNull()
            val candidates = listOfNotNull(
                File(cwd, "web"),
                cwd.parentFile?.let { File(it, "web") },
                jarDir?.parentFile?.let { File(it, "web") },
                jarDir?.let { File(it, "web") },
            )
            return candidates.firstOrNull { File(it, INDEX).isFile }
        }
    }
}
