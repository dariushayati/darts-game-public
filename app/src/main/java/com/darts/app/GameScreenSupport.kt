package com.darts.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.darts.core.AutodartsBoardStatus
import com.darts.core.AutodartsThrowStream
import com.darts.core.DartHit
import com.darts.core.GameRules
import com.darts.core.GameState
import com.darts.core.MatchRecord
import com.darts.core.Player
import com.darts.core.ShieldsMode
import com.darts.core.deriveBoardStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberSoundEffects(): SoundEffects {
    val context = LocalContext.current
    val sounds = remember(context) { SoundEffects(context) }
    DisposableEffect(sounds) {
        onDispose { sounds.release() }
    }
    return sounds
}

@Composable
fun rememberVoiceCallouts(): VoiceCallouts {
    val context = LocalContext.current
    val voice = remember(context) { VoiceCallouts(context) }
    DisposableEffect(voice) {
        onDispose { voice.release() }
    }
    return voice
}

@Composable
fun rememberNeutralBoardState(players: List<Player>): GameState = remember(players) {
    ShieldsMode.newGame(
        players.ifEmpty { listOf(Player(1, "", 0)) },
        GameRules(dartsPerPlayer = 300, endOnBoardFull = false),
    )
}

@Composable
fun RecordMatchWhenFinished(
    finished: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    record: () -> MatchRecord,
) {
    val currentRecord by rememberUpdatedState(record)
    val currentOnMatchFinished by rememberUpdatedState(onMatchFinished)
    LaunchedEffect(finished) {
        if (finished) currentOnMatchFinished(currentRecord())
    }
}

@Composable
fun LaunchedAutodartsEffect(
    config: AutodartsConfig?,
    onBoardStatus: (AutodartsBoardStatus) -> Unit,
    onDart: (DartHit) -> Unit,
    onVisitEnded: () -> Unit,
    turnKey: Any? = null,
) {
    val currentOnBoardStatus by rememberUpdatedState(onBoardStatus)
    val currentOnDart by rememberUpdatedState(onDart)
    val currentOnVisitEnded by rememberUpdatedState(onVisitEnded)

    if (config?.mode == ScoringMode.CAMERA) {
        val controller = CameraScoringHost.controller ?: return
        LaunchedEffect(config) { controller.start() }
        LaunchedEffect(turnKey) { controller.beginTurn(turnKey) }
        LaunchedEffect(controller) {
            launch {
                controller.detected.collect { dart -> currentOnDart(dart.hit) }
            }
            launch {
                controller.turnComplete.collect { currentOnVisitEnded() }
            }
        }
        DisposableEffect(config) {
            onDispose { controller.stop() }
        }
        return
    }

    LaunchedEffect(config) {
        if (config == null) return@LaunchedEffect
        val stream = AutodartsThrowStream()
        var primed = false
        while (isActive) {
            val snapshot = withContext(Dispatchers.IO) { fetchAutodarts(config) }
            currentOnBoardStatus(deriveBoardStatus(snapshot))
            if (snapshot != null) {
                if (!primed) {
                    stream.prime(snapshot)
                    primed = true
                } else {
                    val update = stream.update(snapshot)
                    update.darts.forEach(currentOnDart)
                    if (update.visitEnded) currentOnVisitEnded()
                }
            }
            delay(250)
        }
    }
}
