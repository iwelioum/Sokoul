package com.sokoul.pc.startup.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sokoul.desktop.data.AudioPreference
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.desktop.data.Playlist
import com.sokoul.desktop.data.PlaylistType
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.desktop.data.RealDebridAuth
import com.sokoul.desktop.data.Settings
import com.sokoul.credentials.ServiceCheck
import com.sokoul.iptv.data.debrid.RealDebridOAuth
import com.sokoul.pc.account.AccountResult
import com.sokoul.pc.account.AccountVault
import com.sokoul.pc.data.CredentialValidator
import com.sokoul.pc.data.Verdict
import com.sokoul.pc.startup.SetupState
import com.sokoul.pc.startup.StartupRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The steps of the first launch, in order. Stored so that leaving the app in the middle resumes at the same place. */
enum class OnboardingStep { Welcome, Sources, Preferences, Ready }

/** Where a source stands: what the card shows on the right. */
enum class SourceStatus { Empty, Checking, Ready, Problem }

/** One source as the cards show it: its state, a one-line summary once ready, a plain-language problem otherwise. */
data class SourceView(val status: SourceStatus, val summary: String? = null, val problem: String? = null)

enum class SourceKind { Iptv, RealDebrid, Subtitles, Server, Advanced }

/** The Real-Debrid device sign-in as the sheet shows it. */
sealed interface DeviceLink {
    data object Idle : DeviceLink
    data object Starting : DeviceLink
    data class Waiting(val userCode: String, val verificationUrl: String, val qrUrl: String, val expiresAt: Long) : DeviceLink
    data object Expired : DeviceLink
    data class Failed(val message: String) : DeviceLink
}

/**
 * State and rules of the first launch. Nothing is stored before the service accepted it, typed secrets are never shown
 * back, and every source is optional: [finish] works with none.
 */
