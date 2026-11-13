package com.darts.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.darts.core.CheckoutRules
import com.darts.core.GameRules
import com.darts.core.GunslingerRules
import com.darts.core.InvadersRules
import com.darts.core.LavaRules
import com.darts.core.MatchRecord
import com.darts.core.Player
import com.darts.core.RobinHoodRules
import com.darts.core.WhackAMoleRules
import com.darts.app.autoscore.AutoScoreSettings
import com.darts.core.X01Rules
import kotlinx.coroutines.delay

@Composable
fun DartsApp() {
    val context = LocalContext.current
    val rulesStore = remember { RulesStore(context) }
    val appSettings = remember { AppSettingsStore(context) }
    val invadersStore = remember { InvadersStore(context) }
    val lavaStore = remember { LavaStore(context) }
    val robinHoodStore = remember { RobinHoodStore(context) }
    val whackAMoleStore = remember { WhackAMoleStore(context) }
    val x01Store = remember { X01Store(context) }
    val gunslingerStore = remember { GunslingerStore(context) }
    val playerDraft = remember { PlayerSetupDraft(PlayerNamesStore(context)) }
    val matchHistoryStore = remember { MatchHistoryStore(context) }
    var matchHistory by remember { mutableStateOf(matchHistoryStore.load()) }
    var rules by remember { mutableStateOf<GameRules>(rulesStore.load()) }
    var invadersRules by remember { mutableStateOf<InvadersRules>(invadersStore.load()) }
    var lavaRules by remember { mutableStateOf<LavaRules>(lavaStore.load()) }
    var robinHoodRules by remember { mutableStateOf<RobinHoodRules>(robinHoodStore.load()) }
    var whackAMoleRules by remember { mutableStateOf<WhackAMoleRules>(whackAMoleStore.load()) }
    var x01Rules by remember { mutableStateOf<X01Rules>(x01Store.load()) }
    var checkoutRules by remember { mutableStateOf(CheckoutRules()) }
    var gunslingerRules by remember { mutableStateOf<GunslingerRules>(gunslingerStore.load()) }
    var soundEnabled by remember { mutableStateOf(appSettings.loadSoundEnabled()) }
    var voiceCalloutsEnabled by remember { mutableStateOf(appSettings.loadVoiceCalloutsEnabled()) }
    var scoringMode by remember { mutableStateOf(appSettings.loadScoringMode()) }
    var autodartsUrl by remember { mutableStateOf(appSettings.loadAutodartsUrl()) }
    var autoScoreSoftBoard by remember { mutableStateOf(appSettings.loadAutoScoreSoftBoard()) }
    var autoScoreForceCpu by remember { mutableStateOf(appSettings.loadAutoScoreForceCpu()) }
    var autoScoreDebugImages by remember { mutableStateOf(appSettings.loadAutoScoreDebugImages()) }
    var cameraTrackingRate by remember { mutableStateOf(appSettings.loadCameraTrackingRate()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }

    fun updateAutodartsUrl(url: String) {
        appSettings.saveAutodartsUrl(url)
        autodartsUrl = url
    }

    fun updateScoringMode(mode: ScoringMode) {
        appSettings.saveScoringMode(mode)
        scoringMode = mode
    }

    val cameraScoringController = remember(context) {
        CameraScoringController(context.applicationContext).also {
            CameraScoringHost.controller = it
        }
    }
    DisposableEffect(cameraScoringController) {
        onDispose {
            cameraScoringController.release()
            if (CameraScoringHost.controller === cameraScoringController) {
                CameraScoringHost.controller = null
            }
        }
    }

    fun addMatch(record: MatchRecord) {
        if (matchHistory.any { it.id == record.id }) return
        matchHistory = (listOf(record) + matchHistory).take(MAX_MATCH_HISTORY)
        matchHistoryStore.save(matchHistory)
    }

    fun deleteMatch(id: String) {
        matchHistory = matchHistory.filterNot { it.id == id }
        matchHistoryStore.save(matchHistory)
    }

    LaunchedEffect(Unit) {
        delay(1200)
        if (screen == Screen.Splash) screen = Screen.Menu
    }

    BackHandler(
        enabled = screen != Screen.Menu &&
            screen !is Screen.Game &&
            screen !is Screen.Mirror,
    ) {
        screen = when (val current = screen) {
            Screen.Splash -> Screen.Menu
            Screen.AppSettings -> Screen.Menu
            Screen.Autodarts -> Screen.Menu
            Screen.AutoScore -> Screen.AppSettings
            Screen.Licences -> Screen.AppSettings
            Screen.Training -> Screen.Menu
            Screen.JoinGame -> Screen.Menu
            is Screen.Setup -> if (current.game.isTraining) Screen.Training else Screen.Menu
            is Screen.Settings -> Screen.Setup(current.game)
            is Screen.MatchDetail -> Screen.MatchHistory
            Screen.MatchHistory -> Screen.Menu
            else -> Screen.Menu
        }
    }
    val showBottomNav = screen == Screen.Menu ||
        screen == Screen.Training ||
        screen == Screen.MatchHistory ||
        screen == Screen.AppSettings ||
        screen == Screen.Autodarts

    val isTablet = LayoutPolicy.isTablet(LocalConfiguration.current.smallestScreenWidthDp)
    val showPhoneBottomNav = showBottomNav && !isTablet
    val rootInsets = if (showPhoneBottomNav) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    } else {
        WindowInsets.safeDrawing
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DartsPalette.BackgroundDeep)
            .windowInsetsPadding(rootInsets),
    ) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (showBottomNav && isTablet) {
                BottomNavRail(
                    selected = when (screen) {
                        Screen.AppSettings -> BottomTab.SETTINGS
                        Screen.Autodarts -> BottomTab.AUTODARTS
                        Screen.Training -> BottomTab.TRAINING
                        Screen.MatchHistory, is Screen.MatchDetail -> BottomTab.STATS
                        else -> BottomTab.GAMES
                    },
                    onSelect = { tab ->
                        screen = when (tab) {
                            BottomTab.GAMES -> Screen.Menu
                            BottomTab.TRAINING -> Screen.Training
                            BottomTab.STATS -> Screen.MatchHistory
                            BottomTab.AUTODARTS -> Screen.Autodarts
                            BottomTab.SETTINGS -> Screen.AppSettings
                        }
                    },
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(DartsPalette.Border))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (val current = screen) {
                Screen.Splash -> LaunchSplashScreen()

                Screen.Menu -> GameMenuScreen(
                    onSelect = {
                        playerDraft.reset()
                        screen = Screen.Setup(it)
                    },
                    onJoin = { screen = Screen.JoinGame },
                )

                Screen.Training -> TrainingMenuScreen(
                    onSelect = {
                        playerDraft.reset()
                        screen = Screen.Setup(it)
                    },
                )

                Screen.JoinGame -> JoinGameScreen(
                    onBack = { screen = Screen.Menu },
                    onJoined = { host, port, code ->
                        screen = Screen.Mirror(host, port, code)
                    },
                )

                is Screen.Mirror -> MirrorGameScreen(
                    host = current.host,
                    port = current.port,
                    code = current.code,
                    onExit = { screen = Screen.JoinGame },
                )

                Screen.Autodarts -> AutodartsScreen(
                    autodartsUrl = autodartsUrl,
                    onAutodartsUrlChange = { updateAutodartsUrl(it) },
                )

                Screen.MatchHistory -> MatchHistoryScreen(
                    matches = matchHistory,
                    onOpen = { screen = Screen.MatchDetail(it) },
                    onDelete = { deleteMatch(it) },
                )

                is Screen.MatchDetail -> {
                    val record = matchHistory.firstOrNull { it.id == current.id }
                    if (record != null) {
                        MatchDetailScreen(
                            record = record,
                            onBack = { screen = Screen.MatchHistory },
                        )
                    } else {
                        LaunchedEffect(current.id) { screen = Screen.MatchHistory }
                    }
                }

                Screen.AppSettings -> GeneralSettingsScreen(
                    soundEnabled = soundEnabled,
                    onSoundEnabledChange = { enabled ->
                        appSettings.saveSoundEnabled(enabled)
                        soundEnabled = enabled
                    },
                    voiceCalloutsEnabled = voiceCalloutsEnabled,
                    onVoiceCalloutsChange = { enabled ->
                        appSettings.saveVoiceCalloutsEnabled(enabled)
                        voiceCalloutsEnabled = enabled
                    },
                    autodartsUrl = autodartsUrl,
                    onAutodartsUrlChange = { updateAutodartsUrl(it) },
                    autoScoreSoftBoard = autoScoreSoftBoard,
                    onAutoScoreSoftBoardChange = { enabled ->
                        appSettings.saveAutoScoreSoftBoard(enabled)
                        autoScoreSoftBoard = enabled
                    },
                    autoScoreForceCpu = autoScoreForceCpu,
                    onAutoScoreForceCpuChange = { enabled ->
                        appSettings.saveAutoScoreForceCpu(enabled)
                        autoScoreForceCpu = enabled
                    },
                    autoScoreDebugImages = autoScoreDebugImages,
                    onAutoScoreDebugImagesChange = { enabled ->
                        appSettings.saveAutoScoreDebugImages(enabled)
                        autoScoreDebugImages = enabled
                    },
                    cameraTrackingRate = cameraTrackingRate,
                    onCameraTrackingRateChange = { rate ->
                        appSettings.saveCameraTrackingRate(rate)
                        cameraTrackingRate = rate
                    },
                    onOpenAutoScore = { screen = Screen.AutoScore },
                    onOpenLicences = { screen = Screen.Licences },
                    themeMode = ThemePreference.mode,
                    onThemeModeChange = { mode ->
                        appSettings.saveThemeMode(mode)
                        ThemePreference.mode = mode
                    },
                )

                Screen.AutoScore -> AutoScoreCameraScreen(
                    settings = AutoScoreSettings(
                        softBoard = autoScoreSoftBoard,
                        forceCpu = autoScoreForceCpu,
                        debugImages = autoScoreDebugImages,
                    ),
                    onSettingsChange = { updated ->
                        appSettings.saveAutoScoreSoftBoard(updated.softBoard)
                        appSettings.saveAutoScoreForceCpu(updated.forceCpu)
                        appSettings.saveAutoScoreDebugImages(updated.debugImages)
                        autoScoreSoftBoard = updated.softBoard
                        autoScoreForceCpu = updated.forceCpu
                        autoScoreDebugImages = updated.debugImages
                    },
                    onOpenLicences = { screen = Screen.Licences },
                    onBack = { screen = Screen.AppSettings },
                )

                Screen.Licences -> LicencesScreen(onBack = { screen = Screen.AppSettings })

                is Screen.Setup -> when (current.game) {
                    GameType.SHIELDS -> SetupScreen(
                        game = current.game,
                        rules = rules,
                        draft = playerDraft,
                        onBack = { screen = Screen.Menu },
                        onSettings = { screen = Screen.Settings(current.game) },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts ->
                            screen = Screen.Game(current.game, players, autodarts)
                        },
                    )

                    GameType.INVADERS -> InvadersSetupScreen(
                        rules = invadersRules,
                        draft = playerDraft,
                        onBack = { screen = Screen.Menu },
                        onSettings = { screen = Screen.Settings(current.game) },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts, currentRules ->
                            invadersStore.save(currentRules)
                            invadersRules = currentRules
                            screen = Screen.Game(
                                game = current.game,
                                players = players,
                                autodarts = autodarts,
                                invadersRules = currentRules,
                            )
                        },
                    )

                    GameType.LAVA -> LavaSetupScreen(
                        rules = lavaRules,
                        draft = playerDraft,
                        onBack = { screen = Screen.Menu },
                        onSettings = { screen = Screen.Settings(current.game) },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts, currentRules ->
                            lavaStore.save(currentRules)
                            lavaRules = currentRules
                            screen = Screen.Game(
                                game = current.game,
                                players = players,
                                autodarts = autodarts,
                                lavaRules = currentRules,
                            )
                        },
                    )

                    GameType.RING_RUN -> RingRunSetupScreen(
                        draft = playerDraft,
                        onBack = { screen = Screen.Menu },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts ->
                            screen = Screen.Game(
                                game = current.game,
                                players = players,
                                autodarts = autodarts,
                            )
                        },
                    )

                    GameType.ROBIN_HOOD -> RobinHoodSetupScreen(
                        rules = robinHoodRules,
                        draft = playerDraft,
                        onBack = { screen = Screen.Menu },
                        onSettings = { screen = Screen.Settings(current.game) },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts, currentRules ->
                            robinHoodStore.save(currentRules)
                            robinHoodRules = currentRules
                            screen = Screen.Game(
                                game = current.game,
                                players = players,
                                autodarts = autodarts,
                                robinHoodRules = currentRules,
                            )
                        },
                    )

                    GameType.WHACK_A_MOLE -> WhackAMoleSetupScreen(
                        rules = whackAMoleRules,
                        draft = playerDraft,
                        onBack = { screen = Screen.Menu },
                        onSettings = { screen = Screen.Settings(current.game) },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts, currentRules ->
                            whackAMoleStore.save(currentRules)
                            whackAMoleRules = currentRules
                            screen = Screen.Game(
                                game = current.game,
                                players = players,
                                autodarts = autodarts,
                                whackAMoleRules = currentRules,
                            )
                        },
                    )

                    GameType.CRICKET -> CricketSetupScreen(
                        draft = playerDraft,
                        onBack = { screen = Screen.Menu },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts ->
                            screen = Screen.Game(
                                game = current.game,
                                players = players,
                                autodarts = autodarts,
                            )
                        },
                    )

                    GameType.X01 -> X01SetupScreen(
                        rules = x01Rules,
                        draft = playerDraft,
                        onBack = { screen = Screen.Menu },
                        onSettings = { screen = Screen.Settings(current.game) },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts, currentRules ->
                            x01Store.save(currentRules)
                            x01Rules = currentRules
                            screen = Screen.Game(
                                game = current.game,
                                players = players,
                                autodarts = autodarts,
                                x01Rules = currentRules,
                            )
                        },
                    )

                    GameType.AROUND_THE_CLOCK -> AroundTheClockSetupScreen(
                        draft = playerDraft,
                        onBack = { screen = Screen.Training },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts ->
                            screen = Screen.Game(
                                game = current.game,
                                players = players,
                                autodarts = autodarts,
                            )
                        },
                    )

                    GameType.CHECKOUT -> CheckoutSetupScreen(
                        draft = playerDraft,
                        rules = checkoutRules,
                        onRulesChange = { checkoutRules = it },
                        onBack = { screen = Screen.Training },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { player, autodarts, currentRules ->
                            checkoutRules = currentRules
                            screen = Screen.Game(
                                game = current.game,
                                players = listOf(player),
                                autodarts = autodarts,
                                checkoutRules = currentRules,
                            )
                        },
                    )

                    GameType.GUNSLINGER -> GunslingerSetupScreen(
                        rules = gunslingerRules,
                        draft = playerDraft,
                        onBack = { screen = Screen.Menu },
                        onSettings = { screen = Screen.Settings(current.game) },
                        autodartsUrl = autodartsUrl,
                        scoringMode = scoringMode,
                        onScoringModeChange = { updateScoringMode(it) },
                        onStart = { players, autodarts, currentRules ->
                            gunslingerStore.save(currentRules)
                            gunslingerRules = currentRules
                            screen = Screen.Game(
                                game = current.game,
                                players = players,
                                autodarts = autodarts,
                                gunslingerRules = currentRules,
                            )
                        },
                    )
                }

                is Screen.Settings -> when (current.game) {
                    GameType.SHIELDS -> SettingsScreen(
                        rules = rules,
                        onSave = { updated ->
                            rulesStore.save(updated)
                            rules = updated
                            screen = Screen.Setup(current.game)
                        },
                        onBack = { screen = Screen.Setup(current.game) },
                    )

                    GameType.INVADERS -> InvadersSettingsScreen(
                        rules = invadersRules,
                        onSave = { updated ->
                            invadersStore.save(updated)
                            invadersRules = updated
                            screen = Screen.Setup(current.game)
                        },
                        onBack = { screen = Screen.Setup(current.game) },
                    )

                    GameType.LAVA -> LavaSettingsScreen(
                        rules = lavaRules,
                        onSave = { updated ->
                            lavaStore.save(updated)
                            lavaRules = updated
                            screen = Screen.Setup(current.game)
                        },
                        onBack = { screen = Screen.Setup(current.game) },
                    )

                    // Ring Run has fixed rules; its setup screen intentionally has no EDIT route.
                    GameType.RING_RUN -> Unit

                    GameType.ROBIN_HOOD -> RobinHoodSettingsScreen(
                        rules = robinHoodRules,
                        onSave = { updated ->
                            robinHoodStore.save(updated)
                            robinHoodRules = updated
                            screen = Screen.Setup(current.game)
                        },
                        onBack = { screen = Screen.Setup(current.game) },
                    )

                    GameType.WHACK_A_MOLE -> WhackAMoleSettingsScreen(
                        rules = whackAMoleRules,
                        onSave = { updated ->
                            whackAMoleStore.save(updated)
                            whackAMoleRules = updated
                            screen = Screen.Setup(current.game)
                        },
                        onBack = { screen = Screen.Setup(current.game) },
                    )

                    // Cricket has no editable rules.
                    GameType.CRICKET -> Unit

                    GameType.X01 -> X01SettingsScreen(
                        rules = x01Rules,
                        onSave = { updated ->
                            x01Store.save(updated)
                            x01Rules = updated
                            screen = Screen.Setup(current.game)
                        },
                        onBack = { screen = Screen.Setup(current.game) },
                    )

                    // Around the Clock and Checkout have fixed training rules.
                    GameType.AROUND_THE_CLOCK,
                    GameType.CHECKOUT -> Unit

                    GameType.GUNSLINGER -> GunslingerSettingsScreen(
                        rules = gunslingerRules,
                        onSave = { updated ->
                            gunslingerStore.save(updated)
                            gunslingerRules = updated
                            screen = Screen.Setup(current.game)
                        },
                        onBack = { screen = Screen.Setup(current.game) },
                    )
                }

                is Screen.Game -> when (current.game) {
                    GameType.SHIELDS -> GameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        rules = rules,
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.INVADERS -> InvadersGameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        rules = current.invadersRules ?: InvadersRules(),
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.LAVA -> LavaGameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        rules = current.lavaRules ?: LavaRules(),
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.RING_RUN -> RingRunGameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.ROBIN_HOOD -> RobinHoodGameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        rules = current.robinHoodRules ?: robinHoodRules,
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.WHACK_A_MOLE -> WhackAMoleGameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        rules = current.whackAMoleRules ?: whackAMoleRules,
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.CRICKET -> CricketGameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.X01 -> X01GameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        rules = current.x01Rules ?: x01Rules,
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.AROUND_THE_CLOCK -> AroundTheClockGameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.CHECKOUT -> CheckoutGameScreen(
                        player = current.players.first(),
                        autodarts = current.autodarts,
                        rules = current.checkoutRules ?: checkoutRules,
                        soundEnabled = soundEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )

                    GameType.GUNSLINGER -> GunslingerGameScreen(
                        players = current.players,
                        autodarts = current.autodarts,
                        rules = current.gunslingerRules ?: gunslingerRules,
                        soundEnabled = soundEnabled,
                        voiceCalloutsEnabled = voiceCalloutsEnabled,
                        onMatchFinished = { addMatch(it) },
                        onExit = { screen = Screen.Setup(current.game) },
                    )
                }
            }
        }
        }
        if (showPhoneBottomNav) {
            BottomNav(
                selected = when (screen) {
                    Screen.AppSettings -> BottomTab.SETTINGS
                    Screen.Autodarts -> BottomTab.AUTODARTS
                    Screen.Training -> BottomTab.TRAINING
                    Screen.MatchHistory, is Screen.MatchDetail -> BottomTab.STATS
                    else -> BottomTab.GAMES
                },
                onSelect = { tab ->
                    screen = when (tab) {
                        BottomTab.GAMES -> Screen.Menu
                        BottomTab.TRAINING -> Screen.Training
                        BottomTab.STATS -> Screen.MatchHistory
                        BottomTab.AUTODARTS -> Screen.Autodarts
                        BottomTab.SETTINGS -> Screen.AppSettings
                    }
                },
            )
        }
    }
}

