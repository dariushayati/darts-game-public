# Corresponding Source Offer

Darty Time is licensed under **AGPL-3.0-or-later**. Builds that include the
BullTrack camera engine are distributed under the same license, including
BullTrack and its `model-v1` weights (AGPL-3.0-or-later / AGPL-3.0).

This file explains where recipients of a Darty Time binary can obtain the
complete corresponding source for that exact build.

## Where to obtain source

- Repository: <https://github.com/dariushayati/darts-game-public>
- Each distributed build must identify the exact source commit or tag in its
  release notes. The source for that build is the repository at that commit,
  including the pinned `third_party/BullTrack` submodule.
- If repository access is restricted, the maintainer will provide a source
  archive or temporary repository access to every recipient of the build. A
  written request is sufficient.
- This offer is valid for at least three years from the date the build was
  distributed.

## What the source includes

- All Darty Time application, `:core`, and `:vision` source and tests.
- Gradle files, `settings.gradle.kts`, build scripts, and `app/proguard-rules.pro`.
- The `third_party/BullTrack` gitlink and the exact BullTrack commit
  (`5286565b0da904417170867a24ab2175a2f34f2b`).
- `scripts/fetch-bulltrack-deps.sh`, which pins OpenCV 4.12.0, ncnn 20260113,
  and the `model-v1` YOLO model by URL and SHA-256.
- License texts and notices under `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`,
  and `licenses/`.

## Build from source

```bash
git clone https://github.com/dariushayati/darts-game-public.git
cd darts-game
git checkout <release commit or tag>
git submodule update --init --depth 1
scripts/fetch-bulltrack-deps.sh
./gradlew :app:assembleRelease -PbulltrackAbis=arm64-v8a
```

The native SDKs and model are fetched from their upstream sources by the pinned,
checksummed script. If a recipient cannot download them, the maintainer can
supply the exact downloaded archives/model alongside the source archive.

## Release checklist

For every distributed build:

1. Record the source commit/tag and release `versionCode` in the release notes.
2. Verify `git submodule status` points at the intended BullTrack commit.
3. Verify the model/native dependency hashes from
   `scripts/fetch-bulltrack-deps.sh`.
4. Provide source via the public repository, a source archive, or recipient
   access before or with the binary.
5. Keep the source offer available for at least three years.

## Network use

Darty Time includes a local second-screen mirror server. The AGPL source offer
also applies to users interacting with a modified version over a network; the
in-app Licences screen and repository point to the same source location.

## Third-party licenses

- BullTrack: `third_party/BullTrack/LICENSE` (AGPL-3.0).
- BullTrack model: AGPL-3.0.
- OpenCV: Apache-2.0 plus bundled third-party texts in `licenses/opencv/`.
- ncnn: BSD-3-Clause and bundled third-party terms in `licenses/ncnn-LICENSE.txt`.
- AndroidX/CameraX, Kotlin, Guava, Kotlinx Coroutines: Apache-2.0.
- Kenney Interface Sounds: CC0-1.0.

This document is a compliance aid, not legal advice. Have counsel review the
final distribution and source-offer process.
