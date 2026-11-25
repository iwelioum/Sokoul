package com.sokoul.pc.dev

import com.sokoul.desktop.data.DesktopDb
import com.sokoul.pc.account.AccountResult
import com.sokoul.pc.account.MemoryAccountStore
import com.sokoul.pc.account.SokoulAccount
import com.sokoul.pc.sync.HttpSyncTransport
import com.sokoul.pc.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import kotlin.system.exitProcess

/**
 * "Nothing local is lost" on real data (task 7.2): a COPY of a device's database is uploaded to a Sokoul server by its
 * first sync, then an empty database signed in to the same account downloads it; what each side then holds is compared
 * per kind of item. Never point it at the live database. Prints counts only.
 *
 * Arguments: <copy of sokoul.db> <empty db path> <server url> <account name> <passphrase>
 */
fun main(args: Array<String>): Unit = runBlocking {
    require(args.size == 5) { "usage: <copy db> <empty db> <server> <name> <passphrase>" }
    val (copyPath, freshPath, server, name, passphrase) = args
    require(!copyPath.replace('\\', '/').contains("/Roaming/SokoulPC/")) { "refusing the live database: use a copy" }
    val http = OkHttpClient()

    suspend fun syncOnce(path: String, device: String): DesktopDb {
        val db = DesktopDb(File(path))
        val account = SokoulAccount(MemoryAccountStore(server), http)
        when (val r = account.login(name, passphrase, device)) {
            is AccountResult.Failed -> error("login failed on $device: ${r.problem.code}")
            is AccountResult.Ok -> Unit
        }
        val engine = SyncEngine(db, HttpSyncTransport(account), userId = { account.session()?.user?.id })
        check(engine.syncNow()) { "sync failed on $device: ${engine.status.value}" }
        return db
    }

    fun counts(db: DesktopDb): Map<String, Int> =
        db.shadowRows().filter { !it.deleted }.groupingBy { it.type }.eachCount().toSortedMap()

    val before = counts(DesktopDb(File(copyPath)))
    val uploaded = counts(syncOnce(copyPath, "copy"))
    val downloaded = counts(syncOnce(freshPath, "fresh"))
    println("ROUNDTRIP|before-first-sync=$before")
    println("ROUNDTRIP|copy-after-upload=$uploaded")
    println("ROUNDTRIP|fresh-after-download=$downloaded")
    println("ROUNDTRIP|${if (uploaded == downloaded && uploaded.isNotEmpty()) "SAME" else "DIFFERENT"}")
    exitProcess(if (uploaded == downloaded) 0 else 1)
}
