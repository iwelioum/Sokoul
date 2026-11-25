package com.sokoul.pc

import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.pc.account.AccountPanelModel
import com.sokoul.pc.account.AccountResult
import com.sokoul.pc.account.AccountView
import com.sokoul.pc.account.DbAccountStore
import com.sokoul.pc.account.LinkOutcome
import com.sokoul.pc.account.MemoryAccountStore
import com.sokoul.pc.account.Secret
import com.sokoul.pc.account.SokoulAccount
import com.sokoul.pc.account.StoredSession
import com.sokoul.pc.account.lastUsedText
import com.sokoul.pc.i18n.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** openspec sokoul-server 5.4: the app side of accounts and TV linking, against a scripted server. */
class AccountTest {
    private val server = MockWebServer()
    private val sleeps = mutableListOf<Long>()
    private var now = 1_000_000L
    private val token = "tok_SECRET_abcdefghijklmnopqrstuvwxyz0123456789"
    private val deviceCode = "dev_SECRET_zyxwvutsrqponmlkjihgfedcba9876543210"

    @Before fun start() {
        server.start()
        Strings.applyLanguage("en")
    }

    @After fun stop() {
        server.shutdown()
        Strings.applyLanguage("en")
    }

    private fun base() = server.url("/").toString().trimEnd('/')

    private fun account(store: MemoryAccountStore = MemoryAccountStore(base())) =
        SokoulAccount(store, OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(), sleep = { sleeps += it; now += it }, clock = { now })

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun linkStart(code: String = "BCDF-GHJK", expiresIn: Int = 600, interval: Int = 3) =
        json(200, """{"deviceCode":"$deviceCode","userCode":"$code","verifyPath":"/link?code=$code","expiresIn":$expiresIn,"interval":$interval}""")

    private fun me(name: String = "Ana", role: String = "owner") = json(200, """{"id":"u1","name":"$name","role":"$role"}""")

    private fun devices() = json(
        200,
        """{"sessions":[
            {"id":"s1","deviceName":"Salon TV","createdAt":1,"lastUsedAt":$now,"current":true},
            {"id":"s2","deviceName":"Tab S7 FE","createdAt":1,"lastUsedAt":${now - 86_400_000L},"current":false},
            {"id":"s3","deviceName":"PC du bureau","createdAt":1,"lastUsedAt":${now - 3 * 86_400_000L},"current":false}
        ]}""",
    )

    // --- client ---------------------------------------------------------------------------------------------------

    @Test fun `the poll's own user saves the extra call`() = runBlocking {
        server.enqueue(linkStart())
        server.enqueue(json(200, """{"token":"$token","sessionId":"s1","user":{"id":"u1","name":"Ana","role":"owner"}}"""))
        val account = account()
        val outcome = account.awaitLink((account.startLink("Salon TV") as AccountResult.Ok).value)
        assertTrue(outcome is LinkOutcome.Linked)
        assertEquals("Ana", account.session()?.user?.name)
        assertEquals(2, server.requestCount)
    }

    @Test fun `the server says whether it still waits for its owner`() = runBlocking {
        server.enqueue(json(200, """{"needsOwner":true}"""))
        server.enqueue(json(200, """{"needsOwner":false}"""))
        server.enqueue(json(404, """{"error":"not_found"}"""))
        val account = account()
        assertEquals(true, account.needsOwner())
        assertEquals(false, account.needsOwner())
        assertEquals(null, account.needsOwner())
    }

    @Test fun `link happy path waits the interval, stores the session and reads the name`() = runBlocking {
        server.enqueue(linkStart())
        server.enqueue(json(428, """{"error":"pending"}"""))
        server.enqueue(json(200, """{"token":"$token","sessionId":"s1"}"""))
        server.enqueue(me())
        val store = MemoryAccountStore(base())
        val account = account(store)

        val code = (account.startLink("Salon TV") as AccountResult.Ok).value
        assertEquals("BCDF-GHJK", code.userCode)
        assertEquals(base() + "/link?code=BCDF-GHJK", code.verifyUrl)
        val outcome = account.awaitLink(code)

        assertTrue(outcome is LinkOutcome.Linked)
        assertEquals(listOf(3_000L, 3_000L), sleeps)
        assertEquals("Ana", account.session()?.user?.name)
        assertEquals(token, store.session?.token?.reveal())
        assertTrue(server.takeRequest().body.readUtf8().contains("Salon TV"))
        assertTrue(server.takeRequest().body.readUtf8().contains(deviceCode))
        server.takeRequest()
        val meRequest = server.takeRequest()
        assertEquals("/api/me", meRequest.path)
        assertEquals("Bearer $token", meRequest.getHeader("Authorization"))
    }

