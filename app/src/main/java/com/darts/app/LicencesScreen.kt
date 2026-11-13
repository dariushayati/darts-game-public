package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SOURCE_URL = "https://github.com/dariushayati/darts-game-public"
private const val SOURCE_OFFER_URL =
    "https://github.com/dariushayati/darts-game-public/blob/main/SOURCE_OFFER.md"

/** Plain in-app licence notice, including the BullTrack camera integration. */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val notices = listOf(
        "Darty Time (this application)" to
            "AGPL-3.0-or-later. Complete source and build instructions: " +
            "$SOURCE_URL (see SOURCE_OFFER.md).",
        "BullTrack detection engine" to
            "Copyright (C) 2026 Thomasff. GNU AGPL-3.0-or-later. " +
            "Source: https://github.com/Thomasff/BullTrack",
        "BullTrack model-v1 (YOLO11s, ncnn)" to
            "Fine-tuned with Ultralytics YOLO11s and distributed under AGPL-3.0. " +
            "Source: https://github.com/Thomasff/BullTrack/releases/tag/model-v1",
        "OpenCV Android SDK 4.12.0" to
            "Apache-2.0. Source: https://github.com/opencv/opencv",
        "ncnn 20260113 (android-vulkan)" to
            "BSD-3-Clause. Source: https://github.com/Tencent/ncnn. " +
            "Bundled third-party terms are in licenses/ncnn-LICENSE.txt.",
        "AndroidX CameraX 1.3.4" to "Apache-2.0.",
        "Kotlinx Coroutines 1.8.1 / 1.9.0" to "Apache-2.0.",
        "Guava 33.2.1-android" to "Apache-2.0.",
        "AndroidX Core, Lifecycle, Compose" to "Apache-2.0.",
        "Kenney Interface Sounds" to "CC0-1.0. Used for the app's sound cues.",
        "OpenCV bundled third-party notices" to
            "Full texts are bundled in the app assets under licenses/opencv/.",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DartsPalette.BackgroundDeep, DartsPalette.Background),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row {
                Text(
                    text = "BACK",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "OPEN-SOURCE LICENCES",
                    color = DartsPalette.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.weight(1f))
            }

            Text(
                text = "Darty Time is licensed AGPL-3.0-or-later. This build also embeds " +
                    "BullTrack and its model-v1 weights under AGPL-3.0-or-later; the other " +
                    "components keep their own licences. Full AGPL and third-party texts " +
                    "are bundled in the APK assets under licenses/. Complete corresponding " +
                    "source and build instructions are described in SOURCE_OFFER.md.",
                color = DartsPalette.Muted,
                fontSize = 12.sp,
            )

            Text(
                text = "VIEW SOURCE CODE",
                color = DartsPalette.BlueBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clickable { uriHandler.openUri(SOURCE_URL) }
                    .padding(vertical = 6.dp),
            )
            Text(
                text = "SOURCE OFFER & BUILD INSTRUCTIONS",
                color = DartsPalette.BlueBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clickable { uriHandler.openUri(SOURCE_OFFER_URL) }
                    .padding(bottom = 6.dp),
            )

            notices.forEach { (title, body) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DartsPalette.Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, DartsPalette.Border, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        color = DartsPalette.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = body,
                        color = DartsPalette.Muted,
                        fontSize = 11.sp,
                    )
                }
            }

            Text(
                text = "Source code: $SOURCE_URL. If repository access is restricted, " +
                    "request the corresponding source from the maintainer; see " +
                    "SOURCE_OFFER.md for the exact build mapping.",
                color = DartsPalette.Amber,
                fontSize = 11.sp,
            )
        }
    }
}
