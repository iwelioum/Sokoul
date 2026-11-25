package com.sokoul.server

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `GET /link`: the small page where a phone or computer confirms a TV's code (opened from the TV's QR, or typed).
 * It signs in with the owner's or a member's name and passphrase, keeps the session token in `localStorage`, and calls
 * `/api/link/confirm`. Monochrome, no external assets, French or English from `Accept-Language`. Temporary until the
 * web client exists.
 */
object LinkPage {
    const val PATH = "/link"

    private val FR = mapOf(
        "lang" to "fr",
        "title" to "Connecter une TV",
        "intro" to "Saisissez le code affiché sur la TV.",
        "signin" to "Connexion",
        "name" to "Nom",
        "passphrase" to "Phrase secrète",
        "signinButton" to "Se connecter",
        "code" to "Code de la TV",
        "confirm" to "Connecter la TV",
        "signout" to "Se déconnecter",
        "wrong" to "Nom ou phrase secrète incorrects.",
        "tooMany" to "Trop d’essais. Réessayez dans quelques minutes.",
        "badCode" to "Code inconnu ou expiré. Vérifiez la TV.",
        "linked" to "C’est fait : %s est connectée.",
        "failed" to "Le serveur ne répond pas. Réessayez.",
        "device" to "Navigateur",
    )

    private val EN = mapOf(
        "lang" to "en",
        "title" to "Link a TV",
        "intro" to "Enter the code shown on the TV.",
        "signin" to "Sign in",
        "name" to "Name",
        "passphrase" to "Passphrase",
        "signinButton" to "Sign in",
        "code" to "TV code",
        "confirm" to "Link the TV",
        "signout" to "Sign out",
        "wrong" to "Wrong name or passphrase.",
        "tooMany" to "Too many attempts. Try again in a few minutes.",
        "badCode" to "Unknown or expired code. Check the TV.",
        "linked" to "Done: %s is signed in.",
        "failed" to "The server did not answer. Try again.",
        "device" to "Browser",
    )

    /** French when the browser prefers it over English, English otherwise. */
    fun language(acceptLanguage: String?): String {
        val ranked = acceptLanguage.orEmpty().split(',').mapIndexedNotNull { index, part ->
            val pieces = part.trim().split(';')
            val tag = pieces[0].trim().lowercase().substringBefore('-')
            val q = pieces.drop(1).firstNotNullOfOrNull { p ->
                p.trim().takeIf { it.startsWith("q=") }?.removePrefix("q=")?.toDoubleOrNull()
            } ?: 1.0
            if (tag == "fr" || tag == "en") Triple(tag, q, index) else null
        }
        return ranked.sortedWith(compareByDescending<Triple<String, Double, Int>> { it.second }.thenBy { it.third })
            .firstOrNull()?.first ?: "en"
    }

