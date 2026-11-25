package com.sokoul.desktop.data

import com.sokoul.credentials.ServiceCheck
import com.sokoul.credentials.Verdict
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class ProbeResult(val ok: Boolean, val message: String)

/**
 * « Tester » de Réglages › Clés API : un vrai appel par service, celui de [ServiceCheck] (le même que le premier
 * lancement et le coffre du serveur Sokoul). Le message ne contient jamais l'adresse (certaines portent la clé),
 * seulement le code HTTP ou la nature de l'échec.
 */
object ProviderProbe {
    private val http = OkHttpClient.Builder().dns(com.sokoul.iptv.data.net.Ipv4FirstDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val services = ServiceCheck(http)

    suspend fun check(db: DesktopDb, id: String): ProbeResult {
        val key = ProviderConfig.key(db, id) ?: return ProbeResult(false, "Clé absente")
        return checkKey(id, key)
    }

    suspend fun checkKey(id: String, key: String): ProbeResult {
        if (id !in ServiceCheck.SERVICES) return ProbeResult(true, "Clé détectée")
        val r = services.key(id, key)
        return when {
            r.verdict == Verdict.Accepted -> ProbeResult(true, "Connexion réussie")
            r.quota -> ProbeResult(false, "Quota atteint, réessayez plus tard")
            r.httpCode != null && r.verdict == Verdict.Refused -> ProbeResult(false, "Clé refusée (HTTP ${r.httpCode})")
            r.httpCode != null -> ProbeResult(false, "Service indisponible (HTTP ${r.httpCode})")
            r.verdict == Verdict.Refused -> ProbeResult(false, "Clé refusée")
            r.noNetwork -> ProbeResult(false, "Réseau indisponible")
            else -> ProbeResult(false, "Pas de réponse (${r.failure})")
        }
    }
}
