package com.darts.core

import com.google.gson.Gson
import kotlin.math.sqrt

/**
 * One dart as reported by the local Autodarts Board Manager (`GET /api/state`).
 * `multiplier` is the bed multiplier (1..3); a missing field defaults to 1.
 * `segmentNumber` (1..20, or 25 for bull) is only authoritative for darts that the
 * board places **on** the scoring surface: darts landing outside the double ring are
 * reported against the *nearest* number (miss beds `M1`..`M20`, `MISS`/`OUT`/`OUTSIDE`,
 * or `multiplier = 0` depending on the board version), so those markers and the
 * normalised landing radius are checked before the dart is scored.
 * `segmentName` is a fallback for older payloads.
 */
data class AutodartsThrow(
    val bed: String? = null,
    val bouncer: Boolean = false,
    val coordsX: Float = 0f,
    val coordsY: Float = 0f,
    val failed: Boolean = false,
    val method: String? = null,
    val multiplier: Int = 1,
    val segmentName: String? = null,
    val segmentNumber: Int = 0,
) {
    fun toDartHit(): DartHit {
        if (bouncer || failed || multiplier <= 0) return DartHit(null, 0, Ring.MISS)
        val landing = landingPoint()
        if (isMissBed(segmentName) || isMissBed(bed)) {
            return DartHit(null, 0, Ring.MISS, x = landing?.x, y = landing?.y)
        }
        if (isOffBoard()) return DartHit(null, 0, Ring.MISS, x = landing?.x, y = landing?.y)
        val number = when {
            segmentNumber in 1..20 -> segmentNumber
            segmentNumber == BoardGeometry.BULL -> BoardGeometry.BULL
            else -> parseNumber(segmentName)
        } ?: return DartHit(null, 0, Ring.MISS)
        val mult = multiplier.coerceIn(1, 3)
        if (number == BoardGeometry.BULL) {
            val bedName = bed?.uppercase()
            val inner = when {
                bedName?.contains("INNER") == true -> true
                bedName?.contains("BULLSEYE") == true -> true
                bedName?.contains("OUTER") == true -> false
                mult >= 2 -> true
                coordsX != 0f || coordsY != 0f ->
                    sqrt(coordsX * coordsX + coordsY * coordsY) <= BoardGeometry.INNER_BULL
                else -> false
            }
            return DartHit(
                number = number,
                multiplier = 1,
                ring = if (inner) Ring.INNER_BULL else Ring.OUTER_BULL,
                x = landing?.x,
                y = landing?.y,
            )
        }
        val ring = when {
            mult == 3 -> Ring.TREBLE
            mult == 2 -> Ring.DOUBLE
            else -> Ring.SINGLE
        }
        val band = if (ring == Ring.SINGLE) singleBand() else null
        return DartHit(number, mult, ring, band, x = landing?.x, y = landing?.y)
    }

    /**
     * Landing point in the renderer's coordinate system: Autodarts normalises to the
     * outer double wire (radius 1) with x right and **y up**, while [BoardGeometry]
     * uses x right and y down, so y is negated. `(0, 0)` means the payload carried no
     * position and yields null.
     */
    private fun landingPoint(): LandingPoint? =
        if (coordsX == 0f && coordsY == 0f) null else LandingPoint(coordsX, -coordsY)

    private data class LandingPoint(val x: Float, val y: Float)

    private fun singleBand(): SingleBand? {
        val bedName = bed?.uppercase()
        if (bedName != null) {
            when {
                "OUTER" in bedName -> return SingleBand.LARGE
                "INNER" in bedName -> return SingleBand.SMALL
            }
        }

        // Older payloads may omit `bed` but still provide normalised board coordinates.
        if (coordsX == 0f && coordsY == 0f) return null
        val radius = sqrt(coordsX * coordsX + coordsY * coordsY)
        return when {
            radius > BoardGeometry.TREBLE_OUTER -> SingleBand.LARGE
            radius < BoardGeometry.TREBLE_INNER -> SingleBand.SMALL
            else -> null
        }
    }

    /**
     * Miss beds the board reports for darts outside the double ring: `M`, `MISS`,
     * `MISSED` or the numbered `M1`..`M20` variants, plus `OUT`/`OUTSIDE`/`SURROUND`.
     * No scoring bed in any known payload starts with `M` (`SingleOuter`, `Double`,
     * `Triple`, `BULL`, `S20`, ...), so the prefix is safe.
     */
    private fun isMissBed(name: String?): Boolean {
        val text = name?.trim()?.uppercase() ?: return false
        if (text.isEmpty()) return false
        if (text.startsWith("M")) return true
        return text == "OUT" || text == "OUTSIDE" || text == "SURROUND"
    }

    /**
     * Landing radius is normalised to the outer double ring (1.0), so a dart beyond it
     * landed in the surround even when `segmentNumber` names the nearest wedge.
     * The small tolerance covers calibration jitter at the wire. Coordinates that are
     * absent (0,0) carry no evidence, and only the radius is trusted: older flat
     * payloads use different angle conventions than the board geometry.
     */
    private fun isOffBoard(): Boolean {
        if (coordsX == 0f && coordsY == 0f) return false
        val radius = sqrt(coordsX * coordsX + coordsY * coordsY)
        return radius > BoardGeometry.DOUBLE_OUTER + OFF_BOARD_TOLERANCE
    }

    private fun parseNumber(name: String?): Int? {
        val text = name?.trim()?.uppercase() ?: return null
        if (text.contains("BULL")) return BoardGeometry.BULL
        val digits = text.filter { it.isDigit() }.toIntOrNull() ?: return null
        return when (digits) {
            in 1..20 -> digits
            25 -> BoardGeometry.BULL
            else -> null
        }
    }

    private companion object {
        /** Normalised radius head-room above the double ring before a dart counts as outside. */
        const val OFF_BOARD_TOLERANCE = 0.05f
    }
}

