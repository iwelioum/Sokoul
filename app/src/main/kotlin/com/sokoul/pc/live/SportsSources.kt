package com.sokoul.pc.live

import com.sokoul.desktop.player.StreamRequest
import com.sokoul.iptv.data.net.SportsClient
import okhttp3.OkHttpClient

typealias SportsEvent = com.sokoul.iptv.data.net.SportsEvent
typealias SportsServer = com.sokoul.iptv.data.net.SportsServer
typealias SportsFailure = com.sokoul.iptv.data.net.SportsFailure
typealias SportsResult<T> = com.sokoul.iptv.data.net.SportsResult<T>
internal typealias SportsParse = com.sokoul.iptv.data.net.SportsParse

class SportsSources(http: OkHttpClient, baseUrl: () -> String?, accessKey: () -> String?) {
    private val client = SportsClient(http, baseUrl, accessKey)

    suspend fun events() = client.events()

    suspend fun servers(event: SportsEvent) = client.servers(event.id)

    suspend fun resolve(event: SportsEvent, server: SportsServer): SportsResult<StreamRequest> {
        val result = client.resolve(event.id, server.key)
        return SportsResult(result.value?.let { StreamRequest(it, null, null, event.name, live = true, mediaType = "hls") }, result.failure)
    }
}