    suspend fun respond(call: ApplicationCall) {
        val strings = if (language(call.request.headers["Accept-Language"]) == "fr") FR else EN
        val nonce = Secrets.b64url(Secrets.bytes(16))
        call.response.header(
            "Content-Security-Policy",
            "default-src 'none'; script-src 'nonce-$nonce'; style-src 'nonce-$nonce'; connect-src 'self'; " +
                "base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
        )
        // The code travels in the query string: keep it out of caches and referrers.
        call.response.header("Cache-Control", "no-store")
        call.response.header("Referrer-Policy", "no-referrer")
        call.response.header("X-Content-Type-Options", "nosniff")
        call.respondText(html(strings, nonce), ContentType.Text.Html.withParameter("charset", "utf-8"))
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun html(t: Map<String, String>, nonce: String): String {
        val js = Json.encodeToString(JsonObject.serializer(), JsonObject(t.mapValues { JsonPrimitive(it.value) }))
            .replace("<", "\\u003c")
        return """<!doctype html>
<html lang="${t.getValue("lang")}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="dark">
<title>Sokoul · ${esc(t.getValue("title"))}</title>
<style nonce="$nonce">
*{box-sizing:border-box}
body{margin:0;background:#000;color:#fff;font:400 18px/1.45 -apple-system,system-ui,"Segoe UI",Roboto,sans-serif}
main{max-width:28rem;margin:0 auto;padding:48px 16px}
h1{font-size:29px;font-weight:600;margin:0 0 8px}
h2{font-size:21px;font-weight:600;margin:0 0 16px}
p{color:rgba(255,255,255,.72);margin:0 0 24px}
label{display:block;font-size:15px;color:rgba(255,255,255,.72);margin:16px 0 6px}
input{width:100%;font:inherit;color:#fff;background:#1c1c1e;border:1px solid rgba(255,255,255,.18);border-radius:12px;padding:14px 16px}
input:focus{outline:2px solid #fff;outline-offset:2px}
#code{font-size:29px;letter-spacing:.12em;text-transform:uppercase;text-align:center}
button{width:100%;font:inherit;font-weight:600;color:#000;background:#fff;border:0;border-radius:12px;padding:14px 16px;margin-top:24px;cursor:pointer}
button:focus-visible{outline:2px solid #fff;outline-offset:3px}
button.quiet{background:transparent;color:rgba(255,255,255,.72);font-weight:400;margin-top:12px}
[hidden]{display:none}
#status{min-height:1.5em;margin-top:24px;color:#fff}
</style>
</head>
<body>
<main>
<h1>${esc(t.getValue("title"))}</h1>
<p>${esc(t.getValue("intro"))}</p>
<form id="signin" hidden>
<h2>${esc(t.getValue("signin"))}</h2>
<label for="name">${esc(t.getValue("name"))}</label>
<input id="name" name="username" autocomplete="username" required maxlength="40">
<label for="passphrase">${esc(t.getValue("passphrase"))}</label>
<input id="passphrase" name="password" type="password" autocomplete="current-password" required minlength="12">
<button type="submit">${esc(t.getValue("signinButton"))}</button>
</form>
<form id="link" hidden>
<label for="code">${esc(t.getValue("code"))}</label>
<input id="code" name="code" autocomplete="one-time-code" autocapitalize="characters" spellcheck="false" required maxlength="9" placeholder="XXXX-XXXX">
<button type="submit">${esc(t.getValue("confirm"))}</button>
<button type="button" class="quiet" id="signout">${esc(t.getValue("signout"))}</button>
</form>
<div id="status" role="status" aria-live="polite"></div>
</main>
<script nonce="$nonce">
(function () {
  var T = $js, KEY = "sokoul.session";
  var signin = document.getElementById("signin"), link = document.getElementById("link");
  var status = document.getElementById("status"), code = document.getElementById("code");
  function token() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
  function keep(v) { try { v ? localStorage.setItem(KEY, v) : localStorage.removeItem(KEY); } catch (e) {} }
  function say(text) { status.textContent = text || ""; }
  function show() {
    var signedIn = !!token();
    signin.hidden = signedIn; link.hidden = !signedIn;
    (signedIn ? code : document.getElementById("name")).focus();
  }
  function post(path, body, auth) {
    var headers = { "Content-Type": "application/json" };
    if (auth) headers.Authorization = "Bearer " + token();
    return fetch(path, { method: "POST", headers: headers, body: JSON.stringify(body) });
  }
  code.value = new URLSearchParams(location.search).get("code") || "";
  signin.addEventListener("submit", function (e) {
    e.preventDefault(); say("");
    post("/api/auth/login", {
      name: document.getElementById("name").value,
      passphrase: document.getElementById("passphrase").value,
      deviceName: T.device
    }).then(function (r) {
      if (r.status === 429) return say(T.tooMany);
      if (!r.ok) return say(T.wrong);
      return r.json().then(function (b) { keep(b.token); document.getElementById("passphrase").value = ""; show(); });
    }).catch(function () { say(T.failed); });
  });
  link.addEventListener("submit", function (e) {
    e.preventDefault(); say("");
    post("/api/link/confirm", { userCode: code.value }, true).then(function (r) {
      if (r.status === 401) { keep(null); show(); return; }
      if (!r.ok) return say(T.badCode);
      return r.json().then(function (b) { say(T.linked.replace("%s", b.deviceName)); code.value = ""; });
    }).catch(function () { say(T.failed); });
  });
  document.getElementById("signout").addEventListener("click", function () {
    post("/api/auth/logout", {}, true).catch(function () {});
    keep(null); say(""); show();
  });
  show();
})();
</script>
</body>
</html>
"""
    }
}
