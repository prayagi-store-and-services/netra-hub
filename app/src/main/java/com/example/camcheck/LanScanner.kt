package com.example.camcheck

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

sealed class ScanOutcome {
    /** The phone is not on Wi-Fi (or Ethernet), or the network is not a private one. */
    data class NotAvailable(val reason: String) : ScanOutcome()
    data class Done(val checked: Int, val found: Int) : ScanOutcome()
}

/**
 * Looks at the Wi-Fi network the phone is connected to and lists devices that answer.
 * It only runs when the user taps Scan, only on the phone's own private address range
 * (never more than 254 addresses), keeps nothing and sends nothing anywhere.
 */
object LanScanner {
    private const val CONNECT_TIMEOUT_MS = 300
    private const val PARALLEL = 48

    fun ownNetwork(context: Context): Pair<String, Int>? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net) ?: return null
        val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!local) return null
        val lp = cm.getLinkProperties(net) ?: return null
        val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress } ?: return null
        return Pair(la.address.hostAddress ?: return null, la.prefixLength)
    }

    private fun portOpen(ip: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS); true }
    } catch (e: Exception) {
        false
    }

    suspend fun scan(
        context: Context,
        onProgress: (done: Int, total: Int) -> Unit,
        onDevice: (LanDevice) -> Unit
    ): ScanOutcome = withContext(Dispatchers.IO) {
        val own = ownNetwork(context)
            ?: return@withContext ScanOutcome.NotAvailable("Not connected to Wi-Fi. Connect to the network you want to check.")
        val targets = CameraCheckLogic.scanTargets(own.first, own.second)
        if (targets.isEmpty()) return@withContext ScanOutcome.NotAvailable("This network does not use a normal private address range, so it was not scanned.")
        val sem = Semaphore(PARALLEL)
        var done = 0
        var found = 0
        coroutineScope {
            targets.map { ip ->
                async {
                    sem.withPermit {
                        val open = CameraCheckLogic.PROBE_PORTS.filter { portOpen(ip, it) }
                        val alive = open.isNotEmpty() || try { InetAddress.getByName(ip).isReachable(CONNECT_TIMEOUT_MS) } catch (e: Exception) { false }
                        if (alive) {
                            val name = withTimeoutOrNull(1200L) {
                                try { InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip } } catch (e: Exception) { null }
                            }
                            val dev = LanDevice(ip, name, open, CameraCheckLogic.cameraHint(name, open))
                            synchronized(this@LanScanner) { found++ }
                            onDevice(dev)
                        }
                        val d = synchronized(this@LanScanner) { ++done }
                        onProgress(d, targets.size)
                    }
                }
            }.awaitAll()
        }
        ScanOutcome.Done(targets.size, found)
    }
}