data class AutodartsState(
    val connected: Boolean = false,
    val event: String? = null,
    val running: Boolean = false,
    val status: String? = null,
    val throws: List<AutodartsThrow> = emptyList(),
)

/** Coarse board readiness, derived from the raw Board Manager state. */
enum class AutodartsBoardStatus { OFFLINE, READY, TAKEOUT, UNKNOWN }

/**
 * Maps a `/api/state` snapshot to a traffic-light status:
 * green [READY], yellow [TAKEOUT], red [OFFLINE], amber [UNKNOWN].
 *
 * A null snapshot means the request failed, so the board is unreachable.
 */
fun deriveBoardStatus(state: AutodartsState?): AutodartsBoardStatus {
    if (state == null || !state.connected) return AutodartsBoardStatus.OFFLINE
    val text = buildString {
        state.event?.let { append(it).append(' ') }
        state.status?.let { append(it) }
    }.lowercase()
    return when {
        "takeout" in text && "finished" !in text -> AutodartsBoardStatus.TAKEOUT
        state.running -> AutodartsBoardStatus.READY
        else -> AutodartsBoardStatus.UNKNOWN
    }
}

object Autodarts {
    private val gson = Gson()

    fun parseState(json: String): AutodartsState? = try {
        gson.fromJson(json, AutodartsState::class.java)
    } catch (_: Throwable) {
        null
    }
}

/** Emits only darts not seen before, and flags when a visit (turn) has ended. */
class AutodartsThrowStream {
    data class Update(val darts: List<DartHit>, val visitEnded: Boolean)

    private var seen = 0

    fun update(state: AutodartsState): Update {
        val throws = state.throws
        if (throws.isEmpty()) {
            val ended = seen > 0
            seen = 0
            return Update(emptyList(), ended)
        }
        if (throws.size < seen) {
            // Darts are being taken out one at a time. Never re-emit the darts that
            // remain on the board; the empty-list branch above reports the visit end
            // once the last one is removed.
            seen = throws.size
            return Update(emptyList(), false)
        }
        val fresh = if (throws.size > seen) throws.drop(seen).map { it.toDartHit() } else emptyList()
        seen = throws.size
        return Update(fresh, false)
    }

    /** Marks whatever the board currently holds as already seen (used when the app attaches). */
    fun prime(state: AutodartsState) {
        seen = state.throws.size
    }

    fun reset() {
        seen = 0
    }
}
