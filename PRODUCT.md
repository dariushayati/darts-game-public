# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Home darts players playing in small groups of 1–6, usually on a phone or tablet beside a dartboard. They may have an Autodarts board manager on the same LAN or a plain board with no sensors.

## Product Purpose

Darty Time is a camera-free-by-default Android darts game that runs a full match on the device. It scores either from a local Autodarts board manager or from manual taps on the on-screen board. Success means a group can start a game quickly, see the live state clearly, and finish with a trustworthy result without creating an account or depending on a cloud service.

## Positioning

Local-first darts scoring that treats Autodarts and manual tap input as equal first-class paths. It combines nine match modes plus a Training tab with optional live mirroring to a second Android device on the same Wi-Fi, with no backend, account, or advertising and no camera requirement in the shipping build.

## Operating Context

- Play happens with players physically around one dartboard, taking turns.
- Autodarts runs a board manager on the local network; the app polls its `/api/state` endpoint over cleartext HTTP.
- Manual play uses taps and press-and-hold loupe aiming on the phone or tablet.
- A second Android device can join over the LAN to mirror the live game.
- Phones stay in portrait; tablets can rotate; Android TV is supported as an optional leanback target.
- Match history, settings, and remembered player names stay on the device.

## Capabilities and Constraints

- Kotlin and Jetpack Compose app with `minSdk 26` and `targetSdk 36`.
- Nine match modes plus a Training tab: Shields, Invaders, The Board Is Lava, Ring Run, Robin Hood, Whack a Mole, Cricket, X01 (501/301/121), Gunslinger, Around the Clock, and Checkout.
- 1–6 players, remembered six name slots, configurable rules for most match modes; training drills use fixed paths, with an easy/hard choice for Checkout.
- Autodarts polling, manual scoring, undo, miss, match history, sound effects, and Gunslinger voice callouts.
- Second-screen host/guest mirroring over the local network.
- No backend, login, cloud sync, camera requirement, or advertising in the shipping build.
- Optional on-device camera auto-scoring is available (BullTrack, AGPL-3.0-or-later).
- Darty Time and combined camera builds are licensed AGPL-3.0-or-later; see `LICENSE`, `NOTICE` and `SOURCE_OFFER.md`.
- Autodarts integration accepts cleartext HTTP because it targets a local board manager.
- Player data and match results are device-local and are not uploaded.

## Brand Commitments

- Existing product name: Darty Time.
- Existing launcher icon and splash assets live in the repository.

## Evidence on Hand

- The README documents the modes, rules, Autodarts contract, second-screen flow, build, and test commands.
- The repository contains the working Android implementation and its test suite.
- No customer testimonials, press coverage, pricing, or third-party benchmarks are available; future work must not fabricate them.

## Product Principles

1. Keep core play local and account-free.
2. Treat Autodarts and manual tap scoring as equally valid ways to play.
3. Design for a group around one board, not for a solo benchmark.
4. Make the current player, target, and score legible at a glance on phone, tablet, and mirrored screens.
5. Preserve trust in the result: scoring, history, and player data stay accurate and on the device.
