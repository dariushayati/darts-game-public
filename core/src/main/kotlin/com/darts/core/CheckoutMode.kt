package com.darts.core

import kotlin.random.Random

enum class CheckoutDifficulty { EASY, HARD }

/**
 * One dart in a checkout path. [ring] is exact: a single can land in either single
 * band, but doubles, trebles and the inner bull must match the named ring.
 */
data class CheckoutDart(
    val number: Int,
    val ring: Ring,
) {
    init {
        require(number in 1..20 || number == BoardGeometry.BULL) {
            "checkout number must be 1..20 or the bull, got $number"
        }
        require(number != BoardGeometry.BULL || ring == Ring.INNER_BULL) {
            "the bull checkout must use the inner bull"
        }
        require(number == BoardGeometry.BULL || ring in setOf(Ring.SINGLE, Ring.DOUBLE, Ring.TREBLE)) {
            "a checkout number must use single, double or treble"
        }
    }

    val value: Int
        get() = when (ring) {
            Ring.INNER_BULL -> 50
            Ring.OUTER_BULL -> 25
            Ring.DOUBLE -> number * 2
            Ring.TREBLE -> number * 3
            Ring.SINGLE -> number
            Ring.MISS -> 0
        }

    val label: String
        get() = when (ring) {
            Ring.DOUBLE -> "D$number"
            Ring.TREBLE -> "T$number"
            Ring.INNER_BULL -> "BULL"
            Ring.OUTER_BULL -> "25"
            Ring.SINGLE -> "$number"
            Ring.MISS -> "MISS"
        }

    val isCheckout: Boolean
        get() = ring == Ring.DOUBLE || ring == Ring.INNER_BULL

    fun matches(hit: DartHit): Boolean {
        if (hit.isMiss) return false
        if (number == BoardGeometry.BULL) {
            return hit.number == BoardGeometry.BULL && hit.ring == Ring.INNER_BULL
        }
        return hit.number == number && hit.ring == ring
    }
}

/** A named checkout score and the exact darts needed to take it out. */
data class CheckoutPath(
    val score: Int,
    val darts: List<CheckoutDart>,
) {
    init {
        require(darts.size in 2..3) { "checkout path must use 2 or 3 darts" }
        require(darts.sumOf { it.value } == score) {
            "checkout path for $score adds up to ${darts.sumOf { it.value }}"
        }
        require(darts.last().isCheckout) { "checkout path must finish on a double or the bull" }
    }

    val label: String = darts.joinToString(" · ") { it.label }
}

data class CheckoutRules(
    val difficulty: CheckoutDifficulty = CheckoutDifficulty.EASY,
    val checkoutsPerSession: Int = 10,
) {
    init {
        require(checkoutsPerSession in 1..20) {
            "checkoutsPerSession must be 1..20"
        }
    }
}

data class CheckoutState(
    val players: List<Player>,
    val rules: CheckoutRules = CheckoutRules(),
    val order: List<CheckoutPath> = emptyList(),
    val index: Int = 0,
    val step: Int = 0,
    val dartsLeft: Int = CheckoutMode.DARTS_PER_TURN,
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val dartsThrown: Int = 0,
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    val currentPlayer: Player get() = players.first()
    val currentPath: CheckoutPath? get() = order.getOrNull(index)
    val expectedDart: CheckoutDart? get() = currentPath?.darts?.getOrNull(step)
    val completed: Int get() = index
    val total: Int get() = order.size
    val score: Int? get() = currentPath?.score
    val pathLabel: String get() = currentPath?.label.orEmpty()
    val targetLabel: String get() = expectedDart?.label ?: "DONE"
}

sealed interface CheckoutEvent {
    data class DartThrown(
        val playerId: Int,
        val hit: DartHit,
        val expected: CheckoutDart?,
        val matched: Boolean,
    ) : CheckoutEvent

    data class CheckoutCompleted(
        val playerId: Int,
        val path: CheckoutPath,
        val completed: Int,
    ) : CheckoutEvent

    data class GameFinished(val winnerId: Int) : CheckoutEvent
}

data class CheckoutReduction(
    val state: CheckoutState,
    val events: List<CheckoutEvent>,
)

/**
 * Checkout practice. A session is a shuffled set of common 2-dart (easy) or 3-dart
 * (hard) finishes. The player must hit the displayed path in order. A wrong dart
 * uses a dart but leaves the current target unchanged. Darts refresh after three
 * throws; completing a path advances to the next checkout with fresh darts.
 */
object CheckoutMode {
    const val DARTS_PER_TURN = 3

    private fun s(number: Int) = CheckoutDart(number, Ring.SINGLE)
    private fun d(number: Int) = CheckoutDart(number, Ring.DOUBLE)
    private fun t(number: Int) = CheckoutDart(number, Ring.TREBLE)
    private val bull = CheckoutDart(BoardGeometry.BULL, Ring.INNER_BULL)

