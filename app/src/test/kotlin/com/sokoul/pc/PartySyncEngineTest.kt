package com.sokoul.pc

import com.sokoul.pc.party.PartyPlayback
import com.sokoul.pc.party.PartySyncEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartySyncEngineTest {
    /** A round trip of [tripMs] on the network while the server's clock is [offset] ms ahead. */
    private fun PartySyncEngine.roundTrip(sentAt: Long, offset: Long, tripMs: Long = 40, heldMs: Long = 0): Boolean {
        val serverReceived = sentAt + tripMs / 2 + offset
        return probe(sentAt, serverReceived, serverReceived + heldMs, sentAt + tripMs + heldMs)
    }

    private fun calibrated(offset: Long = 5_000) = PartySyncEngine().apply {
        setMode("pro")
        repeat(3) { assertTrue(roundTrip(10_000L + it * 100, offset)) }
    }

    @Test fun clock_estimate_ignores_a_slow_round_trip() {
        val sync = calibrated()
        assertTrue(sync.calibrated)
        assertEquals(5_000.0, sync.offsetMs, 0.001)
        // A 3-second trip whose two halves were uneven gives a wrong offset; the short trips keep the estimate.
        assertTrue(sync.probe(20_000, 20_000 + 2_900 + 5_000, 20_000 + 2_900 + 5_000, 23_000))
        assertEquals(5_000.0, sync.offsetMs, 0.001)
    }

    @Test fun server_processing_time_is_not_network_time() {
        val sync = PartySyncEngine()
        repeat(3) { assertTrue(sync.roundTrip(1_000L * it, offset = 200, tripMs = 40, heldMs = 900)) }
        assertEquals(200.0, sync.offsetMs, 0.001)
    }

    @Test fun invalid_round_trips_are_refused() {
        val sync = PartySyncEngine()
        assertFalse(sync.probe(100, 105, 106, 99))
        assertFalse(sync.probe(100, 105, 104, 110))
        assertFalse(sync.probe(100, 105, 106, 10_000))
        assertFalse(sync.calibrated)
    }

    @Test fun a_scheduled_command_waits_for_its_time_and_an_older_state_cannot_undo_it() {
        val sync = calibrated()
        sync.ingest(PartyPlayback(false, 5.0, 10_000, serverAtMs = 15_000))
        assertTrue(sync.schedule("play", 20.0, scheduledAt = 15_250, serverNow = 15_000, heardAt = 10_000))
        assertTrue(sync.tick(false, 5.0, 10_249).none)
        assertFalse(sync.ingest(PartyPlayback(false, 5.0, 10_100, serverAtMs = 15_100)))
        val due = sync.tick(false, 5.0, 10_250)
        assertEquals(true, due.setPlaying)
        assertEquals(20.0, due.seekToSec!!, 0.001)
    }

    @Test fun commands_outside_pro_mode_or_too_far_ahead_are_refused() {
        val classic = PartySyncEngine()
        assertFalse(classic.schedule("play", 0.0, 1_000, 1_000, 1_000))
        val sync = calibrated()
        assertFalse(sync.schedule("play", 0.0, scheduledAt = 7_000, serverNow = 1_000, heardAt = 1_000))
        assertFalse(sync.schedule("rewind", 0.0, 1_000, 1_000, 1_000))
    }

    @Test fun small_gaps_change_speed_large_gaps_jump_with_a_cooldown() {
        val sync = calibrated()
        sync.ingest(PartyPlayback(true, 10.0, 10_000, serverAtMs = 15_000))
        assertTrue(sync.tick(true, 9.96, 10_000).none)
        assertEquals(1.03, sync.tick(true, 9.7, 10_000).playbackRate!!, 0.001)
        assertEquals(1.05, sync.tick(true, 9.4, 10_000).playbackRate!!, 0.001)
        assertEquals(1.0, sync.tick(true, 10.25, 10_250).playbackRate!!, 0.001)
        assertNotNull(sync.tick(true, 0.0, 10_500).seekToSec)
        assertNull(sync.tick(true, 0.0, 11_000).seekToSec)
        assertNotNull(sync.tick(true, 0.0, 12_000).seekToSec)
    }

    @Test fun speed_is_not_steered_while_paused() {
        val sync = calibrated()
        sync.ingest(PartyPlayback(false, 10.0, 10_000, serverAtMs = 15_000))
        val action = sync.tick(false, 9.6, 10_000)
        assertNull(action.playbackRate)
        assertNull(action.seekToSec)
    }

    @Test fun without_a_clock_the_classic_rules_apply() {
        val sync = PartySyncEngine().apply { setMode("pro") }
        assertFalse(sync.ingest(PartyPlayback(true, Double.NaN, 0)))
        sync.ingest(PartyPlayback(true, 100.0, 0))
        assertTrue(sync.tick(true, 101.5, 0).none)
        assertNotNull(sync.tick(true, 50.0, 0).seekToSec)
        assertTrue(sync.tick(true, Double.POSITIVE_INFINITY, 1_000).none)
    }

    @Test fun reset_forgets_the_room_the_clock_and_the_mode() {
        val sync = calibrated(0)
        sync.ingest(PartyPlayback(true, 10.0, 10_500, serverAtMs = 10_000))
        sync.setMode("unknown")
        assertTrue(sync.pro)
        sync.reset()
        assertFalse(sync.pro)
        assertFalse(sync.calibrated)
        assertTrue(sync.tick(true, 0.0, 0).none)
    }
}
