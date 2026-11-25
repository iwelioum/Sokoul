package com.sokoul.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class ServerConfigTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `defaults bind loopback on 25570 without Access`() {
        val c = ServerConfig.from(Properties(), emptyMap(), File(tmp.root, "server.properties"))
        assertEquals(25570, c.port)
        assertEquals("127.0.0.1", c.bind)
        assertFalse(c.lan)
        assertNull(c.access)
        assertEquals(tmp.root.absoluteFile, c.dataDir)
    }

    @Test fun `the vault key file sits next to the config unless set, even when the data folder moves`() {
        val config = File(tmp.root, "server.properties")
        assertEquals(File(tmp.root, "vault.key").absoluteFile, ServerConfig.from(props("dataDir" to "elsewhere"), emptyMap(), config).vaultKeyFile)
        val set = File(tmp.root, "keys/master.key").path
        assertEquals(File(set).absoluteFile, ServerConfig.from(props("vault.keyFile" to set), emptyMap(), config).vaultKeyFile)
        assertEquals(File("env.key").absoluteFile, ServerConfig.from(props("vault.keyFile" to set), mapOf("SOKOUL_SERVER_VAULT_KEY_FILE" to "env.key"), config).vaultKeyFile)
    }

    @Test fun `lan switches the bind address unless bind is set`() {
        assertEquals("0.0.0.0", ServerConfig.from(props("lan" to "true")).bind)
        assertEquals("192.168.0.10", ServerConfig.from(props("lan" to "true", "bind" to "192.168.0.10")).bind)
    }

    @Test fun `environment overrides the file`() {
        val c = ServerConfig.from(
            props("port" to "1234", "dataDir" to "fromFile"),
            mapOf("SOKOUL_SERVER_PORT" to "4321", "SOKOUL_SERVER_DATA_DIR" to "fromEnv", "SOKOUL_SERVER_LAN" to "yes"),
        )
        assertEquals(4321, c.port)
        assertEquals(File("fromEnv"), c.dataDir)
        assertTrue(c.lan)
    }

    @Test fun `config file comes from SOKOUL_SERVER_CONFIG, then LOCALAPPDATA on Windows, then the home folder`() {
        assertEquals(File("x/s.properties"), ServerConfig.defaultConfigFile(mapOf("SOKOUL_SERVER_CONFIG" to "x/s.properties"), "Linux", "/h"))
        assertEquals(File("C:/L/SokoulServer/server.properties"), ServerConfig.defaultConfigFile(mapOf("LOCALAPPDATA" to "C:/L"), "Windows 11", "C:/U"))
        assertEquals(File("/h/.sokoul-server/server.properties"), ServerConfig.defaultConfigFile(emptyMap(), "Mac OS X", "/h"))
    }

    @Test fun `load reads the file named by SOKOUL_SERVER_CONFIG`() {
        val file = File(tmp.root, "custom.properties").apply { writeText("port=30000\naccess.teamDomain=https://Team.cloudflareaccess.com/\naccess.audience=aud-secret-tag\n") }
        val c = ServerConfig.load(mapOf("SOKOUL_SERVER_CONFIG" to file.path), "Linux", tmp.root.path)
        assertEquals(30000, c.port)
        assertEquals("team.cloudflareaccess.com", c.access!!.teamDomain)
        assertEquals("https://team.cloudflareaccess.com", c.access!!.issuer)
        assertEquals("https://team.cloudflareaccess.com/cdn-cgi/access/certs", c.access!!.certsUrl)
    }

    @Test fun `toString redacts the audience`() {
        val c = ServerConfig.from(props("access.teamDomain" to "team.cloudflareaccess.com", "access.audience" to "aud-secret-tag"))
        assertFalse(c.toString().contains("aud-secret-tag"))
        assertFalse(c.access.toString().contains("aud-secret-tag"))
        assertTrue(c.toString().contains("team.cloudflareaccess.com"))
    }

    @Test fun `bad values are refused`() {
        assertThrows { ServerConfig.from(props("port" to "70000")) }
        assertThrows { ServerConfig.from(props("lan" to "maybe")) }
        assertThrows { ServerConfig.from(props("access.teamDomain" to "team.cloudflareaccess.com")) }
        assertThrows { ServerConfig.from(props("access.audience" to "a")) }
        assertThrows { ServerConfig.from(props("access.teamDomain" to "evil.com/path", "access.audience" to "a")) }
    }

    private fun assertThrows(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        throw AssertionError("Expected IllegalArgumentException")
    }

    private fun props(vararg pairs: Pair<String, String>) = Properties().apply { pairs.forEach { (k, v) -> setProperty(k, v) } }
}