    /**
     * Common two-dart finishes, compiled from standard checkout charts. Low finishes
     * use a single to set up a preferred double; higher finishes use a treble.
     */
    val EASY_PATHS: List<CheckoutPath> = listOf(
        CheckoutPath(100, listOf(t(20), d(20))),
        CheckoutPath(98, listOf(t(20), d(19))),
        CheckoutPath(97, listOf(t(19), d(20))),
        CheckoutPath(96, listOf(t(20), d(18))),
        CheckoutPath(95, listOf(t(19), d(19))),
        CheckoutPath(94, listOf(t(18), d(20))),
        CheckoutPath(93, listOf(t(19), d(18))),
        CheckoutPath(92, listOf(t(20), d(16))),
        CheckoutPath(91, listOf(t(17), d(20))),
        CheckoutPath(90, listOf(t(20), d(15))),
        CheckoutPath(89, listOf(t(19), d(16))),
        CheckoutPath(88, listOf(t(20), d(14))),
        CheckoutPath(87, listOf(t(17), d(18))),
        CheckoutPath(86, listOf(t(18), d(16))),
        CheckoutPath(85, listOf(t(15), d(20))),
        CheckoutPath(84, listOf(t(20), d(12))),
        CheckoutPath(83, listOf(t(17), d(16))),
        CheckoutPath(82, listOf(bull, d(16))),
        CheckoutPath(81, listOf(t(19), d(12))),
        CheckoutPath(80, listOf(t(20), d(10))),
        CheckoutPath(76, listOf(t(20), d(8))),
        CheckoutPath(72, listOf(t(16), d(12))),
        CheckoutPath(70, listOf(t(18), d(8))),
        CheckoutPath(68, listOf(t(20), d(4))),
        CheckoutPath(64, listOf(t(16), d(8))),
        CheckoutPath(61, listOf(t(15), d(8))),
        CheckoutPath(60, listOf(s(20), d(20))),
        CheckoutPath(58, listOf(s(18), d(20))),
        CheckoutPath(56, listOf(s(16), d(20))),
        CheckoutPath(54, listOf(s(14), d(20))),
        CheckoutPath(52, listOf(s(12), d(20))),
    )

    /**
     * Common three-dart finishes. The list follows the standard checkout chart for
     * 101–170, skipping the impossible bogey numbers 159, 162, 163, 165, 166, 168, 169.
     */
    val HARD_PATHS: List<CheckoutPath> = listOf(
        CheckoutPath(101, listOf(t(20), s(1), d(20))),
        CheckoutPath(102, listOf(t(20), s(2), d(20))),
        CheckoutPath(103, listOf(t(20), s(3), d(20))),
        CheckoutPath(104, listOf(t(20), s(4), d(20))),
        CheckoutPath(105, listOf(t(20), s(5), d(20))),
        CheckoutPath(106, listOf(t(20), s(6), d(20))),
        CheckoutPath(107, listOf(t(20), s(7), d(20))),
        CheckoutPath(108, listOf(t(20), s(8), d(20))),
        CheckoutPath(109, listOf(t(20), s(9), d(20))),
        CheckoutPath(110, listOf(t(20), s(10), d(20))),
        CheckoutPath(111, listOf(t(20), s(11), d(20))),
        CheckoutPath(112, listOf(t(20), s(12), d(20))),
        CheckoutPath(113, listOf(t(20), s(13), d(20))),
        CheckoutPath(114, listOf(t(20), s(14), d(20))),
        CheckoutPath(115, listOf(t(20), s(15), d(20))),
        CheckoutPath(116, listOf(t(20), s(16), d(20))),
        CheckoutPath(117, listOf(t(20), s(17), d(20))),
        CheckoutPath(118, listOf(t(20), s(18), d(20))),
        CheckoutPath(119, listOf(t(19), t(12), d(13))),
        CheckoutPath(120, listOf(t(20), s(20), d(20))),
        CheckoutPath(121, listOf(t(20), t(11), d(14))),
        CheckoutPath(122, listOf(t(18), t(18), d(7))),
        CheckoutPath(123, listOf(t(19), t(16), d(9))),
        CheckoutPath(124, listOf(t(20), t(16), d(8))),
        CheckoutPath(125, listOf(t(20), t(15), d(10))),
        CheckoutPath(126, listOf(t(19), t(19), d(6))),
        CheckoutPath(127, listOf(t(20), t(17), d(8))),
        CheckoutPath(128, listOf(t(18), t(14), d(16))),
        CheckoutPath(129, listOf(t(19), t(16), d(12))),
        CheckoutPath(130, listOf(t(20), t(18), d(8))),
        CheckoutPath(131, listOf(t(20), t(13), d(16))),
        CheckoutPath(132, listOf(t(20), t(16), d(12))),
        CheckoutPath(133, listOf(t(20), t(19), d(8))),
        CheckoutPath(134, listOf(t(20), t(14), d(16))),
        CheckoutPath(135, listOf(t(20), t(17), d(12))),
        CheckoutPath(136, listOf(t(20), t(20), d(8))),
        CheckoutPath(137, listOf(t(20), t(15), d(16))),
        CheckoutPath(138, listOf(t(20), t(18), d(12))),
        CheckoutPath(139, listOf(t(20), t(13), d(20))),
        CheckoutPath(140, listOf(t(20), t(16), d(16))),
        CheckoutPath(141, listOf(t(20), t(19), d(12))),
        CheckoutPath(142, listOf(t(20), t(14), d(20))),
        CheckoutPath(143, listOf(t(20), t(17), d(16))),
        CheckoutPath(144, listOf(t(20), t(20), d(12))),
        CheckoutPath(145, listOf(t(20), t(15), d(20))),
        CheckoutPath(146, listOf(t(20), t(18), d(16))),
        CheckoutPath(147, listOf(t(20), t(17), d(18))),
        CheckoutPath(148, listOf(t(20), t(16), d(20))),
        CheckoutPath(149, listOf(t(20), t(19), d(16))),
        CheckoutPath(150, listOf(t(20), t(18), d(18))),
        CheckoutPath(151, listOf(t(20), t(17), d(20))),
        CheckoutPath(152, listOf(t(20), t(20), d(16))),
        CheckoutPath(153, listOf(t(20), t(19), d(18))),
        CheckoutPath(154, listOf(t(20), t(18), d(20))),
        CheckoutPath(155, listOf(t(20), t(19), d(19))),
        CheckoutPath(156, listOf(t(20), t(20), d(18))),
        CheckoutPath(157, listOf(t(20), t(19), d(20))),
        CheckoutPath(158, listOf(t(20), t(20), d(19))),
        CheckoutPath(160, listOf(t(20), t(20), d(20))),
        CheckoutPath(161, listOf(t(20), t(17), bull)),
        CheckoutPath(164, listOf(t(20), t(18), bull)),
        CheckoutPath(167, listOf(t(20), t(19), bull)),
        CheckoutPath(170, listOf(t(20), t(20), bull)),
    )