    @Test fun `slow_down makes the poll wait longer each time`() = runBlocking {
        server.enqueue(linkStart())
        server.enqueue(json(429, """{"error":"slow_down"}""").setHeader("Retry-After", "3"))
        server.enqueue(json(428, """{"error":"pending"}"""))
        server.enqueue(json(429, """{"error":"slow_down"}""").setHeader("Retry-After", "3"))
        server.enqueue(json(200, """{"token":"$token","sessionId":"s1"}"""))
        server.enqueue(me())
        val account = account()
        val code = (account.startLink("TV") as AccountResult.Ok).value
        assertTrue(account.awaitLink(code) is LinkOutcome.Linked)
        // 3 s, then 3 + 5 after the first slow_down (kept while pending), then 8 + 5 after the second.
        assertEquals(listOf(3_000L, 8_000L, 8_000L, 13_000L), sleeps)
    }

    @Test fun `410 and the code's own time both end as expired`() = runBlocking {
        server.enqueue(linkStart())
        server.enqueue(json(410, """{"error":"expired"}"""))
        val account = account()
        val code = (account.startLink("TV") as AccountResult.Ok).value
        assertEquals(LinkOutcome.Expired, account.awaitLink(code))

        server.enqueue(linkStart(expiresIn = 2))
        val short = (account.startLink("TV") as AccountResult.Ok).value
        val polls = server.requestCount
        assertEquals(LinkOutcome.Expired, account.awaitLink(short))
        assertEquals("no poll once the code ran out", polls, server.requestCount)
    }

    @Test fun `a network hiccup while polling is retried with a growing wait`() = runBlocking {
        server.enqueue(linkStart())
        server.enqueue(json(503, """{"error":"internal_error"}"""))
        server.enqueue(json(200, """{"token":"$token","sessionId":"s1"}"""))
        server.enqueue(me())
        val account = account()
        val code = (account.startLink("TV") as AccountResult.Ok).value
        assertTrue(account.awaitLink(code) is LinkOutcome.Linked)
        assertEquals(listOf(3_000L, 6_000L), sleeps)
    }

    @Test fun `sign-in refusals become plain words`() = runBlocking {
        val account = account()
        server.enqueue(json(401, """{"error":"invalid_credentials"}"""))
        val wrong = account.login("Ana", "not the passphrase", "PC") as AccountResult.Failed
        assertEquals("invalid_credentials", wrong.problem.code)
        assertEquals("Wrong name or passphrase.", wrong.problem.text())

        server.enqueue(json(429, """{"error":"too_many_attempts"}""").setHeader("Retry-After", "600"))
        val many = account.login("Ana", "still not it!", "PC") as AccountResult.Failed
        assertEquals("Too many tries. Wait 10 min, then try again.", many.problem.text())

        server.enqueue(json(400, """{"error":"weak_passphrase"}"""))
        val weak = account.setupOwner("Ana", "short", "PC") as AccountResult.Failed
        assertEquals("The passphrase needs at least 12 characters.", weak.problem.text())

        server.enqueue(json(409, """{"error":"already_set_up"}"""))
        assertEquals("already_set_up", (account.setupOwner("Ana", "long enough phrase", "PC") as AccountResult.Failed).problem.code)

        server.enqueue(json(403, """{"error":"access_denied"}"""))
        assertTrue((account.login("Ana", "long enough phrase", "TV") as AccountResult.Failed).problem.text().contains("refuses this device"))

        Strings.applyLanguage("fr")
        server.enqueue(json(401, """{"error":"invalid_credentials"}"""))
        assertEquals("Nom ou phrase secrète incorrects.", (account.login("Ana", "x", "PC") as AccountResult.Failed).problem.text())
        Strings.applyLanguage("en")

        server.shutdown()
        val down = account.login("Ana", "long enough phrase", "PC") as AccountResult.Failed
        assertEquals("unreachable", down.problem.code)
        assertNull(account.session())
    }

