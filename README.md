<p align="center">
  <img src="docs/icon.png" width="112" height="112" alt="Darty Time icon" />
</p>

<h1 align="center">Darty Time</h1>

<p align="center">
  A neon darts game for Android, with Autodarts integration or manual board scoring.
</p>

<p align="center">
  Kotlin · Jetpack Compose · Autodarts · Phone + tablet
</p>

---

Darty Time ships nine match modes: **Shields**, a competitive claim-the-board game for 1–6 players; **Invaders**, a shared-board alien hunt for 1–6 players; **The Board Is Lava**, a 1–6 player survival game where the board burns away; **Ring Run**, a four-round ring challenge that shrinks from the double to the bull; **Robin Hood**, a ring-accuracy score chase where every step closer to the bull is worth more; **Whack a Mole**, an ordered target race; **Cricket**, the classic close-and-score game; **X01**, the classic race to zero with 501, 301 or 121 starts and custom in/out rules; and **Gunslinger**, a beep-countdown reaction game where a random target must be hit with all three darts before the score drains away. A separate **Training** tab holds practice drills, including **Around the Clock** and **Checkout**. The app works either with an [Autodarts](https://autodarts.io) board manager on your LAN, by tapping the on-screen board, or with optional on-device camera auto-scoring.

## Features

- **Shields** — 1–6 players, editable names and a configurable dart budget.
- **Autodarts mode** — polls the local board manager (`/api/state`) every 250 ms and syncs visits automatically.
- **Manual mode** — tap the board to score; press and hold for a magnified aiming loupe, drag to fine-tune, release to score.
- **Accessible manual entry** — TalkBack users can open a ring-and-number keypad from the board's accessibility actions.
- **Second screen** — share any game over the local network and mirror it live on another device.
- **Shields rules** — one point per claimed number; doubles and trebles add shields as a bonus; three shields lock a number.
- **Invaders rules** — 1–6 players sharing three aliens on every number, with escalating 1/3/5 points and doubles/trebles killing two or three.
- **The Board Is Lava** — 1–6 players lose a life when they hit lava or miss; last player standing wins.
- **Ring Run** — 1–6 players chase four shrinking scoring rings and finish on the bull.
- **Robin Hood** — 1–6 players score by ring only; doubles are worth 1 and an inner bull is worth 8, with an editable number of rounds and a colour-coded ring guide.
- **Whack a Mole** — 1–6 players race through a random ordered target list; hard mode makes some targets double- or treble-only.
- **Cricket** — close 20 to 15 and the bull with three marks, then score on targets your opponents leave open.
- **X01** — start at 501, 301 or 121; choose straight, double or master in/out; set first-to-N legs.
- **Gunslinger** — wait for the countdown beeps, then hit a random number with all three darts; the faster the finish, the higher the score.
- **Training** — a dedicated practice tab with Around the Clock and Checkout; Checkout offers easy 2-dart or hard 3-dart finishes.
- **Remembered player names** — the six setup name slots persist between games and app launches.
- **Voice callouts** — Gunslinger speaks the revealed target number through Android TextToSpeech, with an app-level toggle.
- **Match history** — every finished game is saved on the device only; tap a match for final standings, swipe it left to delete.
- **Responsive layout** — phones stay portrait; tablets can rotate, with a leading navigation rail and a two-pane board and stats layout in landscape.
- **Sound effects** — distinct add, lock, remove and life-loss cues, with an app-level toggle.
- **Match celebration** — a full-screen result card with winner, stats, sorted standings and confetti.
- **Game menu** — the app starts on a GAMES screen; adding another game type is a `GameType` entry plus its routes.
- **Branded launch splash** — native Android splash plus an animated DARTY TIME logo reveal.
- **Clean dark UI** — neon scoreboard, animated board, bottom navigation, lock and shatter effects.

## Screens

```text
GAMES ──► choose a game ──► setup ──► play
  │                         │
  ├── TRAINING ──► choose a drill ──► setup ──► play
  │
  └── SETTINGS (app)        └── EDIT (game rules)
```

The bottom navigation is shown on the top-level **GAMES**, **TRAINING**, **STATS**, **AUTODARTS** and **SETTINGS** screens only, so it never collides with the in-game action bar.

## Shields rules

| Dart | Shields added | Goes used |
| --- | ---: | ---: |
| Single / bull | +1 | 1 |
| Double | +2 | 1 |
| Treble | +3 and locks | 1 |
| Miss | 0 | 1 |

- **Score:** one point per claimed number, regardless of how many shields it holds.
- **Winner:** most claimed numbers. Ties break by locked numbers, then total shields.
- **Locking:** reaching the lock threshold (default 3 shields, configurable) makes a number permanent.
- **Bull:** one target (`25`) — inner and outer both add one shield.
- **Every dart costs exactly one go**, so doubles and trebles are bonuses.
- **Game end:** when every player has used their dart budget (default 15 darts), or early once the board is full if enabled.

Game rules for Shields, Invaders, Lava, Robin Hood, Whack a Mole, X01 and Gunslinger live under **setup → EDIT**; Ring Run, Cricket and Around the Clock have fixed rules. Checkout uses a fixed path list with an easy/hard choice. Sound and other app-level settings live in the **SETTINGS** tab.

## Invaders rules

Invaders is a 1–6 player score attack. Each of the 20 numbers has three **shared** aliens sitting outside the board, and players take turns throwing.

| Hit | Aliens killed | Points |
| --- | ---: | --- |
| Single | 1 | 1, 3 or 5 depending on which alien it is |
| Double | 2 | points for both aliens |
| Treble | 3 | points for all three aliens |
| Miss / bull | 0 | 0 |

- The **first** alien on a number is worth **1 point**, the **second 3**, the **third 5**; the player who lands the hit collects the points.
- Aliens stay dead for everyone: if one player clears the inner aliens, the next player can collect the higher-value outer ones.
- Extra kills are wasted if fewer aliens remain (for example, a double on a number with one alien left scores 5).
- The **bull has no aliens**; a bull hit consumes a dart and scores nothing.
- Every player gets a **dart budget** (default 15, configurable) in turns of **3 darts**. Every hit costs one dart, so doubles and trebles are bonuses.
- The game ends when all **60 aliens** are cleared or every player has spent their budget.
- **Winner:** highest score; kills are the tiebreak; otherwise a draw. Maximum possible score is **180**.
- The end screen shows the winner, final standings, aliens killed and darts used.

## The Board Is Lava rules

The Board Is Lava is a 1–6 player **survival** game. There is no scoring; the last player with lives wins.

- Each player starts with **3 lives** (configurable 1–9 in the rules).
- The **first round starts with 3 lava numbers** (configurable 1–5). After each round, **3 more random numbers** become lava.
- Hitting a **lava number or missing costs one life**. The bull always stays safe; normal hits do not cost lives.
- When **all 20 numbers are lava**, the board is consumed and the run ends; the player with the most lives wins, or it is a draw if tied.
- At **zero lives** you are out. The last player standing wins.
- In **solo play**, the run ends when your lives run out; the result shows the rounds survived.
- Each player throws **3 darts per turn**.
- 1–6 players; rules live under **setup → EDIT** and are saved for the next game.

## Ring Run rules

Ring Run is a four-round target game for 1–6 players. Each round highlights one whole ring around the board; any number in that ring counts. Every player throws three darts before the next round.

| Round | Highlighted zone | Points |
| --- | --- | ---: |
| 1 | Double + large single | 1 |
| 2 | Large single on 12, 5, 20, 1, 18, 7, 19, 3, 17, 2 | 1 |
| 3 | Small single | 2 |
| 4 | Bull + outer bull | 3 |

- **Large single** is the outer single band, between the treble and double rings. **Small single** is the inner band, between the outer bull and treble ring.
- Only hits in the highlighted ring or bull area score. A hit in a different ring scores zero.
- Round 1 and round 3 use every number in the highlighted ring. Round 2 is limited to the large singles on **12, 5, 20, 1, 18, 7, 19, 3, 17 and 2**.
- Every dart consumes one of the three darts for that round, whether it scores or not.
- The final round is always the bull; inner and outer bull both score 3.
- **Winner:** highest total after four rounds. Ties are draws. Solo play reports the final score.
- Ring Run is tap/Autodarts compatible and uses the same board rendering and loupe as the other modes.

## Robin Hood rules

Robin Hood is a 1–6 player ring-accuracy game. The number hit does not matter; only how close the dart is to the bull. Each player throws three darts per round, and the number of rounds is editable in the rules.

| Zone | Points |
| --- | ---: |
| Double | 1 |
| Large single | 2 |
| Treble | 4 |
| Small single | 4 |
| Outer bull | 6 |
| Inner bull | 8 |

- A miss scores zero but still consumes a dart.
- The game ends after the configured number of rounds (default 5, editable 1–20).
- **Winner:** highest total. Ties are draws; solo play reports the final score.
- Every scoring band is a solid, different colour on the board, with a compact legend showing its point value.
- Robin Hood supports tap and Autodarts input. Inner and outer bull are scored separately.

## Cricket rules

Cricket (also called Tactics) is a 1–6 player close-and-score game. The targets are **20, 19, 18, 17, 16, 15 and the bull**.

| Hit | Marks |
| --- | ---: |
| Single | 1 |
| Double | 2 |
| Treble | 3 |
| Outer bull | 1 |
| Inner bull | 2 |

- Three marks **close** a target for that player.
- Once you have closed a target, hitting it again scores while at least one opponent still has it open: 20/40/60 on 20, 25/50 on the bull.
- A hit that closes a target scores nothing; the closing mark uses the whole visit.
- The game ends when a player has closed all seven targets and has **equal or more** points than every opponent. A player behind on points keeps scoring until they take the lead.
- Every turn is three darts. Solo play ends once the board is closed.
- Every dart consumes one go, including misses and hits on numbers outside the target list.
- The board shows each player's progress: closed targets fill in that player's colour, and multiple closed players split the target side by side.

## Around the Clock rules

Around the Clock is a 1–6 player training drill. Players hit 1 through 20 in order, then finish on the bull.

- **Target order:** 1, 2, … 20, bull.
- **Advance:** any single, double or treble on the current number counts as one step; a double or treble does not skip numbers.
- **Bull:** outer and inner bull both count when the bull is the final target.
- **Misses:** a wrong number or miss uses a dart but leaves the target unchanged.
- **Turns:** every turn is three darts.
- **Winner:** the first player to hit the bull.

## Checkout rules

Checkout is a solo training drill. A session holds **10 randomly chosen common finishes**.

- **Easy:** two-dart checkouts such as 60 = S20 → D20.
- **Hard:** three-dart checkouts such as 170 = T20 → T20 → Bull.
- **Path:** hit the displayed darts in order. A wrong dart uses a dart but leaves your target unchanged; darts refresh every three throws.
- **Finish:** the last dart must be a double or the inner bull.
- **Session:** clear all 10 checkouts to finish.

## X01 rules

X01 is a 1–6 player race to zero. Every dart subtracts its value; the first player to reach exactly zero wins the leg. The match ends when a player wins the configured number of legs.

| Setting | Options | Default |
| --- | --- | --- |
| Starting score | 501 / 301 / 121 | 501 |
| In rule | Straight / Double / Master | Straight |
| Out rule | Straight / Double / Master | Double |
| Legs to win | 1–7 | 1 |

- **Scoring:** single = face value, double = 2×, treble = 3×; outer bull = 25, inner bull = 50.
- **Double in:** the first scoring dart must be a double (inner bull counts). Darts before it do not score.
- **Master in:** the first scoring dart must be a double or treble.
- **Double out:** the final dart must be a double (inner bull counts).
- **Master out:** the final dart must be a double or treble.
- **Straight in/out:** any dart satisfies that rule.
- **Bust:** going below zero, leaving 1 when double/master out is required, or reaching zero without the required out dart ends the turn immediately; the score returns to its value at the start of the turn and the remaining darts are lost.
- **Legs:** scores reset for the next leg and the starting player alternates. The first player to the configured number of legs wins the match.
- **Input:** tap/Autodarts compatible. Autodarts visits that end early pass the turn; a bust or checkout ends the visit immediately.

## Gunslinger rules

Gunslinger is a 1–6 player reaction game. Each player gets a configured number of turns (default 5).

- Every turn starts with a countdown of beeps (default 3, configurable 0–6).
- After the beeps, a random number from **1 to 20** is revealed.
- Each player has **three darts** to hit that number. Any ring counts — single, double or treble.
- **Normal mode (default):** the two neighbouring board numbers also score, at half the current hit value; the board highlights them in a lighter shade.
- **Hard mode:** only the called number scores.
- The score starts at **1000**, drops a configurable amount per second (default **50**, adjustable in 50-point steps), and floors at **100**.
- Every scoring dart **banks the current value**, so three hits are worth more than one or two.
- Misses and wrong numbers bank nothing; banked points are kept even if the visit ends early.
- Turns alternate players; after every player has thrown, the next round starts with a fresh target.
- Highest total wins; ties are draws. Solo play reports the final score.
- The board glows the revealed target, and the panel shows the live decaying value so the player can see what the next hit is worth.
- With Autodarts, the next turn waits for the board to report the darts being taken out, then adds a short get-ready pause before the beeps start.
- With **Voice callouts** enabled in Settings, the target number is spoken as it appears so the player does not need to look at the screen.

## Autodarts

Set the board manager URL once in **Settings → AUTODARTS URL** (default `http://autodarts.local:3180`), or on the **AUTODARTS** tab. In any game setup just choose **MANUAL** or **AUTODARTS**; the URL is not repeated there. Your Manual/Autodarts choice is remembered between games. The app requests:

```text
GET <autodarts-url>/api/state
```

The board manager and phone must be on the same network. Cleartext HTTP is allowed for this local integration. The app reads `segmentNumber`, `multiplier`, `bouncer`, `failed`, `segmentName`/`bed` and the landing coordinates from each throw. Darts the board anchors to the nearest wedge but places outside the double ring (miss beds `M1`–`M20`/`MISS`/`OUT`, a zero multiplier, or a landing radius beyond the ring) are counted as misses, and a visit ends when the board manager does.

## Second screen

Every game can be mirrored to a second Android device on the same Wi-Fi network. The host keeps the game state and input; the guest is a read-only live view, so it does not need the same players, rules or Autodarts setup.

1. Start a game on the host.
2. Tap **SHARE** in the game's top bar. The dialog shows an address (`192.168.x.x:8787`) and a 4-digit code, and the host starts advertising on the LAN.
3. On the other device open **JOIN A GAME**. Shared games appear under **NEARBY SHARED GAMES**; tap one to join. If multicast is blocked by the router, enter the address and code manually.
4. Approve the device when the host shows **ALLOW SECOND SCREEN?**. Approved devices stay connected for that share session.

The guest follows throws, turns, undo, reset and the final result. It shows `MIRROR` in the top bar and reconnects automatically if the Wi-Fi hiccups. Shields keeps its full board view; the other games mirror the scoreboards, turn/round state, target lists, recent hits and a generic board view. Sharing is opt-in per game and stops when the host leaves the game; keep the host app in the foreground while playing.

## Manual play

Leave Autodarts disabled to score by hand:

- **Tap** a number on the board to score immediately.
- **Press and hold** to open a 2.4× loupe above your finger; drag to aim, then release to score. The crosshair and label show the exact hit (`T20`, `D16`, `BULL`, `MISS`).
- **TalkBack** — focus the board and use the *Enter dart manually* action to choose a ring and number, including both singles, doubles, trebles, both bulls, and a miss.
- **Undo** and **MISS** are available in the action bar.

## Build

Requirements:

- JDK 21
- Android SDK platform **36**, build-tools **36.0.0**
- Android NDK **27.1.12297006** and CMake **3.22.1**
- The project’s Gradle wrapper (Gradle 8.14.6)
- The BullTrack submodule and its pinned native dependencies

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk

git submodule update --init --depth 1
scripts/fetch-bulltrack-deps.sh
./gradlew :app:assembleDebug -PbulltrackAbis=arm64-v8a
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The app uses `minSdk 26` and `targetSdk 36`. Camera builds include a large native library and model; release packaging defaults to arm64 only.

### Tests, lint and coverage

```bash
./gradlew :core:test :vision:test :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew coverage
```

- JVM tests cover Shields, Invaders, Lava, Ring Run, Robin Hood, Whack a Mole, Cricket, X01 and Gunslinger rules, multiplayer turns, lives, round progression, geometry, Autodarts parsing, match-history storage, tablet layout decisions, sound-cue selection and the hit-pop policy.
- Lint must pass. Expected warnings are `LeanbackUsesWifi` and `ChromeOsAbiSupport` for the arm64-only native build.
- JaCoCo reports land in `core/build/reports/jacoco/test/html/`, `vision/build/reports/jacoco/test/html/`, and `app/build/reports/coverage/test/debug/`.
- App unit coverage is limited to pure policy classes; Compose screens need instrumented or Robolectric tests to be covered.

## Project layout

| Module | Purpose |
| --- | --- |
| `:core` | Pure JVM rules, scoring, board geometry and Autodarts payload parsing |
| `:vision` | Standalone OpenCV board detector and JVM tests (not bundled in the app) |
| `:bulltrack-core` | Pinned BullTrack Android detection library for camera scoring |
| `:app` | Compose UI, navigation, settings, sounds and Android lifecycle |
| `serve_apk.py` | Optional local APK server on port 8099 for sideloading builds |

Key app files include `GameScreen.kt`, `InvadersGameScreen.kt`, `InvadersAliensOverlay.kt`, `InvadersCompleteOverlay.kt`, `LavaGameScreen.kt`, `LavaBoardOverlay.kt`, `LavaCompleteOverlay.kt`, `RingRunGameScreen.kt`, `RingRunBoardOverlay.kt`, `RingRunCompleteOverlay.kt`, `RobinHoodGameScreen.kt`, `RobinHoodCompleteOverlay.kt`, `WhackAMoleGameScreen.kt`, `WhackAMoleCompleteOverlay.kt`, `CricketGameScreen.kt`, `CricketBoardOverlay.kt`, `CricketCompleteOverlay.kt`, `GunslingerGameScreen.kt`, `GunslingerBoardOverlay.kt`, `GunslingerCompleteOverlay.kt`, `BoardCanvas.kt`, `BottomNav.kt`, `GameMenuScreen.kt`, `GeneralSettingsScreen.kt`, `SoundEffects.kt`, `LayoutPolicy.kt` and `ShieldVisualPolicy.kt`. Rules and geometry are isolated in `:core` (`ShieldsMode.kt`, `InvadersMode.kt`, `LavaMode.kt`, `RingRunMode.kt`, `RobinHoodMode.kt`, `WhackAMoleMode.kt`, `CricketMode.kt`, `GunslingerMode.kt`, `BoardGeometry.kt`, `Autodarts.kt`), which keeps them testable without Android.

## Third-party assets

The shield sounds are from **Kenney Interface Sounds**, released under **CC0 1.0**. Source mirror: [Calinou/kenney-interface-sounds](https://github.com/Calinou/kenney-interface-sounds). The life-loss thud is an original synthesized cue for this project. Full attribution is in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Status and license

Active development with nine match modes plus Training: Shields, Invaders, The Board Is Lava, Ring Run, Robin Hood, Whack a Mole, Cricket, X01 and Gunslinger, plus Around the Clock and Checkout. Darty Time is licensed under the **GNU Affero General Public License v3.0 or later**. See [LICENSE](LICENSE), [NOTICE](NOTICE) and [SOURCE_OFFER.md](SOURCE_OFFER.md). Third-party assets retain their own licenses as listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

### Camera auto-scoring

Camera auto-scoring embeds [BullTrack](https://github.com/Thomasff/BullTrack)'s Android detection engine (`bulltrack-core`, pinned as a git submodule). Every game setup offers **CAMERA** as a third scoring option next to MANUAL and AUTODARTS, with an in-game preview and automatic dart input. The combined build, including BullTrack and its YOLO11s-derived model, is distributed under **AGPL-3.0-or-later**. Complete corresponding source and build instructions are described in [SOURCE_OFFER.md](SOURCE_OFFER.md); see also [docs/autoscore.md](docs/autoscore.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
