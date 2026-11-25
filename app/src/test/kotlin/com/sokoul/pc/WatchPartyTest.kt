package com.sokoul.pc

import com.sokoul.pc.party.PartyMedia
import com.sokoul.pc.party.PartyPlayback
import com.sokoul.pc.party.PartySync
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Watch party: when the local player follows the room, when it tells the room, and the title carried through the server. */
class WatchPartyTest {
    @Test
    fun aPlayingRoomHasMovedOnSinceItWasHeard() {
        val room = PartyPlayback(playing = true, positionSec = 100.0, heardAtMs = 0)
        assertEquals(103.0, room.positionAt(3_000), 0.001)
        assertEquals(100.0, room.copy(playing = false).positionAt(3_000), 0.001)
    }

    @Test
    fun followsPlayStateAndLargeGapsOnly() {
        val room = PartyPlayback(true, 100.0, 0)
        // Small network delays are left alone.
        assertTrue(PartySync.follow(room, localPlaying = true, localSec = 101.5, nowMs = 0).none)
        val far = PartySync.follow(room, localPlaying = true, localSec = 50.0, nowMs = 0)
        assertEquals(100.0, far.seekToSec!!, 0.001)
        assertNull(far.setPlaying)
        val paused = PartySync.follow(room.copy(playing = false), localPlaying = true, localSec = 100.0, nowMs = 0)
        assertEquals(false, paused.setPlaying)
        assertNull(paused.seekToSec)
    }

    @Test
    fun tellsPlayPauseAndJumpsButNotTheFlowOfTime() {
        assertEquals("pause", PartySync.localChange(true, 10.0, 0, false, 10.5, 500))
        assertEquals("play", PartySync.localChange(false, 10.0, 0, true, 10.0, 500))
        // Ten seconds of playing over ten seconds: nothing to say.
        assertNull(PartySync.localChange(true, 10.0, 0, true, 20.0, 10_000))
        assertEquals("seek", PartySync.localChange(true, 10.0, 0, true, 300.0, 1_000))
        assertEquals("seek", PartySync.localChange(false, 10.0, 0, false, 60.0, 1_000))
    }

    @Test
    fun theTitleSurvivesTheServer() {
        val episode = PartyMedia("tv", 1399, 2, 5, "Game of Thrones", "/img.jpg")
        val back = PartyMedia.fromServer(buildJsonObject {
            put("src", episode.src)
            put("title", episode.title)
            put("poster", episode.image)
        })
        assertEquals(episode, back)
        val film = PartyMedia("movie", 27205, null, null, "Inception", null)
        assertEquals(film, PartyMedia.fromServer(buildJsonObject { put("src", film.src); put("title", "Inception") }))
        // A room created by a web client carries a stream address, not a Sokoul title.
        assertNull(PartyMedia.fromServer(buildJsonObject { put("src", JsonPrimitive("https://example.com/v.m3u8")) }))
    }
}
