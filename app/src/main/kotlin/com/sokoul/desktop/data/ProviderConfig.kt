package com.sokoul.desktop.data

data class ProviderStatus(val id: String, val label: String, val configured: Boolean, val source: String?, val hint: String?)

/**
 * Clés des services externes : celle saisie dans Réglages (base protégée par ACL) l'emporte, sinon la clé intégrée
 * au build ([DesktopKeys], depuis `local.properties`). Jamais affichée en entier ni journalisée.
 */
object ProviderConfig {
    private const val ANIME_BACKEND_SETTING = "anime_backend_url"
    val providers = listOf(
        "tmdb" to "TMDB",
        "realdebrid" to "Real-Debrid",
        "omdb" to "OMDb",
        "fanart" to "fanart.tv",
        "tvdb" to "TheTVDB",
        "tvmaze" to "TVmaze",
        "watchmode" to "Watchmode",
        "tastedive" to "TasteDive",
        "motn" to "Movie of the Night",
        "opensubtitles" to "OpenSubtitles",
        "subdl" to "Subdl",
        // The Sokoul server (Mainapi): its address, and the access key it gives. On the PC that runs it, the defaults below are enough.
        "mainapi_url" to "Sokoul server address",
        "mainapi" to "Sokoul server key",
    )

    /** Where the Sokoul server is when no address was typed: this PC. A TV needs the PC's address on the network. */
    const val MAINAPI_DEFAULT_URL = "http://127.0.0.1:25565"

    /** Remplaçable dans les tests. */
    internal var builtIn: () -> Map<String, String> = { DesktopKeys.all }

    private fun settingKey(id: String) = "api_key_$id"

    fun statuses(db: DesktopDb): List<ProviderStatus> = providers.map { (id, label) ->
        val override = db.setting(settingKey(id))?.takeIf { it.isNotBlank() }
        val built = builtIn()[id]?.takeIf { it.isNotBlank() }
        ProviderStatus(
            id = id, label = label, configured = override != null || built != null,
            // Shown in Settings: translated like the rest of the interface.
            source = when { override != null -> com.sokoul.pc.data.L("Key entered", "Clé saisie").text(); built != null -> com.sokoul.pc.data.L("Built-in key", "Clé intégrée").text(); else -> null },
            hint = (override ?: built)?.let(::hint),
        )
    }

    fun key(db: DesktopDb, id: String): String? =
        db.setting(settingKey(id))?.takeIf { it.isNotBlank() } ?: builtIn()[id]?.takeIf { it.isNotBlank() }

    /** Clé saisie dans Réglages ; vide = revenir à la clé intégrée. */
    fun setKey(db: DesktopDb, id: String, value: String?) = db.putSetting(settingKey(id), value?.trim()?.takeIf { it.isNotEmpty() })

    /** The new private Sokoul server (accounts, devices, later sync): its address, kept apart from the old Mainapi's. */
    private const val SOKOUL_SERVER_SETTING = "sokoul_server_url"

    /** Where the Sokoul server listens on the PC that runs it (docs/server.md). */
    const val SOKOUL_SERVER_LOCAL_URL = "http://127.0.0.1:25570"

    fun sokoulServerUrl(db: DesktopDb): String? = key(db, SOKOUL_SERVER_SETTING)?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    fun setSokoulServerUrl(db: DesktopDb, value: String?) = setKey(db, SOKOUL_SERVER_SETTING, value?.trim()?.trimEnd('/'))

    /** Optional AniProject-compatible backend; kept separate from Sokoul Mainapi's unrelated routes. */
    fun animeBackendUrl(db: DesktopDb): String? = db.setting(settingKey(ANIME_BACKEND_SETTING))?.trim()?.takeIf { it.isNotEmpty() }

    fun setAnimeBackendUrl(db: DesktopDb, value: String?) = db.putSetting(settingKey(ANIME_BACKEND_SETTING), value?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() })

    /** « •••• 0136 » : seulement les 4 derniers caractères. */
    fun hint(key: String): String = "•••• " + key.takeLast(4)
}