    @Test fun `login keeps the session and a later 401 forgets it`() = runBlocking {
        server.enqueue(json(200, """{"token":"$token","sessionId":"s9","user":{"id":"u1","name":"Ana","role":"member"}}"""))
        val account = account()
        val session = (account.login("Ana", "a long passphrase", "PC") as AccountResult.Ok).value
        assertEquals("s9", session.sessionId)
        assertFalse(session.user!!.owner)
        val sent = server.takeRequest().body.readUtf8()
        assertTrue(sent.contains("\"deviceName\":\"PC\""))

        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        val me = account.me() as AccountResult.Failed
        assertEquals("unauthorized", me.problem.code)
        assertNull(account.session())
    }

    @Test fun `the token never shows in text`() = runBlocking {
        server.enqueue(json(200, """{"token":"$token","sessionId":"s9","user":{"id":"u1","name":"Ana","role":"owner"}}"""))
        val store = MemoryAccountStore(base())
        val account = account(store)
        val session = (account.login("Ana", "my secret passphrase", "PC") as AccountResult.Ok)
        server.enqueue(linkStart())
        val code = account.startLink("TV")
        val texts = listOf(session.toString(), session.value.toString(), store.session.toString(), account.toString(), code.toString(), Secret(token).toString())
        texts.forEach { text ->
            assertFalse(text, text.contains(token))
            assertFalse(text, text.contains(deviceCode))
            assertFalse(text, text.contains("my secret passphrase"))
        }
        server.shutdown()
        val failed = account.sessions() as AccountResult.Failed
        assertFalse(failed.toString().contains(token))
        assertFalse(failed.problem.text().contains(token))
    }

    @Test fun `revoking another device, then this one, which is a sign-out`() = runBlocking {
        val store = MemoryAccountStore(base(), StoredSession(Secret(token), "s1", null))
        val account = account(store)
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(account.revoke("s2") is AccountResult.Ok)
        assertEquals("DELETE", server.takeRequest().method)
        assertNotNull(account.session())

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(account.revoke("s1") is AccountResult.Ok)
        assertEquals("/api/auth/logout", server.takeRequest().path)
        assertNull(account.session())
    }

    @Test fun `sign-out forgets the session even when the server is down`() = runBlocking {
        val store = MemoryAccountStore(base(), StoredSession(Secret(token), "s1", null))
        val account = account(store)
        server.shutdown()
        account.logout()
        assertNull(store.session)
    }

    @Test fun `the server address is checked and completed`() = runBlocking {
        assertEquals("http://192.168.1.20:25570", SokoulAccount.normalizeServer("192.168.1.20"))
        assertEquals("http://192.168.1.20:8080", SokoulAccount.normalizeServer(" 192.168.1.20:8080/ "))
        assertEquals("https://sokoul.example.com", SokoulAccount.normalizeServer("https://sokoul.example.com/"))
        assertEquals("http://127.0.0.1:25570", SokoulAccount.normalizeServer("http://127.0.0.1:25570"))
        assertNull(SokoulAccount.normalizeServer("not an address"))
        assertNull(SokoulAccount.normalizeServer(""))

        val store = MemoryAccountStore()
        val account = account(store)
        server.enqueue(json(200, """{"status":"ok","version":"0.1.0"}"""))
        assertTrue(account.setServer(base()) is AccountResult.Ok)
        assertEquals(base(), store.serverUrl)
        server.enqueue(json(200, """<html>router</html>"""))
        assertEquals("not_sokoul", (account.setServer(server.url("/other").toString()) as AccountResult.Failed).problem.code)
        assertEquals(base(), store.serverUrl)
    }

    @Test fun `the database keeps the token like a service key`() {
        ProviderConfig.builtIn.let { saved ->
            ProviderConfig.builtIn = { emptyMap() }
            try {
                DesktopDb(Files.createTempDirectory("sokoul-account").toFile().resolve("t.db")).use { db ->
                    val store = DbAccountStore(db)
                    store.serverUrl = "http://192.168.0.10:25570/"
                    store.session = StoredSession(Secret(token), "s1", com.sokoul.pc.account.AccountUser("u1", "Ana", "owner"))
                    assertEquals("http://192.168.0.10:25570", DbAccountStore(db).serverUrl)
                    assertEquals(token, ProviderConfig.key(db, DbAccountStore.SESSION_KEY))
                    assertEquals("Ana", DbAccountStore(db).session?.user?.name)
                    assertNull("the old Mainapi address is untouched", ProviderConfig.key(db, "mainapi_url"))
                    store.session = null
                    assertNull(DbAccountStore(db).session)
                    assertNull(ProviderConfig.key(db, DbAccountStore.SESSION_KEY))
                }
            } finally {
                ProviderConfig.builtIn = saved
            }
        }
    }

