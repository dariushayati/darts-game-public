package com.darts.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.darts.core.MIRROR_PROTOCOL_VERSION
import com.darts.core.MirrorSessionInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

const val MIRROR_SERVICE_TYPE = "_darty._tcp."

private const val TAG = "DartyMirror"

private fun normalizeServiceType(value: String): String = value.trimEnd('.')

private fun codeFromServiceName(name: String): String? =
    Regex("\\d{4}").findAll(name).lastOrNull()?.value

private fun acquireMirrorMulticastLock(context: Context, lockTag: String): WifiManager.MulticastLock? = try {
    context.applicationContext.getSystemService(WifiManager::class.java)
        ?.createMulticastLock(lockTag)
        ?.apply {
            setReferenceCounted(false)
            acquire()
        }
} catch (error: Throwable) {
    Log.w(TAG, "multicast lock failed", error)
    null
}

data class DiscoveredGame(
    val serviceName: String,
    val host: String,
    val port: Int,
    val code: String,
    val gameType: String,
    val gameTitle: String,
    val hostName: String,
)

class MirrorAdvertiser(
    private val context: Context,
    private val session: MirrorSessionInfo,
    private val servicePort: Int,
    private val onStatus: (String) -> Unit = {},
) {
    private val manager = context.applicationContext.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var listener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile
    private var settled = true

    private val timeout = Runnable {
        if (!settled) onStatus("TIMEOUT")
    }

    fun start() {
        if (listener != null) return
        settled = false
        val info = try {
            NsdServiceInfo().apply {
                serviceName = "DartyTime-${session.code}"
                serviceType = MIRROR_SERVICE_TYPE
                setPort(servicePort)
                try {
                    setAttribute("code", session.code)
                    setAttribute("game", session.gameType)
                    setAttribute("title", session.gameTitle)
                    setAttribute("host", session.hostName)
                    setAttribute("proto", MIRROR_PROTOCOL_VERSION.toString())
                } catch (error: Throwable) {
                    Log.w(TAG, "attributes failed, using name-only discovery", error)
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "service info failed", error)
            reportFailure("info", error)
            return
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "advertising ${serviceInfo.serviceName} on ${serviceInfo.port}")
                settled = true
                main.removeCallbacks(timeout)
                onStatus("ON")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "registration failed: $errorCode")
                settled = true
                main.removeCallbacks(timeout)
                onStatus("FAILED ($errorCode)")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "unregistration failed: $errorCode")
            }
        }
        val nsd = manager
        if (nsd == null) {
            settled = true
            onStatus("UNAVAILABLE")
            return
        }
        multicastLock = acquireMirrorMulticastLock(context, "darty-mirror-advertise")
        listener = registration
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
            main.postDelayed(timeout, 4000)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "register rejected dotted type, retrying dotless", error)
            try {
                info.serviceType = MIRROR_SERVICE_TYPE.trimEnd('.')
                nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
                main.postDelayed(timeout, 4000)
            } catch (retry: Throwable) {
                Log.w(TAG, "register retry failed", retry)
                reportFailure("register", retry)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "register failed", error)
            reportFailure("register", error)
        }
    }

    private fun reportFailure(phase: String, error: Throwable) {
        settled = true
        listener = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
        val reason = error.message?.take(90) ?: error.javaClass.simpleName
        onStatus("FAILED ($phase: $reason)")
    }

    fun stop() {
        settled = true
        main.removeCallbacks(timeout)
        val registration = listener
        listener = null
        try {
            if (registration != null) manager?.unregisterService(registration)
        } catch (_: Throwable) {
        }
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }
}

class MirrorDiscovery(
    context: Context,
    private val onGames: (List<DiscoveredGame>) -> Unit,
    private val onError: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val games = ConcurrentHashMap<String, DiscoveredGame>()
    private val pending = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        multicastLock = acquireMirrorMulticastLock(appContext, "darty-mirror-discovery")
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = publish()

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (normalizeServiceType(serviceInfo.serviceType) == normalizeServiceType(MIRROR_SERVICE_TYPE)) {
                    pending.add(serviceInfo)
                    resolveNext()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                games.remove(serviceInfo.serviceName)
                publish()
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed: $errorCode")
                main.post { onError("Network discovery failed ($errorCode). Enter the address manually.") }
                stop()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "stop discovery failed: $errorCode")
            }
        }
        discoveryListener = discovery
        try {
            manager?.discoverServices(MIRROR_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (_: Throwable) {
            running = false
        }
    }

    fun stop() {
        if (!running) return
        running = false
        val discovery = discoveryListener
        discoveryListener = null
        try {
            if (discovery != null) manager?.stopServiceDiscovery(discovery)
        } catch (_: Throwable) {
        }
        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Throwable) {
        }
        multicastLock = null
        games.clear()
        pending.clear()
        resolving.set(false)
        main.post { onGames(emptyList()) }
    }

    private fun resolveNext() {
        if (!running || !resolving.compareAndSet(false, true)) return
        val serviceInfo = pending.poll()
        if (serviceInfo == null) {
            resolving.set(false)
            return
        }
        val nsd = manager
        if (nsd == null) {
            finishResolve()
            return
        }
        try {
            nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed for ${serviceInfo.serviceName}: $errorCode")
                    finishResolve()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (running) {
                        val host = serviceInfo.host?.hostAddress
                        val attributes = serviceInfo.attributes.orEmpty().mapValues { (_, value) ->
                            String(value, Charsets.UTF_8)
                        }
                        val proto = attributes["proto"]
                        val compatible = proto == null || proto == MIRROR_PROTOCOL_VERSION.toString()
                        val code = attributes["code"]?.takeIf { it.isNotBlank() }
                            ?: codeFromServiceName(serviceInfo.serviceName)
                        if (host != null && compatible && code != null) {
                            games[serviceInfo.serviceName] = DiscoveredGame(
                                serviceName = serviceInfo.serviceName,
                                host = host,
                                port = serviceInfo.port,
                                code = code,
                                gameType = attributes["game"].orEmpty(),
                                gameTitle = attributes["title"]?.takeIf { it.isNotBlank() }
                                    ?: attributes["game"].orEmpty().ifBlank { "GAME" },
                                hostName = attributes["host"].orEmpty(),
                            )
                            publish()
                        }
                    }
                    finishResolve()
                }
            })
        } catch (_: Throwable) {
            finishResolve()
        }
    }

    private fun finishResolve() {
        resolving.set(false)
        resolveNext()
    }

    private fun publish() {
        if (!running) return
        val snapshot = games.values.sortedWith(
            compareBy<DiscoveredGame>({ it.gameTitle }, { it.hostName }, { it.serviceName }),
        )
        main.post { if (running) onGames(snapshot) }
    }

}
