package com.sokoul.pc.party

import kotlin.math.abs

/**
 * Keeps this device's player on the room's moment. Three parts, each small enough to reason about:
 * - [Clock] estimates the gap between this device's clock and the server's from timed round trips;
 * - the room's timeline holds the latest playback the server told, never letting an older message undo a newer one, and
 *   commands the server scheduled for a precise server time;
 * - [Steering] turns the gap between the room and the local player into an action: nothing, a slight speed change, or
 *   a jump, each with a cooldown so the player never flaps.
 *
 * "pro" mode (server timestamps, clock estimate, speed steering) is used once the server asked for it and the clock is
 * known; otherwise the room is followed the classic way ([PartySync.follow]): play/pause and jumps beyond two seconds.
 */
class PartySyncEngine {
    private val clock = Clock()
    private val steering = Steering()
    private var room: PartyPlayback? = null

    @Volatile var pro: Boolean = false
        private set

    val calibrated: Boolean get() = synchronized(this) { clock.ready }
    val offsetMs: Double get() = synchronized(this) { clock.offsetMs }

    @Synchronized fun setMode(mode: String) {
        val wantsPro = when (mode) { "pro" -> true; "classic" -> false; else -> return }
        if (wantsPro == pro) return
        pro = wantsPro
        steering.forgetSpeed()
    }

    /** One clock round trip: sent and received on this device, received and answered on the server. */
    @Synchronized fun probe(clientSent: Long, serverReceived: Long, serverSent: Long, clientReceived: Long): Boolean =
        clock.add(clientSent, serverReceived, serverSent, clientReceived)

    /** A playback state told by the server. False when it is invalid or older than the one already held. */
    @Synchronized fun ingest(next: PartyPlayback): Boolean {
        if (!next.positionSec.isFinite() || next.positionSec < 0) return false
        val held = room
        if (next.serverAtMs != null && held?.serverAtMs != null && next.serverAtMs < held.serverAtMs) return false
        room = next
        return true
    }

    /**
     * A command the server wants applied at server time [scheduledAt] (pro mode only, at most 5 s ahead). It becomes the
     * room's state, due on this device when the server's clock reaches that time.
     */
    @Synchronized fun schedule(action: String, position: Double, scheduledAt: Long, serverNow: Long, heardAt: Long): Boolean {
        if (!pro) return false
        val lead = scheduledAt - serverNow
        if (lead !in 0..MAX_SCHEDULE_LEAD_MS) return false
        val playing = when (action) {
            "play" -> true
            "pause" -> false
            "seek" -> room?.playing ?: false
            else -> return false
        }
        return ingest(PartyPlayback(playing, position, heardAt, serverAtMs = scheduledAt, applyAtMs = heardAt + lead))
    }

    /** What the local player should do now, given what it is doing. */
    @Synchronized fun tick(localPlaying: Boolean, localSec: Double, nowMs: Long): SyncAction {
        val state = room ?: return NOTHING
        if (!localSec.isFinite() || localSec < 0) return NOTHING
        if (nowMs < dueAt(state)) return NOTHING
        val timed = state.copy(clockOffsetMs = clock.offsetMs.takeIf { clock.ready })
        return if (pro && clock.ready) steering.steer(timed, localPlaying, localSec, nowMs)
        else steering.classic(timed, localPlaying, localSec, nowMs)
    }

    /** The title changed: the room's playback no longer applies; the clock estimate does. */
    @Synchronized fun resetMedia() {
        room = null
        steering.reset()
    }

    /** Left the room: everything goes. */
    @Synchronized fun reset() {
        resetMedia()
        clock.clear()
        pro = false
    }

    /** When a state applies on this device: its server time converted with the clock estimate, else when it was heard. */
    private fun dueAt(state: PartyPlayback): Long =
        if (clock.ready && state.serverAtMs != null) (state.serverAtMs - clock.offsetMs).toLong()
        else state.applyAtMs ?: Long.MIN_VALUE

    /**
     * Server clock minus this device's clock. Each round trip gives an estimate whose error is at most half its trip
     * time, so the estimate kept is the median of the samples with the shortest trips: one slow trip cannot move it.
     */
    private class Clock {
        private class Sample(val offsetMs: Double, val tripMs: Long)
        private val samples = ArrayDeque<Sample>()