    fun pathsFor(difficulty: CheckoutDifficulty): List<CheckoutPath> = when (difficulty) {
        CheckoutDifficulty.EASY -> EASY_PATHS
        CheckoutDifficulty.HARD -> HARD_PATHS
    }

    fun newGame(
        player: Player,
        rules: CheckoutRules = CheckoutRules(),
        random: Random = Random.Default,
    ): CheckoutState {
        val available = pathsFor(rules.difficulty)
        val count = rules.checkoutsPerSession.coerceAtMost(available.size)
        val order = available.shuffled(random).take(count)
        return CheckoutState(
            players = listOf(player),
            rules = rules,
            order = order,
            dartsByPlayer = mapOf(player.id to 0),
        )
    }

    fun apply(state: CheckoutState, hit: DartHit): CheckoutReduction {
        if (state.finished) return CheckoutReduction(state, emptyList())
        val path = state.currentPath ?: return CheckoutReduction(state, emptyList())
        val expected = state.expectedDart
        val matched = expected?.matches(hit) == true
        val player = state.currentPlayer
        val nextStep = if (matched) state.step + 1 else state.step
        val dartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + 1))
        val events = mutableListOf<CheckoutEvent>(
            CheckoutEvent.DartThrown(player.id, hit, expected, matched),
        )
        val thrown = state.copy(
            dartsByPlayer = dartsByPlayer,
            dartsThrown = state.dartsThrown + 1,
        )

        if (nextStep >= path.darts.size) {
            val nextIndex = state.index + 1
            events += CheckoutEvent.CheckoutCompleted(player.id, path, nextIndex)
            if (nextIndex >= state.order.size) {
                events += CheckoutEvent.GameFinished(player.id)
                return CheckoutReduction(
                    thrown.copy(
                        index = nextIndex,
                        step = 0,
                        dartsLeft = 0,
                        finished = true,
                        winnerId = player.id,
                    ),
                    events,
                )
            }
            return CheckoutReduction(
                thrown.copy(
                    index = nextIndex,
                    step = 0,
                    dartsLeft = DARTS_PER_TURN,
                ),
                events,
            )
        }

        val dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0)
        return CheckoutReduction(
            thrown.copy(
                step = nextStep,
                dartsLeft = if (dartsLeft == 0) DARTS_PER_TURN else dartsLeft,
            ),
            events,
        )
    }

    /** Ends an Autodarts visit early; progress is kept and the next set is ready. */
    fun advanceTurn(state: CheckoutState): CheckoutReduction {
        if (state.finished) return CheckoutReduction(state, emptyList())
        return CheckoutReduction(
            state.copy(dartsLeft = DARTS_PER_TURN),
            emptyList(),
        )
    }
}
