package com.darts.app

import com.darts.core.Autodarts
import com.darts.core.AutodartsState
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of a board control call. */
enum class AutodartsControlResult { OK, UNSUPPORTED, FAILED }

/**
 * Minimal client for the local Autodarts Board Manager (HTTP, port 3180 by default).
 * All calls are blocking; invoke them from an IO dispatcher.
 */
class AutodartsClient(url: String) {
    private val base = normalizeAutodartsUrl(url)

    fun fetchState(timeoutMs: Int = 1200): AutodartsState? {
        val body = get("/api/state", timeoutMs) ?: return null
        return Autodarts.parseState(body)
    }

    fun fetchVersion(): String? =
        cleanVersion(get("/api/version", 1500)) ?: cleanVersion(get("/api/host", 1500))

    private fun cleanVersion(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        Regex("\"(?:version|boardVersion|desktopVersion)\"\\s*:\\s*\"([^\"]+)\"")
            .find(text)
            ?.let { return it.groupValues[1] }
        return text.take(48)
    }

    fun start(): AutodartsControlResult =
        send("PUT", listOf("/api/detection/start", "/api/start"))

    fun stop(): AutodartsControlResult =
        send("PUT", listOf("/api/detection/stop", "/api/stop"))

    fun reset(): AutodartsControlResult =
        send("POST", listOf("/api/reset"))

    fun calibrate(): AutodartsControlResult =
        send("POST", listOf("/api/config/calibration/auto"))

    private fun get(path: String, timeoutMs: Int): String? {
        val connection = open(path, "GET", timeoutMs) ?: return null
        return try {
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.close()
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Tries each path in order; the first non-404/405 response wins. */
    private fun send(method: String, paths: List<String>): AutodartsControlResult {
        var unsupported = false
        for (path in paths) {
            val connection = open(path, method, 2500) ?: continue
            try {
                val code = connection.responseCode
                connection.errorStream?.close()
                when {
                    code in 200..299 -> return AutodartsControlResult.OK
                    code == 404 || code == 405 -> unsupported = true
                    else -> return AutodartsControlResult.FAILED
                }
            } catch (_: Throwable) {
                return AutodartsControlResult.FAILED
            } finally {
                connection.disconnect()
            }
        }
        return if (unsupported) AutodartsControlResult.UNSUPPORTED else AutodartsControlResult.FAILED
    }

    private fun open(path: String, method: String, timeoutMs: Int): HttpURLConnection? = try {
        (URL("$base$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = method
            instanceFollowRedirects = true
        }
    } catch (_: Throwable) {
        null
    }
}