    // --- panel model ----------------------------------------------------------------------------------------------

    private fun model(account: SokoulAccount, keyboard: Boolean = false, serverPc: Boolean = false) =
        AccountPanelModel(account, CoroutineScope(Dispatchers.Unconfined), keyboard, "Salon TV", serverPc, clock = { now }, retryWait = { })

    private fun waitFor(model: AccountPanelModel, what: String, ok: (AccountView) -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!ok(model.view)) {
            check(System.currentTimeMillis() < until) { "$what: still ${model.view}" }
            Thread.sleep(10)
        }
    }

    @Test fun `without an address the panel asks for one, prefilled on the server's PC`() {
        val tv = model(account(MemoryAccountStore()))
        tv.open()
        assertEquals(AccountView.NeedsServer(""), tv.view)
        val pc = model(account(MemoryAccountStore()), keyboard = true, serverPc = true)
        pc.open()
        assertEquals(AccountView.NeedsServer(ProviderConfig.SOKOUL_SERVER_LOCAL_URL), pc.view)
    }

    @Test fun `an expired code is replaced by a new one, then the TV shows who is connected`() {
        server.enqueue(linkStart("BCDF-GHJK"))
        server.enqueue(json(410, """{"error":"expired"}"""))
        server.enqueue(linkStart("MNPQ-RSTV"))
        server.enqueue(json(200, """{"token":"$token","sessionId":"s1"}"""))
        server.enqueue(me())
        server.enqueue(devices())
        val model = model(account())
        model.open()
        assertEquals(AccountView.SignedOut(), model.view)
        model.startLinking()
        waitFor(model, "linked") { it is AccountView.SignedIn }
        val signedIn = model.view as AccountView.SignedIn
        assertTrue(signedIn.justConnected)
        assertEquals("Ana", signedIn.user.name)
        assertEquals(3, signedIn.devices.size)
        val starts = (1..server.requestCount).map { server.takeRequest().path }.count { it == "/api/link/start" }
        assertEquals(2, starts)
    }

    @Test fun `a failed sign-in stays on the form with the reason`() {
        server.enqueue(json(401, """{"error":"invalid_credentials"}"""))
        val model = model(account(), keyboard = true)
        model.open()
        model.showSignIn()
        model.signIn("Ana", "wrong passphrase")
        waitFor(model, "refused") { (it as? AccountView.SignIn)?.problem != null }
        assertEquals("invalid_credentials", (model.view as AccountView.SignIn).problem?.code)
    }

    @Test fun `owner setup is offered only on the server's PC`() {
        val tv = model(account())
        tv.showSignIn(owner = true)
        assertEquals(AccountView.SignIn(owner = false), tv.view)
        val pc = model(account(), keyboard = true, serverPc = true)
        pc.showSignIn(owner = true)
        assertEquals(AccountView.SignIn(owner = true), pc.view)
    }

    @Test fun `signed in, a device is revoked and the list is read again, then sign-out`() {
        val store = MemoryAccountStore(base(), StoredSession(Secret(token), "s1", null))
        server.enqueue(me())
        server.enqueue(devices())
        val model = model(account(store))
        model.open()
        waitFor(model, "signed in") { it is AccountView.SignedIn }
        assertFalse((model.view as AccountView.SignedIn).justConnected)

        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(me())
        server.enqueue(json(200, """{"sessions":[{"id":"s1","deviceName":"Salon TV","createdAt":1,"lastUsedAt":$now,"current":true}]}"""))
        model.revoke("s2")
        waitFor(model, "one device left") { (it as? AccountView.SignedIn)?.devices?.size == 1 }

        server.enqueue(MockResponse().setResponseCode(204))
        model.signOut()
        waitFor(model, "signed out") { it is AccountView.SignedOut }
        assertNull(store.session)
    }

    @Test fun `a session revoked elsewhere opens on the signed-out view`() {
        val store = MemoryAccountStore(base(), StoredSession(Secret(token), "s1", null))
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        val model = model(account(store))
        model.open()
        waitFor(model, "signed out") { it is AccountView.SignedOut }
        assertEquals("unauthorized", (model.view as AccountView.SignedOut).problem?.code)
    }

    @Test fun `last use in words`() {
        assertEquals("Used today", lastUsedText(now, now))
        assertEquals("Used yesterday", lastUsedText(now - 86_400_000L, now))
        assertEquals("Used 3 days ago", lastUsedText(now - 3 * 86_400_000L, now))
    }
}