class OnboardingModel(
    private val db: DesktopDb,
    private val validator: CredentialValidator,
    private val oauth: RealDebridOAuth,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    /** The signed-in account's vault: accepted keys are saved there too, and its masks show what the account has. */
    private val vault: AccountVault? = null,
) {
    private val prefs = DesktopPrefs(db)
    private val rdAuth = RealDebridAuth(db, oauth)

    var step by mutableStateOf(OnboardingStep.entries.firstOrNull { it.name == db.setting(STEP_SETTING) } ?: OnboardingStep.Welcome)
        private set

    val sources = mutableStateMapOf<SourceKind, SourceView>()
    var deviceLink by mutableStateOf<DeviceLink>(DeviceLink.Idle)
        private set
    var audio by mutableStateOf(prefs.audioPreference)
        private set
    var trailers by mutableStateOf(prefs.autoTrailers)
        private set
    private var linkJob: Job? = null

    /** The masks of the keys the account's vault holds, by service: empty signed out, offline, or with no vault. */
    val vaultHints = mutableStateMapOf<String, String>()

    init {
        refreshSaved()
        refreshVault()
    }

    /** Reads which keys the account has (never the keys). Quiet when it fails: the page simply shows none. */
    fun refreshVault() {
        val v = vault?.takeIf { it.signedIn } ?: return
        scope.launch {
            val listed = (v.list() as? AccountResult.Ok)?.value ?: return@launch
            vaultHints.clear()
            listed.forEach { (service, entry) -> vaultHints[service] = entry.hint }
        }
    }

    /** After this device's check accepted [value], the account keeps it too; the server checks it again first. */
    private fun saveToVault(service: String, value: String) {
        val v = vault?.takeIf { it.signedIn } ?: return
        scope.launch {
            (v.save(service, value) as? AccountResult.Ok)?.value?.let { vaultHints[service] = it.hint }
        }
    }

    fun goTo(next: OnboardingStep) {
        step = next
        db.putSetting(STEP_SETTING, next.name)
    }

    fun next() = OnboardingStep.entries.getOrNull(step.ordinal + 1)?.let(::goTo)

    /** Back one step; false on the first one (the caller then leaves the screen). */
    fun back(): Boolean {
        val previous = OnboardingStep.entries.getOrNull(step.ordinal - 1) ?: return false
        goTo(previous)
        return true
    }

    /** Leaves the first launch: the catalogue opens with whatever is ready. */
    fun finish() {
        cancelDeviceLink()
        db.putSetting(STEP_SETTING, null)
        StartupRoute.set(db, SetupState.Done)
    }

    /** Leaves without finishing: the next launch opens the catalogue too, and Settings keeps every source. */
    fun later() {
        cancelDeviceLink()
        StartupRoute.set(db, SetupState.Deferred)
    }

    // ---- IPTV ---------------------------------------------------------------------------------------------------

    private fun activePlaylist(): Playlist? {
        val all = db.playlists()
        val active = db.setting(Settings.ACTIVE_PLAYLIST)?.toLongOrNull()
        return all.firstOrNull { it.id == active } ?: all.firstOrNull()
    }

    suspend fun saveXtream(server: String, username: String, password: String): Boolean {
        val url = normalizeServer(server) ?: return problem(SourceKind.Iptv, Copy.serverFormat)
        if (username.isBlank() || password.isBlank()) return problem(SourceKind.Iptv, Copy.iptvIncomplete)
        sources[SourceKind.Iptv] = SourceView(SourceStatus.Checking)
        val check = validator.iptvAccount(url, username.trim(), password, activePlaylist()?.userAgent)
        return when (check.verdict) {
            Verdict.Accepted -> {
                val saved = activePlaylist()
                val id = db.upsertPlaylist(Playlist(saved?.id ?: 0, saved?.name ?: "IPTV", PlaylistType.XTREAM, url, username.trim(), password, saved?.epgUrl, saved?.userAgent))
                db.putSetting(Settings.ACTIVE_PLAYLIST, id.toString())
                sources[SourceKind.Iptv] = SourceView(SourceStatus.Ready, Copy.iptvSummary(check.expiresAt, check.maxConnections, check.trial))
                saveToVault(ServiceCheck.IPTV, ServiceCheck.xtreamValue(url, username.trim(), password))
                true
            }
            Verdict.Refused -> problem(SourceKind.Iptv, Copy.iptvRefused)
            Verdict.Unreachable -> problem(SourceKind.Iptv, Copy.serverUnreachable)
        }
    }

    suspend fun saveM3u(link: String): Boolean {
        val url = link.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return problem(SourceKind.Iptv, Copy.m3uFormat)
        sources[SourceKind.Iptv] = SourceView(SourceStatus.Checking)
        return when (validator.m3u(url)) {
            Verdict.Accepted -> {
                val saved = activePlaylist()
                val id = db.upsertPlaylist(Playlist(saved?.id ?: 0, saved?.name ?: "IPTV", PlaylistType.M3U, url, null, null, saved?.epgUrl, saved?.userAgent))
                db.putSetting(Settings.ACTIVE_PLAYLIST, id.toString())
                sources[SourceKind.Iptv] = SourceView(SourceStatus.Ready, Copy.m3uReady)
                saveToVault(ServiceCheck.IPTV, url)
                true
            }
            Verdict.Refused -> problem(SourceKind.Iptv, Copy.m3uRefused)
            Verdict.Unreachable -> problem(SourceKind.Iptv, Copy.linkUnreachable)
        }
    }

    // ---- Real-Debrid ----------------------------------------------------------------------------------------------

    /** Asks Real-Debrid for a code, shows it, and waits (in the background) for the person to confirm it on their phone. */
    fun startDeviceLink() {
        linkJob?.cancel()
        deviceLink = DeviceLink.Starting
        linkJob = scope.launch {
            val code = runCatching { oauth.deviceCode() }.getOrElse {
                deviceLink = DeviceLink.Failed(Copy.debridUnreachable)
                return@launch
            }
            val expiresAt = now() + code.expiresInSeconds * 1000L
            deviceLink = DeviceLink.Waiting(code.userCode, code.verificationUrl.removePrefix("https://"), code.directUrl ?: code.verificationUrl, expiresAt)
            while (now() < expiresAt) {
                delay(code.intervalSeconds * 1000L)
                val credentials = runCatching { oauth.credentials(code.deviceCode) }.getOrNull() ?: continue
                val token = runCatching { oauth.token(credentials, code.deviceCode) }.getOrElse {
                    deviceLink = DeviceLink.Failed(Copy.debridRefused)
                    return@launch
                }
                rdAuth.store(credentials, token, now())
                deviceLink = DeviceLink.Idle
                checkDebrid(token.accessToken)
                return@launch
            }
            deviceLink = DeviceLink.Expired
        }
    }

    fun cancelDeviceLink() {
        linkJob?.cancel()
        linkJob = null
        if (deviceLink !is DeviceLink.Idle) deviceLink = DeviceLink.Idle
    }

    /** The other way in: a private API token from real-debrid.com/apitoken. */
    suspend fun saveDebridToken(token: String): Boolean {
        if (token.isBlank()) return problem(SourceKind.RealDebrid, Copy.keyMissing)
        val ok = checkDebrid(token.trim())
        if (ok) {
            rdAuth.signOut()
            ProviderConfig.setKey(db, "realdebrid", token.trim())
            refreshSaved()
            saveToVault("realdebrid", token.trim())
        }
        return ok
    }

    private suspend fun checkDebrid(token: String): Boolean {
        sources[SourceKind.RealDebrid] = SourceView(SourceStatus.Checking)
        val check = validator.realDebrid(token, now())
        return when (check.verdict) {
            Verdict.Accepted -> {
                sources[SourceKind.RealDebrid] = SourceView(SourceStatus.Ready, Copy.debridSummary(check.username, check.premiumDaysLeft))
                true
            }
            Verdict.Refused -> problem(SourceKind.RealDebrid, Copy.debridRefused)
            Verdict.Unreachable -> problem(SourceKind.RealDebrid, Copy.debridUnreachable)
        }
    }

    // ---- Keys and the Sokoul server -------------------------------------------------------------------------------

    fun keyHint(id: String): String? = ProviderConfig.key(db, id)?.let(ProviderConfig::hint)

    /** Every service key with where it comes from (entered or built in) and its mask, for Settings › Sources and keys. */
    fun providerStatuses() = ProviderConfig.statuses(db)

    val keyErrors = mutableStateMapOf<String, String>()

    /** Checks one key with its service, then stores it. */
    suspend fun saveKey(id: String, key: String, kind: SourceKind): Boolean {
        keyErrors.remove(id)
        if (key.isBlank()) {
            keyErrors[id] = Copy.keyMissing
            return false
        }
        return when (validator.key(id, key.trim())) {
            Verdict.Accepted -> {
                ProviderConfig.setKey(db, id, key.trim())
                refreshSaved()
                saveToVault(id, key.trim())
                true
            }
            Verdict.Refused -> { keyErrors[id] = Copy.keyRefused; refreshSaved(); false }
            Verdict.Unreachable -> { keyErrors[id] = Copy.keyUnreachable; refreshSaved(); false }
        }.also { if (!it) sources[kind] = sources[kind] ?: SourceView(SourceStatus.Empty) }
    }

    suspend fun saveServer(address: String, key: String): Boolean {
        val url = normalizeServer(address, defaultPort = 25565) ?: return problem(SourceKind.Server, Copy.serverFormat)
        sources[SourceKind.Server] = SourceView(SourceStatus.Checking)
        if (validator.reachable(url) != Verdict.Accepted) return problem(SourceKind.Server, Copy.sokoulUnreachable)
        ProviderConfig.setKey(db, "mainapi_url", url)
        if (key.isNotBlank()) ProviderConfig.setKey(db, "mainapi", key.trim())
        sources[SourceKind.Server] = SourceView(SourceStatus.Ready, Copy.serverSummary(url))
        return true
    }

    // ---- Preferences ----------------------------------------------------------------------------------------------

    fun chooseAudio(a: AudioPreference) {
        audio = a
        prefs.audioPreference = a
    }

    fun chooseTrailers(on: Boolean) {
        trailers = on
        prefs.autoTrailers = on
    }

    /** What is already configured (a resumed launch, a built-in key, an import) shows as ready. */
    fun refreshSaved() {
        activePlaylist()?.let { p ->
            if (sources[SourceKind.Iptv]?.status != SourceStatus.Ready) {
                sources[SourceKind.Iptv] = SourceView(SourceStatus.Ready, if (p.type == PlaylistType.M3U) Copy.m3uReady else Copy.iptvSaved)
            }
        }
        if (sources[SourceKind.RealDebrid]?.status != SourceStatus.Ready && ProviderConfig.key(db, "realdebrid") != null) {
            sources[SourceKind.RealDebrid] = SourceView(SourceStatus.Ready, Copy.debridSaved)
        }
        val subtitles = listOf("opensubtitles", "subdl").count { ProviderConfig.key(db, it) != null }
        sources[SourceKind.Subtitles] = if (subtitles > 0) SourceView(SourceStatus.Ready, Copy.subtitlesReady) else SourceView(SourceStatus.Empty)
        if (sources[SourceKind.Server]?.status != SourceStatus.Ready) {
            ProviderConfig.key(db, "mainapi_url")?.let { sources[SourceKind.Server] = SourceView(SourceStatus.Ready, Copy.serverSummary(it)) }
        }
        val advanced = ADVANCED_KEYS.count { ProviderConfig.key(db, it.id) != null }
        sources[SourceKind.Advanced] = if (advanced > 0) SourceView(SourceStatus.Ready, Copy.advancedSummary(advanced, ADVANCED_KEYS.size)) else SourceView(SourceStatus.Empty)
    }

    fun readyCount(): Int = listOf(SourceKind.Iptv, SourceKind.RealDebrid, SourceKind.Server).count { sources[it]?.status == SourceStatus.Ready }

    private fun problem(kind: SourceKind, text: String): Boolean {
        sources[kind] = SourceView(SourceStatus.Problem, problem = text)
        return false
    }

    /** One advanced key, grouped by what it brings rather than by the provider's name. */
    data class AdvancedKey(val id: String, val provider: String, val benefit: com.sokoul.pc.data.L)

    companion object {
        const val STEP_SETTING = "pc_onboarding_step"

        val ADVANCED_KEYS = listOf(
            AdvancedKey("tmdb", "TMDB", Copy.benefitCatalogue),
            AdvancedKey("omdb", "OMDb", Copy.benefitRatings),
            AdvancedKey("fanart", "fanart.tv", Copy.benefitArtwork),
            AdvancedKey("tvdb", "TheTVDB", Copy.benefitEpisodes),
            AdvancedKey("tvmaze", "TVmaze", Copy.benefitEpisodes),
            AdvancedKey("watchmode", "Watchmode", Copy.benefitWhere),
            AdvancedKey("motn", "Movie of the Night", Copy.benefitWhere),
            AdvancedKey("tastedive", "TasteDive", Copy.benefitSimilar),
        )

        /** "my.server.tv:8080" → "http://my.server.tv:8080". Null when it cannot be an address. */
        fun normalizeServer(raw: String, defaultPort: Int? = null): String? {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty() || s.any { it.isWhitespace() }) return null
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            val host = s.substringAfter("://").substringBefore('/').substringBefore(':')
            if (host.isEmpty() || (!host.contains('.') && host != "localhost")) return null
            if (defaultPort != null && !s.substringAfter("://").substringBefore('/').contains(':')) s = "$s:$defaultPort"
            return s
        }
    }
}
