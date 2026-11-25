package com.sokoul.pc.account

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One key of the account's vault as the server shows it: its mask and last check, never the key. */
@Serializable
data class VaultEntry(val service: String, val hint: String, val verdict: String, val checkedAt: Long)

/**
 * The account's credentials vault on the Sokoul server (docs/server.md, Credentials vault). A key accepted on this
 * device is also saved there, so the account remembers which services are set up; the server checks it again and
 * keeps it encrypted. Keys never come back: a new device only learns *which* keys the account has (their masks), and
 * the person enters them again. Signed out, every call is a quiet failure.
 */
class AccountVault(private val account: SokoulAccount) {
    val signedIn: Boolean get() = account.session() != null

    /** The account's stored keys, by service. */
    suspend fun list(): AccountResult<Map<String, VaultEntry>> {
        if (!signedIn) return AccountResult.Failed(Problems.forCode("unauthorized"))
        return when (val r = account.getWithSession(PATH)) {
            is AccountResult.Failed -> r
            is AccountResult.Ok -> runCatching { AccountResult.Ok(json.decodeFromString(ListSerializer(VaultEntry.serializer()), r.value).associateBy { it.service }) }
                .getOrElse { AccountResult.Failed(Problems.notSokoul) }
        }
    }

    /** Saves [value] for [service] once this device's own check accepted it. */
    suspend fun save(service: String, value: String): AccountResult<VaultEntry> {
        if (!signedIn) return AccountResult.Failed(Problems.forCode("unauthorized"))
        val body = json.encodeToString(PutBody.serializer(), PutBody(value))
        return when (val r = account.putWithSession("$PATH/$service", body)) {
            is AccountResult.Failed -> r
            is AccountResult.Ok -> runCatching { AccountResult.Ok(json.decodeFromString(VaultEntry.serializer(), r.value)) }
                .getOrElse { AccountResult.Failed(Problems.notSokoul) }
        }
    }

    @Serializable private data class PutBody(val value: String) {
        override fun toString(): String = "PutBody(value=***)"
    }

    companion object {
        const val PATH = "/api/credentials"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