const val DEFAULT_AUTODARTS_URL = "http://autodarts.local:3180"

/** Upper bound on locally kept matches so the preferences blob stays small. */
private const val MAX_MATCH_HISTORY = 200

/** Accepts a bare host, host:port, or full URL and returns the API base URL. */
fun normalizeAutodartsUrl(value: String): String {
    val trimmed = value.trim()
        .trimEnd('/')
        .removeSuffix("/api/state")
    if (trimmed.isBlank()) return DEFAULT_AUTODARTS_URL
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val hostWithPort = if (trimmed.contains(":")) trimmed else "$trimmed:3180"
    return "http://$hostWithPort"
}

data class AutodartsConfig(
    val url: String = DEFAULT_AUTODARTS_URL,
    val mode: ScoringMode = ScoringMode.AUTODARTS,
)

sealed interface Screen {
    data object Splash : Screen
    data object Menu : Screen
    data object AppSettings : Screen
    data object Autodarts : Screen
    data object AutoScore : Screen
    data object Licences : Screen
    data object MatchHistory : Screen
    data object Training : Screen
    data object JoinGame : Screen
    data class Mirror(val host: String, val port: Int, val code: String) : Screen
    data class MatchDetail(val id: String) : Screen
    data class Setup(val game: GameType) : Screen
    data class Settings(val game: GameType) : Screen
    data class Game(
        val game: GameType,
        val players: List<Player>,
        val autodarts: AutodartsConfig? = null,
        val invadersRules: InvadersRules? = null,
        val lavaRules: LavaRules? = null,
        val robinHoodRules: RobinHoodRules? = null,
        val whackAMoleRules: WhackAMoleRules? = null,
        val x01Rules: X01Rules? = null,
        val checkoutRules: CheckoutRules? = null,
        val gunslingerRules: GunslingerRules? = null,
    ) : Screen
}
