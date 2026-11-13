# Experimental camera auto-scoring (BullTrack)

This branch embeds the standalone `bulltrack-core` Android library from
[BullTrack](https://github.com/Thomasff/BullTrack). The React Native UI is not used.

## Build

Prerequisites:

- JDK 17+ and Android SDK 36.
- Android NDK `27.1.12297006` and CMake `3.22.1`.
- `git submodule update --init --depth 1`.

Native SDKs and the YOLO model are downloaded on demand:

```sh
scripts/fetch-bulltrack-deps.sh          # OpenCV 4.12.0, ncnn 20260113, model-v1
./gradlew :app:assembleDebug -PbulltrackAbis=arm64-v8a
```

`preBuild` runs the fetch script automatically. Downloads are SHA-256 pinned and land
under `third_party/BullTrack/bulltrack-core/`. They are gitignored. Use
`-PbulltrackAbis=arm64-v8a,armeabi-v7a` to package more than arm64.

The APK/AAB contains `lib/arm64-v8a/libbulltrack.so`; verify with `unzip -l`.

## Code map

| File | Purpose |
| --- | --- |
| `app/.../autoscore/AutoScoreEngine.kt` | Session lifecycle, single-thread native calls, `StateFlow` state machine |
| `app/.../autoscore/AutoScoreDriver.kt` | `DartDetector` + native-only extras, with a production adapter |
| `app/.../autoscore/AutoScoreModel.kt` | States, placement guide, `ThrowResult` → `DartHit` mapping |
| `app/.../AutoScoreCameraScreen.kt` | Standalone camera test/placement screen with guide, status, corrections and mini board |
| `app/.../CameraScoringController.kt` | In-game engine owner: dart stream, three-dart turn completion, turn resets |
| `app/.../CameraScoringOverlay.kt` | Small in-game CameraX preview and detection status |
| `app/.../ScoringMode.kt` | `MANUAL` / `AUTODARTS` / `CAMERA` input selection |
| `app/.../LicencesScreen.kt` | In-app open-source notices |
| `scripts/fetch-bulltrack-deps.sh` | Pinned native/model downloads |

`AutoScoreEngine` keeps every native call on one dispatcher because a BullTrack session
is not thread-safe. Frames are submitted as `ARGB_8888` bitmaps and recycled by the
engine. Processing is throttled to about 7 fps; the camera analyser is also throttled.

## In-game camera scoring (all modes)

Every game setup now offers a third scoring option, **CAMERA**, next to MANUAL and
AUTODARTS. When selected, the game runs the same BullTrack engine headlessly and feeds
each detected dart into that mode's normal `DartHit` path. A camera button at the bottom-right opens a full
preview with pinch/slider zoom, reset and a re-detect action; closing it returns the
camera to the headless mode:

- The first frame of each turn calibrates/resets the native round.
- Each newly detected dart is scored immediately.
- Most modes advance the turn automatically when the third dart fills the turn; the
  camera controller does not advance it a second time. X01 bust/checkout, Lava
  elimination and Checkout path completion are handled as early turn ends.
- After a turn the controller waits until `detectPrepBoard` finds no dart tips for about
  2.5 seconds before starting the next native round. This avoids calibrating on a hand or
  the old darts, which previously caused a start-round error after the third dart.
- If a start still fails, the controller backs off for 1.5 seconds and retries. The camera
  dialog switches its action to RECALIBRATE when the engine is in an error/waiting state.
- A manual END TURN or an early bust/checkout changes the X01 turn key, which starts the
  same wait-for-clear path for the next player.
- X01 busts and leg-winning checkouts have already advanced the game. When the darts are
  removed the camera clears the game's ignore flag and starts the next round, so END TURN
  is not needed. Lava eliminations and Checkout path completions use the same wait-for-clear
  path. Manual END TURN in any mode also waits for the board to clear before restarting.
- The camera button's dot and the top-bar indicator show calibration/ready/re-detect/error
  status. Camera settings (soft board, CPU YOLO, debug images, tracking rate) come from
  Settings; every setting has an info tooltip. The tracking rate trades responsiveness
  against battery and heat (140/200/300 ms per sample).
- Zoom is preserved across opening/closing the preview and remembered between turns and
  games. It is shared with the standalone camera setup screen, so a game starts on the
  last zoom level used there.

The standalone camera screen is still the place to check placement and correct a round
before starting a match.

## State machine

`Idle → Calibrating → Ready → DartDetected… → TurnComplete`, with
`PlacingCamera(guide)`, `CameraMoved`, `Redetecting`, and `Error` branches. `redetect()`
is called automatically for alignment/camera-move gate reasons and can also be triggered
manually. `endTurn()` calls `findMissedDarts()` and merges any darts the frame loop did
not confirm.

## Current limitations / follow-ups

- CAMERA is available in every game mode. The shared controller handles turn input, but
  in-game correction is still only on the standalone camera screen.
- In-game correction is not available yet. The camera test screen supports removing a
  wrong dart and adding a miss; editing a dart's segment or ring, and dragging markers
  on the real board, are not implemented.
- A manual early end in-game does not call `findMissedDarts()`; the next turn simply
  resets the round. Missed darts are best caught before the turn ends.
- Release builds still compile BullTrack with debug images enabled because the flag is a
  compile-time constant inside `bulltrack-core/build.gradle`. Changing it needs a small
  upstream patch or a fork; the app keeps debug images disabled at runtime by default.
- R8 is off in this app. A keep rule for `NativeDartDetector`'s native methods is in
  `app/proguard-rules.pro` for when minification is enabled.
- Performance fallbacks (CPU/GPU probe, low-end device downgrade) and the full device
  matrix are not automated.

## Manual test matrix

Record the percentage of darts scored correctly without correction.

| Scenario | Runs | Correct darts | Notes |
| --- | ---: | ---: | --- |
| Straight-on camera | | | |
| ~30° camera angle | | | |
| ~45° camera angle | | | |
| Bright lighting | | | |
| Dim lighting | | | |
| Mixed / backlit | | | |
| Steel board | | | |
| Soft board (`softBoard = true`) | | | |
| Darts clustered in one segment | | | |
| Dart hidden behind another | | | |
| Camera bumped mid-turn | | | auto re-detect path |
| X01: three camera darts in one turn | | | auto turn end |
| X01: manual END TURN after 1–2 camera darts | | | next round resets |
| X01: bust/checkout then END TURN | | | next round resets |
| Darts pulled (manual end) | | | `findMissedDarts` path |
| Low-end phone | | | |
| Mid-range phone | | | |
| High-end phone | | | |

Detection needs a real device; an emulator is only useful for the UI.

## Licence

Darty Time and the combined build containing BullTrack and the `model-v1` weights are
distributed under **AGPL-3.0-or-later**. BullTrack itself is AGPL-3.0-or-later and the
model is AGPL-3.0. Complete corresponding source, including the pinned
`third_party/BullTrack` submodule, build scripts, native-dependency checksums and model
pin, is described in [SOURCE_OFFER.md](../SOURCE_OFFER.md). License texts are in
[LICENSE](../LICENSE), [NOTICE](../NOTICE), [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)
and [licenses/](../licenses/).

Any build distributed to testers must be accompanied by or offered with that source.
The source offer also covers the local mirror server.
