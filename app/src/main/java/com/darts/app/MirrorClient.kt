package com.darts.app

import android.os.Build
import com.darts.core.MirrorCodec
import com.darts.core.MirrorSnapshot
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

sealed interface MirrorFetchResult {
    data class Success(val snapshot: MirrorSnapshot) : MirrorFetchResult
    data object Pending : MirrorFetchResult
    data object Denied : MirrorFetchResult
    data object WrongCode : MirrorFetchResult
    data object Incompatible : MirrorFetchResult
    data object Unreachable : MirrorFetchResult
}

fun mirrorDeviceName(): String = (Build.MODEL ?: "")
    .filter { it.code in 0x20..0x7E }
    .take(40)
    .ifBlank { "Android" }

fun parseMirrorTarget(raw: String, defaultPort: Int = DEFAULT_MIRROR_PORT): Pair<String, Int>? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return try {
            val uri = URI(trimmed)
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val port = if (uri.port > 0) uri.port else defaultPort
            host to port
        } catch (_: Throwable) {
            null
        }
    }
    val colon = trimmed.lastIndexOf(':')
    if (colon > 0) {
        val host = trimmed.substring(0, colon).trim()
        val port = trimmed.substring(colon + 1).trim().toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return host to port
    }
    return trimmed to defaultPort
}

class MirrorClient(
    private val host: String,
    private val port: Int = DEFAULT_MIRROR_PORT,
    private val code: String,
    private val deviceName: String = mirrorDeviceName(),
) {
    fun fetch(timeoutMs: Int = 1500): MirrorFetchResult {
        val encodedCode = URLEncoder.encode(code, "UTF-8")
        val encodedDevice = URLEncoder.encode(deviceName, "UTF-8")
        val urlHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        val connection = try {
            URL("http://$urlHost:$port/snapshot?code=$encodedCode&device=$encodedDevice")
                .openConnection() as HttpURLConnection
        } catch (_: Throwable) {
            return MirrorFetchResult.Unreachable
        }
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "GET"
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            when (val status = connection.responseCode) {
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    MirrorCodec.decode(body)
                        ?.let { MirrorFetchResult.Success(it) }
                        ?: MirrorFetchResult.Incompatible
                }

                403 -> when (connection.getHeaderField("X-Darty-Status")) {
                    "pending" -> MirrorFetchResult.Pending
                    "denied" -> MirrorFetchResult.Denied
                    "wrong-code" -> MirrorFetchResult.WrongCode
                    else -> MirrorFetchResult.WrongCode
                }

                else -> {
                    connection.errorStream?.close()
                    if (status in 500..599) MirrorFetchResult.Unreachable else MirrorFetchResult.WrongCode
                }
            }
        } catch (_: Throwable) {
            MirrorFetchResult.Unreachable
        } finally {
            connection.disconnect()
        }
    }
}
