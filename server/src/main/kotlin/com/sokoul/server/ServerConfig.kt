package com.sokoul.server

import java.io.File
import java.util.Properties

/**
 * Start-up settings of the server.
 *
 * Read from a properties file (path in `SOKOUL_SERVER_CONFIG`, default `%LOCALAPPDATA%\SokoulServer\server.properties`
 * on Windows and `~/.sokoul-server/server.properties` elsewhere), then overridden by environment variables:
 *
 * | key                 | env                                  | default                       |
 * |---------------------|--------------------------------------|-------------------------------|
 * | `port`              | `SOKOUL_SERVER_PORT`                 | 25570                         |
 * | `bind`              | `SOKOUL_SERVER_BIND`                 | 127.0.0.1 (0.0.0.0 with lan)  |
 * | `lan`               | `SOKOUL_SERVER_LAN`                  | false                         |
 * | `dataDir`           | `SOKOUL_SERVER_DATA_DIR`             | the config file's folder      |
 * | `access.teamDomain` | `SOKOUL_SERVER_ACCESS_TEAM_DOMAIN`   | unset (local-only mode)       |
 * | `access.audience`   | `SOKOUL_SERVER_ACCESS_AUDIENCE`      | unset (local-only mode)       |
 * | `vault.keyFile`     | `SOKOUL_SERVER_VAULT_KEY_FILE`       | `vault.key` next to the config |
 *
 * [toString] never prints the Access audience; nothing here should reach a log in clear.
 */
class ServerConfig(
    val port: Int,
    val bind: String,
    val lan: Boolean,
    val dataDir: File,
    val access: AccessConfig?,
    val configFile: File?,
    /** The credentials vault's master key (32 bytes, owner-only). Keep it out of the data folder's backups. */
    val vaultKeyFile: File,
) {
    /** Cloudflare Access settings; present only when both the team domain and the audience are set. */
    class AccessConfig(val teamDomain: String, val audience: String) {
        val issuer: String get() = "https://$teamDomain"
        val certsUrl: String get() = "https://$teamDomain/cdn-cgi/access/certs"
        override fun toString(): String = "AccessConfig(teamDomain=$teamDomain, audience=<redacted>)"
    }

    override fun toString(): String =
        "ServerConfig(port=$port, bind=$bind, lan=$lan, dataDir=$dataDir, access=${access ?: "off"})"

    companion object {
        const val DEFAULT_PORT = 25570
        const val DEFAULT_BIND = "127.0.0.1"
        const val LAN_BIND = "0.0.0.0"
        const val VAULT_KEY_FILE_NAME = "vault.key"

        private val ENV = mapOf(
            "port" to "SOKOUL_SERVER_PORT",
            "bind" to "SOKOUL_SERVER_BIND",
            "lan" to "SOKOUL_SERVER_LAN",
            "dataDir" to "SOKOUL_SERVER_DATA_DIR",
            "access.teamDomain" to "SOKOUL_SERVER_ACCESS_TEAM_DOMAIN",
            "access.audience" to "SOKOUL_SERVER_ACCESS_AUDIENCE",
            "vault.keyFile" to "SOKOUL_SERVER_VAULT_KEY_FILE",
        )

        private val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

        /** The default config file for this OS, from `SOKOUL_SERVER_CONFIG` first. */
        fun defaultConfigFile(
            env: Map<String, String> = System.getenv(),
            osName: String = System.getProperty("os.name").orEmpty(),
            userHome: String = System.getProperty("user.home").orEmpty(),
        ): File {
            env["SOKOUL_SERVER_CONFIG"]?.takeIf { it.isNotBlank() }?.let { return File(it) }
            val windows = osName.startsWith("Windows", ignoreCase = true)
            val localAppData = env["LOCALAPPDATA"]?.takeIf { it.isNotBlank() }
            return if (windows && localAppData != null) {
                File(File(localAppData, "SokoulServer"), "server.properties")
            } else {
                File(File(userHome, ".sokoul-server"), "server.properties")
            }
        }

        /** Loads the config file (missing file = all defaults) and applies environment overrides. */
        fun load(
            env: Map<String, String> = System.getenv(),
            osName: String = System.getProperty("os.name").orEmpty(),
            userHome: String = System.getProperty("user.home").orEmpty(),
        ): ServerConfig {
            val file = defaultConfigFile(env, osName, userHome)
            val props = Properties()
            if (file.isFile) file.reader(Charsets.UTF_8).use { props.load(it) }
            return from(props, env, file)
        }

        /** Builds a config from [props] with [env] overrides. Throws [IllegalArgumentException] on bad values. */
        fun from(props: Properties, env: Map<String, String> = emptyMap(), configFile: File? = null): ServerConfig {
            fun value(key: String): String? =
                (env[ENV.getValue(key)] ?: props.getProperty(key))?.trim()?.takeIf { it.isNotEmpty() }

            val port = value("port")?.let { raw ->
                raw.toIntOrNull()?.takeIf { it in 1..65535 } ?: throw IllegalArgumentException("port must be 1-65535")
            } ?: DEFAULT_PORT
            val lan = value("lan")?.let { raw ->
                when (raw.lowercase()) {
                    "true", "yes", "1", "on" -> true
                    "false", "no", "0", "off" -> false
                    else -> throw IllegalArgumentException("lan must be true or false")
                }
            } ?: false
            val bind = value("bind") ?: if (lan) LAN_BIND else DEFAULT_BIND
            val dataDir = value("dataDir")?.let(::File)
                ?: configFile?.absoluteFile?.parentFile
                ?: File(System.getProperty("user.home"), ".sokoul-server")

            val teamDomain = value("access.teamDomain")
                ?.lowercase()?.removePrefix("https://")?.trimEnd('/')
            val audience = value("access.audience")
            val access = when {
                teamDomain == null && audience == null -> null
                teamDomain == null || audience == null ->
                    throw IllegalArgumentException("access.teamDomain and access.audience must be set together")
                !HOST.matches(teamDomain) -> throw IllegalArgumentException("access.teamDomain must be a host name")
                else -> AccessConfig(teamDomain, audience)
            }
            val vaultKeyFile = value("vault.keyFile")?.let(::File)
                ?: File(configFile?.absoluteFile?.parentFile ?: dataDir, VAULT_KEY_FILE_NAME)
            return ServerConfig(port, bind, lan, dataDir, access, configFile, vaultKeyFile.absoluteFile)
        }
    }
}
