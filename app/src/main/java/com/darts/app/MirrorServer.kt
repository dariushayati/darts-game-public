package com.darts.app

import android.content.Context
import android.net.ConnectivityManager
import com.darts.core.MirrorCodec
import com.darts.core.MirrorSessionInfo
import com.darts.core.MirrorSnapshot
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

const val DEFAULT_MIRROR_PORT = 8787

data class MirrorPendingClient(
    val address: String,
    val deviceName: String,
)

object MirrorNetwork {
    fun localIpv4Address(context: Context? = null): String? {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (nif in interfaces) {
                    if (!nif.isUp || nif.isLoopback) continue
                    for (address in nif.inetAddresses) {
                        val text = address.hostAddress ?: continue
                        if (text.contains(':')) continue
                        if (address.isLoopbackAddress) continue
                        if (address.isSiteLocalAddress) addresses.add(text)
                    }
                }
            }
        } catch (_: Throwable) {
        }
        if (context != null && addresses.isEmpty()) {
            try {
                val manager = context.getSystemService(ConnectivityManager::class.java)
                val network = manager?.activeNetwork
                val link = network?.let { manager.getLinkProperties(it) }
                link?.linkAddresses?.forEach { linkAddress ->
                    val address = linkAddress.address
                    if (address is Inet4Address && address.isSiteLocalAddress) {
                        address.hostAddress?.let(addresses::add)
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return addresses.firstOrNull { it.startsWith("192.168.") }
            ?: addresses.firstOrNull { it.startsWith("10.") }
            ?: addresses.firstOrNull { it.startsWith("172.") }
            ?: addresses.firstOrNull()
    }
}

class MirrorServer(
    val session: MirrorSessionInfo,
    private val preferredPort: Int = DEFAULT_MIRROR_PORT,
) {
    private val running = AtomicBoolean(false)
    private val approved = ConcurrentHashMap.newKeySet<String>()
    private val denied = ConcurrentHashMap.newKeySet<String>()
    private val pending = ConcurrentHashMap<String, MirrorPendingClient>()

    @Volatile
    var onPendingChanged: ((List<MirrorPendingClient>) -> Unit)? = null

    @Volatile
    private var snapshot: MirrorSnapshot? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    var port: Int = preferredPort
        private set

    val isRunning: Boolean get() = running.get()
    val clientCount: Int get() = approved.size

    fun approve(address: String) {
        pending.remove(address)
        denied.remove(address)
        approved.add(address)
        notifyPending()
    }

    fun deny(address: String) {
        pending.remove(address)
        denied.add(address)
        notifyPending()
    }

    fun start(): Boolean {
        if (running.get()) return true
        val socket = bind(preferredPort) ?: bind(0) ?: return false
        serverSocket = socket
        port = socket.localPort
        running.set(true)
        thread(isDaemon = true, name = "darty-mirror-server") {
            acceptLoop(socket)
        }
        return true
    }

    fun publish(next: MirrorSnapshot) {
        snapshot = next
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverSocket = null
        approved.clear()
        denied.clear()
        pending.clear()
        notifyPending()
    }

    private fun bind(candidate: Int): ServerSocket? = try {
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(candidate))
        }
    } catch (_: Throwable) {
        null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (_: Throwable) {
                break
            }
            thread(isDaemon = true, name = "darty-mirror-client") { handle(client) }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 3000
            val reader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            val target = requestLine.split(' ').getOrNull(1) ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val path = target.substringBefore('?')
            val code = queryParam(target, "code")
            when {
                path == "/snapshot" && code != session.code ->
                    respond(
                        socket,
                        403,
                        "wrong code",
                        "text/plain",
                        mapOf("X-Darty-Status" to "wrong-code"),
                    )

                path == "/snapshot" -> handleSnapshot(socket, target)

                path == "/ping" -> respond(socket, 200, "ok", "text/plain")
                else -> respond(socket, 404, "not found", "text/plain")
            }
        } catch (_: Throwable) {
        } finally {
            try {
                socket.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun handleSnapshot(socket: Socket, target: String) {
        val address = socket.inetAddress?.hostAddress ?: "unknown"
        when {
            address in denied -> respond(
                socket,
                403,
                "denied",
                "text/plain",
                mapOf("X-Darty-Status" to "denied"),
            )

            address in approved -> {
                val current = snapshot
                if (current == null) {
                    respond(socket, 503, "not ready", "text/plain")
                } else {
                    respond(socket, 200, MirrorCodec.encode(current), "application/json")
                }
            }

            else -> {
                val name = queryParam(target, "device")
                    ?.trim()
                    ?.take(40)
                    ?.takeIf { it.isNotEmpty() }
                    ?: "Second screen"
                val existing = pending.putIfAbsent(address, MirrorPendingClient(address, name))
                if (existing == null) notifyPending()
                respond(
                    socket,
                    403,
                    "pending",
                    "text/plain",
                    mapOf("X-Darty-Status" to "pending"),
                )
            }
        }
    }

    private fun notifyPending() {
        val snapshot = pending.values.sortedBy { it.address }
        onPendingChanged?.invoke(snapshot)
    }

    private fun queryParam(target: String, name: String): String? {
        val query = target.substringAfter('?', "")
        if (query.isBlank()) return null
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?.let { URLDecoder.decode(it, "UTF-8") }
    }

    private fun respond(
        socket: Socket,
        status: Int,
        body: String,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        val reason = when (status) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            503 -> "Service Unavailable"
            else -> "Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }
}
