package com.sokoul.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("com.sokoul.server.Main")

fun main() {
    val config = try {
        ServerConfig.load()
    } catch (e: IllegalArgumentException) {
        log.error("Invalid server configuration: {}", e.message)
        exitProcess(2)
    }
    val db = Database.open(config.dataDir)
    log.info("Database {} at schema {}", db.file.name, db.schemaVersion())
    log.info(
        "Private access: {}",
        config.access?.let { "Cloudflare Access (${it.teamDomain})" }
            ?: if (config.lan) "loopback and private LAN only" else "loopback only",
    )

    val services = Services(db)
    if (!services.accounts.hasUsers()) {
        log.info("No account yet: create the owner from this PC with POST /api/setup (see docs/server.md)")
    }

    val server = embeddedServer(Netty, port = config.port, host = config.bind) {
        module(config, services)
    }
    Runtime.getRuntime().addShutdownHook(Thread({
        log.info("Stopping Sokoul server")
        server.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
    }, "sokoul-server-shutdown"))

    server.start(wait = false)
    log.info("Sokoul server on http://{}:{}", config.bind, config.port)
    Thread.currentThread().join()
}