        val ready: Boolean get() = samples.size >= READY_SAMPLES
        var offsetMs: Double = 0.0
            private set

        fun add(clientSent: Long, serverReceived: Long, serverSent: Long, clientReceived: Long): Boolean {
            val serverHeld = serverSent - serverReceived
            val trip = clientReceived - clientSent - serverHeld
            if (serverHeld < 0 || trip < 0 || trip > MAX_TRIP_MS) return false
            val offset = ((serverReceived - clientSent) + (serverSent - clientReceived)) / 2.0
            samples.addLast(Sample(offset, trip))
            while (samples.size > KEPT_SAMPLES) samples.removeFirst()
            val best = samples.sortedBy { it.tripMs }.take(READY_SAMPLES).map { it.offsetMs }.sorted()
            offsetMs = best[best.size / 2]
            return true
        }

        fun clear() {
            samples.clear()
            offsetMs = 0.0
        }
    }

    /** Turns "where the room is" against "where the player is" into actions, with cooldowns. */
    private class Steering {
        private var lastJumpAt: Long? = null
        private var lastToggleAt: Long? = null
        private var speed = 1.0

        fun classic(room: PartyPlayback, localPlaying: Boolean, localSec: Double, nowMs: Long): SyncAction {
            val wanted = PartySync.follow(room, localPlaying, localSec, nowMs)
            return SyncAction(
                setPlaying = wanted.setPlaying?.takeIf { cooled(lastToggleAt, nowMs, TOGGLE_COOLDOWN_MS) }?.also { lastToggleAt = nowMs },
                seekToSec = wanted.seekToSec?.takeIf { cooled(lastJumpAt, nowMs, JUMP_COOLDOWN_MS) }?.also { lastJumpAt = nowMs },
            )
        }

        fun steer(room: PartyPlayback, localPlaying: Boolean, localSec: Double, nowMs: Long): SyncAction {
            val target = room.positionAt(nowMs)
            val behind = target - localSec
            val toggle = room.playing.takeIf { it != localPlaying && cooled(lastToggleAt, nowMs, TOGGLE_COOLDOWN_MS) }
                ?.also { lastToggleAt = nowMs }
            if (abs(behind) >= JUMP_SEC && cooled(lastJumpAt, nowMs, JUMP_COOLDOWN_MS)) {
                lastJumpAt = nowMs
                speed = 1.0
                return SyncAction(toggle, target, 1.0)
            }
            val bothPlaying = room.playing && localPlaying
            val wanted = if (!bothPlaying || abs(behind) < DEAD_BAND_SEC) 1.0
                else (1.0 + behind * SPEED_PER_SEC).coerceIn(1.0 - MAX_SPEED_CHANGE, 1.0 + MAX_SPEED_CHANGE)
            val newSpeed = wanted.takeIf { abs(it - speed) >= SPEED_STEP }?.also { speed = it }
            return SyncAction(toggle, null, newSpeed)
        }

        fun forgetSpeed() { speed = 1.0 }

        fun reset() {
            lastJumpAt = null
            lastToggleAt = null
            speed = 1.0
        }

        private fun cooled(last: Long?, nowMs: Long, cooldownMs: Long) = last == null || nowMs - last >= cooldownMs
    }

    private companion object {
        val NOTHING = SyncAction(null, null)
        const val MAX_SCHEDULE_LEAD_MS = 5_000L
        const val MAX_TRIP_MS = 5_000L
        const val READY_SAMPLES = 3
        const val KEPT_SAMPLES = 8
        const val TOGGLE_COOLDOWN_MS = 600L
        const val JUMP_COOLDOWN_MS = 1_500L
        /** From this gap the player jumps instead of catching up. */
        const val JUMP_SEC = 1.0
        /** Below this gap nothing is corrected: no viewer notices it. */
        const val DEAD_BAND_SEC = 0.1
        /** Speed change per second of gap, and its bound (±5 % stays inaudible for speech). */
        const val SPEED_PER_SEC = 0.1
        const val MAX_SPEED_CHANGE = 0.05
        const val SPEED_STEP = 0.005
    }
}
