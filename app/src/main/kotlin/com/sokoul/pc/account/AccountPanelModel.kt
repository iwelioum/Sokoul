package com.sokoul.pc.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sokoul.desktop.data.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the "Sokoul account" panel shows. */
sealed interface AccountView {
    /** Opening: reading who is signed in. */
    data object Loading : AccountView

    /** No server address yet: one sentence about the server and the address field, prefilled on the PC that runs it. */
    data class NeedsServer(val address: String, val checking: Boolean = false, val problem: AccountProblem? = null) : AccountView

    /** Signed out: "Link this device", and "Sign in" where there is a keyboard. */
    /** [needsOwner]: on the server's own PC, whether it still waits for its owner (null: unknown, offer it anyway). */
    data class SignedOut(val problem: AccountProblem? = null, val needsOwner: Boolean? = null) : AccountView

    /** The code and its QR, waiting for a phone or computer to confirm. [code] is null while the server is asked for one. */
    data class Linking(val code: LinkCode? = null, val problem: AccountProblem? = null) : AccountView

    /** Name and passphrase (PC and tablet). [owner] creates the first account instead, on the server's own PC. */
    data class SignIn(val owner: Boolean = false, val busy: Boolean = false, val problem: AccountProblem? = null) : AccountView

    /** Who is signed in and every device of the account. [justConnected] says "Connected as …" once, after linking. */
    data class SignedIn(
        val user: AccountUser,
        val devices: List<DeviceSession>,
        val justConnected: Boolean = false,
        val problem: AccountProblem? = null,
    ) : AccountView
}

/**
 * The account panel's states and actions. A TV gets a code and a QR and never a keyboard; a PC or tablet may also sign
 * in by name. An expired code is replaced by a new one at once; leaving the panel ([close]) stops the polling.
 */
