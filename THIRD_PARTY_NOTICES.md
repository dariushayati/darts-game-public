# Third-Party Notices

Darty Time is licensed under the GNU Affero General Public License v3.0 or later
(AGPL-3.0-or-later). The combined camera build includes BullTrack and its
`model-v1` weights and is distributed under AGPL-3.0-or-later. Corresponding
source and build instructions are in [SOURCE_OFFER.md](SOURCE_OFFER.md).

Full license texts are in `LICENSE`, `NOTICE`, `third_party/BullTrack/LICENSE`,
and `licenses/`.

## Sound effects

- `app/src/main/res/raw/shield_add.wav` — `confirmation_001.wav` from **Kenney Interface Sounds**.
- `app/src/main/res/raw/shield_remove.wav` — `glass_002.wav` from **Kenney Interface Sounds**.
- `app/src/main/res/raw/shield_lock.wav` — `confirmation_002.wav` from **Kenney Interface Sounds** (third shield / lock).
- `app/src/main/res/raw/life_lost.wav` — original synthesized life-loss cue created for this project; not from the Kenney pack.
- `app/src/main/res/raw/miss.wav` — original synthesized miss cue created for this project; not from the Kenney pack.
- `app/src/main/res/raw/beep.wav` — original synthesized countdown beep created for this project; not from the Kenney pack.

Source: https://github.com/Calinou/kenney-interface-sounds (WAV mirror of Kenney's pack).
Original: https://kenney.nl/assets/interface-sounds
License: CC0 1.0 Universal (public domain). Attribution is not required; credited here.
License text: `licenses/CC0-1.0.txt`.

## Camera auto-scoring (BullTrack integration)

BullTrack's `bulltrack-core` is a pinned git submodule. Native SDKs and the
model are downloaded at build time by `scripts/fetch-bulltrack-deps.sh` and are
gitignored; the script and exact hashes are part of the corresponding source.

| Component | Version / commit | License | Source |
| --- | --- | --- | --- |
| BullTrack (`bulltrack-core`) | `5286565b0da904417170867a24ab2175a2f34f2b` | AGPL-3.0-or-later | https://github.com/Thomasff/BullTrack |
| BullTrack model (`model.ncnn.param` / `model.ncnn.bin`) | `model-v1`, fine-tuned from Ultralytics YOLO11s | AGPL-3.0 | https://github.com/Thomasff/BullTrack/releases/tag/model-v1 |
| Ultralytics YOLO11s (base weights/training) | model-v1 base | AGPL-3.0 | https://github.com/ultralytics/ultralytics |
| OpenCV Android SDK | 4.12.0 | Apache-2.0 | https://github.com/opencv/opencv |
| ncnn (android-vulkan prebuilt) | 20260113 | BSD-3-Clause | https://github.com/Tencent/ncnn |
| AndroidX CameraX | 1.3.4 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/camera |
| kotlinx.coroutines | 1.8.1 (BullTrack), 1.9.0 (app) | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| Guava | 33.2.1-android | Apache-2.0 | https://github.com/google/guava |
| AndroidX Concurrent Futures | 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/concurrent |

OpenCV includes further third-party components (libjpeg-turbo, libpng, libtiff,
OpenJPEG, OpenEXR, protobuf, flatbuffers and others); their license texts are
in `licenses/opencv/`. ncnn's bundled third-party terms are in
`licenses/ncnn-LICENSE.txt`. BullTrack's copyright and AGPL text are retained in
`third_party/BullTrack/LICENSE`.

## Distribution and source

Darty Time and the combined BullTrack build are distributed under
AGPL-3.0-or-later. The source offer, exact build/source mapping, and build
instructions are in [SOURCE_OFFER.md](SOURCE_OFFER.md). This document is a
notice, not legal advice; review the final distribution with counsel.
