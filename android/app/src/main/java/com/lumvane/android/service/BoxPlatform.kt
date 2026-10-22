package com.lumvane.android.service

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.lumvane.lumcore.InterfaceListener
import com.lumvane.lumcore.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

class BoxPlatform(
    private val service: VpnService,
    private val settings: VpnSettings,
    private val onUnderlying: (Network?) -> Unit = {},
    private val onNetworkChanged: () -> Unit = {},
) : Platform {
    private val cm = service.getSystemService(ConnectivityManager::class.java)

    private val lock = Any()
    private val networks = LinkedHashSet<Network>()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var listener: InterfaceListener? = null
    private var lastIface: String? = null

    override fun protect(fd: Int): Boolean = service.protect(fd)

    override fun openTun(optionsJSON: String): Int {
        val spec = TunSpec.parse(optionsJSON)
        val b = service.Builder()
            .setSession(settings.sessionName)
            .setMtu(spec.mtu)
            .setBlocking(false)
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        if (settings.allowBypass) b.allowBypass()
        for (a in spec.inet4Address + spec.inet6Address) {
            splitCidr(a)?.let { (ip, p) -> b.addAddress(ip, p) }
        }
        val v4 = spec.inet4Routes
        val v6 = spec.inet6Routes
        if (v4.isEmpty() && v6.isEmpty()) {
            if (spec.inet4Address.isNotEmpty()) b.addRoute("0.0.0.0", 0)
            if (spec.inet6Address.isNotEmpty()) b.addRoute("::", 0)
        } else {
            for (r in v4 + v6) splitCidr(r)?.let { (ip, p) -> b.addRoute(ip, p) }
        }
        for (d in spec.dnsServers) runCatching { b.addDnsServer(d) }
        if (spec.includePackage.isNotEmpty()) {
            for (p in spec.includePackage) runCatching { b.addAllowedApplication(p) }
        } else {
            for (p in spec.excludePackage) runCatching { b.addDisallowedApplication(p) }
        }
        spec.httpProxy?.let { hp ->
            if (Build.VERSION.SDK_INT >= 29 && hp.server.isNotEmpty()) {
                b.setHttpProxy(ProxyInfo.buildDirectProxy(hp.server, hp.port, hp.bypassDomain))
            }
        }
        val pfd = b.establish() ?: throw IllegalStateException("VpnService.establish() returned null (permission revoked?)")
        synchronized(lock) {
            tunPfd?.let { runCatching { it.close() } }
            tunPfd = pfd
        }
        return pfd.fd
    }

    private var tunPfd: ParcelFileDescriptor? = null

    fun closeTun() {
        val pfd = synchronized(lock) { tunPfd.also { tunPfd = null } }
        pfd?.let { runCatching { it.close() } }
    }

    override fun interfacesJSON(): String {
        val arr = JSONArray()
        val seen = HashSet<String>()
        val all = synchronized(lock) { networks.toList() }.ifEmpty { cm.allNetworks.toList() }
        for (n in all) {
            val lp = cm.getLinkProperties(n) ?: continue
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val name = lp.interfaceName ?: continue
            if (!seen.add(name)) continue
            val index = ifIndex(name) ?: continue
            val o = JSONObject()
                .put("index", index)
                .put("mtu", if (Build.VERSION.SDK_INT >= 29) lp.mtu.takeIf { it > 0 } ?: 1500 else 1500)
                .put("name", name)
                .put("addresses", JSONArray(lp.linkAddresses.map { "${it.address.hostAddress?.substringBefore('%')}/${it.prefixLength}" }))
                .put("flags", IFF_UP or IFF_BROADCAST or IFF_RUNNING or IFF_MULTICAST)
                .put("type", transportType(caps))
                .put("dns", JSONArray(lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }))
                .put("gateway", JSONArray(gateways(lp)))
                .put("metered", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            arr.put(o)
        }
        return arr.toString()
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceListener) {
        closeDefaultInterfaceMonitor()
        this.listener = listener
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(lock) { networks.add(network) }
                publish()
            }

            override fun onLost(network: Network) {
                synchronized(lock) { networks.remove(network) }
                publish()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = publish()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = publish()
        }
        callback = cb
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        cm.registerNetworkCallback(req, cb)
        cm.allNetworks.forEach { n ->
            val caps = cm.getNetworkCapabilities(n)
            if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) synchronized(lock) { networks.add(n) }
        }
        publish()
    }

    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int,
    ): String {
        if (Build.VERSION.SDK_INT < 29) return ""
        return runCatching {
            val uid = cm.getConnectionOwnerUid(
                ipProtocol, InetSocketAddress(InetAddress.getByName(sourceAddress), sourcePort),
                InetSocketAddress(InetAddress.getByName(destinationAddress), destinationPort),
            )
            if (uid < 0) return ""
            val pkgs = service.packageManager.getPackagesForUid(uid).orEmpty()
            JSONObject().put("uid", uid).put("packages", JSONArray(pkgs.toList())).toString()
        }.getOrDefault("")
    }

    override fun closeDefaultInterfaceMonitor() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        listener = null
        lastIface = null
        synchronized(lock) { networks.clear() }
    }

    private fun publish() {
        val l = listener ?: return
        val best = pickDefault()
        onUnderlying(best)
        val lp = best?.let { cm.getLinkProperties(it) }
        val name = lp?.interfaceName
        val index = name?.let { ifIndex(it) }
        if (best == null || name == null || index == null) {
            l.update("", -1, false, false)
            return
        }
        val caps = cm.getNetworkCapabilities(best)
        val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        l.update(name, index, metered, false)
        val prev = lastIface
        lastIface = name
        if (prev != null && prev != name) onNetworkChanged()
    }

    private fun pickDefault(): Network? {
        val candidates = synchronized(lock) { networks.toList() }.filter {
            val c = cm.getNetworkCapabilities(it)
            c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                cm.getLinkProperties(it)?.interfaceName != null
        }
        if (candidates.isEmpty()) return null
        cm.activeNetwork?.let { if (it in candidates) return it }
        return candidates.minByOrNull { transportRank(cm.getNetworkCapabilities(it)) }
    }

    private fun transportRank(c: NetworkCapabilities?): Int = when {
        c == null -> 9
        c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 0
        c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
        c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
        else -> 3
    }

    private fun transportType(c: NetworkCapabilities): Int = when {
        c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
        c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 1
        c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 2
        else -> 3
    }

    private fun gateways(lp: LinkProperties): List<String> =
        lp.routes.filter { it.isDefaultRoute }.mapNotNull { it.gateway?.hostAddress?.substringBefore('%') }
            .filter { it != "0.0.0.0" && it != "::" }

    private fun ifIndex(name: String): Int? =
        runCatching { NetworkInterface.getByName(name)?.index }.getOrNull()

    private companion object {
        const val IFF_UP = 0x1
        const val IFF_BROADCAST = 0x2
        const val IFF_RUNNING = 0x40
        const val IFF_MULTICAST = 0x1000
    }
}