class AccountPanelModel(
    val account: SokoulAccount,
    private val scope: CoroutineScope,
    /** A keyboard is at hand (PC, tablet): the name and passphrase form is offered. */
    val keyboard: Boolean,
    /** How this device appears in the list of devices. */
    val deviceName: String,
    /** This may be the PC that runs the server: its local address is suggested and the first account can be created. */
    val serverPc: Boolean,
    val clock: () -> Long = System::currentTimeMillis,
    /** What this device is, for the words of the panel ("Link this TV"). */
    val kind: DeviceKind = DeviceKind.current(),
    /** The wait before asking again after a failed start of a link. */
    private val retryWait: suspend (Long) -> Unit = { delay(it) },
    /** The account sync's last result, for the "Last synced" line; null where nothing syncs (renders, tests). */
    val syncStatus: kotlinx.coroutines.flow.StateFlow<com.sokoul.pc.sync.SyncStatus>? = null,
) {
    var view by mutableStateOf<AccountView>(AccountView.Loading)
        private set

    private var job: Job? = null

    /** The server's address as typed by the person, for the address line and the QR caption. */
    val server: String? get() = account.server

    fun open() {
        cancel()
        when {
            account.server == null -> view = AccountView.NeedsServer(if (serverPc) ProviderConfig.SOKOUL_SERVER_LOCAL_URL else "")
            account.session() != null -> refresh()
            else -> showSignedOut()
        }
    }

    /** Back to the address, to point this device at another server. */
    fun changeServer() {
        cancel()
        view = AccountView.NeedsServer(account.server ?: if (serverPc) ProviderConfig.SOKOUL_SERVER_LOCAL_URL else "")
    }

    fun saveServer(address: String) = launch {
        view = AccountView.NeedsServer(address, checking = true)
        when (val r = account.setServer(address)) {
            is AccountResult.Ok -> if (account.session() != null) showSignedIn(justConnected = false) else showSignedOut()
            is AccountResult.Failed -> view = AccountView.NeedsServer(address, problem = r.problem)
        }
    }

    /** Shows a code and its QR, and keeps a valid one on screen until a phone confirms it or the panel closes. */
    fun startLinking() = launch {
        while (isActive) {
            view = AccountView.Linking()
            val code = when (val started = account.startLink(deviceName)) {
                is AccountResult.Failed -> {
                    view = AccountView.Linking(problem = started.problem)
                    retryWait(RETRY_MILLIS)
                    continue
                }
                is AccountResult.Ok -> started.value
            }
            view = AccountView.Linking(code)
            when (val outcome = account.awaitLink(code)) {
                is LinkOutcome.Linked -> {
                    showSignedIn(justConnected = true, known = outcome.session.user)
                    return@launch
                }
                LinkOutcome.Expired -> continue
                is LinkOutcome.Failed -> {
                    view = AccountView.Linking(problem = outcome.problem)
                    retryWait(RETRY_MILLIS)
                }
            }
        }
    }

    fun showSignIn(owner: Boolean = false) {
        cancel()
        view = AccountView.SignIn(owner = owner && serverPc)
    }

    /** Signed out; on the server's own PC, asks the server whether the owner account still has to be created. */
    private fun showSignedOut() {
        view = AccountView.SignedOut()
        // A side check, not the panel's current job: it must neither cancel nor be cancelled by the caller.
        if (serverPc) scope.launch {
            val needs = account.needsOwner()
            (view as? AccountView.SignedOut)?.let { if (it.problem == null) view = it.copy(needsOwner = needs) }
        }
    }

    fun backToSignedOut() {
        cancel()
        showSignedOut()
    }

    fun signIn(name: String, passphrase: String) = launch {
        val owner = (view as? AccountView.SignIn)?.owner == true
        view = AccountView.SignIn(owner, busy = true)
        val r = if (owner) account.setupOwner(name, passphrase, deviceName) else account.login(name, passphrase, deviceName)
        when (r) {
            is AccountResult.Ok -> showSignedIn(justConnected = true, known = r.value.user)
            is AccountResult.Failed -> view = AccountView.SignIn(owner, problem = r.problem)
        }
    }

    fun refresh() = launch { showSignedIn(justConnected = false) }

    /** Signs another device out (the row asked twice first). */
    fun revoke(sessionId: String) = launch {
        when (val r = account.revoke(sessionId)) {
            is AccountResult.Ok -> if (account.session() == null) showSignedOut() else showSignedIn(false)
            is AccountResult.Failed -> afterFailure(r.problem)
        }
    }

    fun signOut() = launch {
        account.logout()
        showSignedOut()
    }

    /** Renders and tests: shows [view] as it is, without asking the server. */
    fun preview(view: AccountView) {
        cancel()
        this.view = view
    }

    /** Leaving the panel: no more polling. */
    fun close() = cancel()

    /** Reads the devices, and who is signed in unless the sign-in just said it ([known]). */
    private suspend fun showSignedIn(justConnected: Boolean, known: AccountUser? = null) {
        val user = known ?: when (val me = account.me()) {
            is AccountResult.Failed -> return afterFailure(me.problem)
            is AccountResult.Ok -> me.value
        }
        val devices = account.sessions()
        view = when (devices) {
            is AccountResult.Ok -> AccountView.SignedIn(user, devices.value, justConnected)
            is AccountResult.Failed -> AccountView.SignedIn(user, emptyList(), justConnected, devices.problem)
        }
    }

    /** A signed-out session ends on the signed-out view; anything else keeps what is known, with the problem. */
    private fun afterFailure(problem: AccountProblem) {
        val stored = account.session()
        view = when {
            stored == null -> AccountView.SignedOut(problem)
            else -> {
                val current = view as? AccountView.SignedIn
                val user = current?.user ?: stored.user
                if (user == null) AccountView.SignedOut(problem) else AccountView.SignedIn(user, current?.devices.orEmpty(), problem = problem)
            }
        }
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        cancel()
        job = scope.launch(block = block)
    }

    private fun cancel() {
        job?.cancel()
        job = null
    }

    companion object {
        const val RETRY_MILLIS = 10_000L
    }
}

/** The kind of device the panel runs on. */
enum class DeviceKind {
    Tv, Tablet, Pc;

    companion object {
        fun current(): DeviceKind = when {
            !com.sokoul.pc.Platform.IS_TV -> Pc
            com.sokoul.pc.ui.Form.tablet -> Tablet
            else -> Tv
        }
    }
}

/** "Salon TV" is what the person sees in the list of devices: the TV's model, or the PC's name. */
fun defaultDeviceName(): String {
    if (com.sokoul.pc.Platform.IS_TV) {
        // "Dalvik/2.1.0 (Linux; U; Android 14; Chromecast Build/UTTK.250729.004)" → "Chromecast"
        val model = System.getProperty("http.agent")?.let { Regex(";\\s*([^;]+?)\\s+Build/").find(it)?.groupValues?.get(1) }
        return model?.takeIf { it.isNotBlank() } ?: if (com.sokoul.pc.ui.Form.tablet) "Sokoul tablet" else "Sokoul TV"
    }
    return (System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME"))?.takeIf { it.isNotBlank() } ?: "Sokoul PC"
}
