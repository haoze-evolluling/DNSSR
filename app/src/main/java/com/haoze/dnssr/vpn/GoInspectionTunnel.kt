package com.haoze.dnssr.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.haoze.dnssr.ui.DnsResolutionMode
import tunnel.AppUidResolver
import tunnel.DomainChecker
import tunnel.Engine
import tunnel.HttpLogCallback
import tunnel.LogCallback
import tunnel.SocketProtector
import tunnel.UIDResolver
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import org.json.JSONObject
import org.json.JSONArray

/**
 * Owns the GPL-3.0 Go full-TUN data plane used only while HTTP inspection is
 * enabled. The Go stack owns TUN reads, so the DNS-only Kotlin packet loop
 * must not run concurrently with this object.
 */
class GoInspectionTunnel(
    private val context: Context,
    private val vpnService: DnsVpnService,
    private val scope: CoroutineScope,
    private var dnsConfig: HttpsDnsConfigSnapshot,
    private val inspectionEnabled: Boolean,
    private val selectedPackages: Set<String>,
    private val blockedPackages: Set<String>,
    private val policy: DomainPolicy,
    private val rewriteRuleManager: RewriteRuleManager,
    private val dnsLogger: DnsLogger,
    private val httpRequestLogger: HttpRequestLogger,
    private val filterHttp3: Boolean,
    private val blockEncryptedDns: Boolean
) {
    private val engine = Engine()
    private var startJob: Job? = null

    fun start(tunFileDescriptor: Int): Boolean = runCatching {
        configureEngine(selectedPackages)
        startJob = scope.launch(Dispatchers.IO) {
            engine.startFull(tunFileDescriptor.toLong(), SocketProtector { fd ->
                vpnService.protect(fd.toInt())
            })
        }
        true
    }.onFailure { Log.e(TAG, "Unable to start Go inspection tunnel", it) }.getOrDefault(false)

    fun stop() {
        startJob?.cancel()
        startJob = null
        runCatching { engine.stop() }.onFailure { Log.w(TAG, "Unable to stop Go inspection tunnel", it) }
        runCatching { engine.stopStackMitm() }
    }

    fun releaseTun() {
        runCatching { engine.releaseTun() }
            .onFailure { Log.w(TAG, "Unable to release Go inspection TUN", it) }
    }

    fun updateRewriteRules() {
        engine.setRewriteRules(JSONObject(rewriteRuleManager.cnameRedirects()).toString())
    }

    @Synchronized
    fun syncDnsConfig(
        providers: List<DnsProvider>,
        resolutionMode: DnsResolutionMode,
        blockResponseMode: BlockResponseMode,
        dynamicBlockResponseConfig: DynamicBlockResponseConfig
    ) {
        val next = HttpsDnsConfigSnapshot.create(
            providers, resolutionMode, blockResponseMode, dynamicBlockResponseConfig
        )
        engine.applyDNSConfig(next.toJson())
        dnsConfig = next
    }

    private fun configureEngine(selectedPackages: Set<String>) {
        syncDnsConfig(
            dnsConfig.providers,
            dnsConfig.mode,
            dnsConfig.blockResponseMode,
            dnsConfig.dynamicBlockResponseConfig
        )
        updateRewriteRules()
        engine.setDomainChecker(object : DomainChecker {
            override fun isBlocked(domain: String): Boolean = policy.evaluate(domain) is DomainDecision.Block

            override fun getBlockReason(domain: String): String =
                (policy.evaluate(domain) as? DomainDecision.Block)?.matchedRule.orEmpty()

            override fun hasCustomRule(domain: String): Long = when (val decision = policy.evaluate(domain)) {
                is DomainDecision.Block -> 1L
                is DomainDecision.Allow -> if (decision.matchedRule != null) 0L else -1L
            }
        })
        engine.setLogCallback(object : LogCallback {
            override fun onDNSQuery(
                domain: String,
                blocked: Boolean,
                queryType: Long,
                responseTimeMs: Long,
                appName: String,
                resolvedIP: String,
                blockedBy: String
            ) {
                scope.launch {
                    dnsLogger.log(
                        queryName = domain,
                        queryType = queryType.toInt(),
                        result = if (blocked) LogResult.BLOCKED else LogResult.PASSED,
                        message = buildDnsLogMessage(appName, resolvedIP, blockedBy, responseTimeMs)
                    )
                }
            }
        })
        engine.setHttpLogCallback(object : HttpLogCallback {
            override fun onHttpEvent(
                packageName: String,
                authority: String,
                protocol: String,
                outcome: String,
                matchedRule: String
            ) {
                scope.launch {
                    httpRequestLogger.log(
                        packageName = packageName,
                        authority = authority.ifBlank { null },
                        protocol = protocol,
                        outcome = outcome.toHttpRequestOutcome(),
                        matchedRule = matchedRule.ifBlank { null }
                    )
                }
            }
        })
        engine.setUIDResolver(ConnectionOwnerUidResolver(context))
        engine.setAppUidResolver(AppPackageResolver(context))
        engine.setUseTcpStack(true)
        engine.setBlockedUIDs(blockedPackages.mapNotNull(::packageUid).joinToString(","))
        if (inspectionEnabled) {
            engine.startStackMitm(GoInspectionCaManager.certificateDirectory(context).absolutePath)
            engine.setMitmAllowedUIDs(selectedPackages.mapNotNull(::packageUid).joinToString(","))
        }
        engine.setFilterHttp3(filterHttp3)
        engine.setBlockEncryptedDns(blockEncryptedDns)
        context.assets.open("https_passthrough.txt").bufferedReader().use {
            engine.setExtraPassthroughSuffixes(it.readText())
        }
    }

    private fun HttpsDnsConfigSnapshot.toJson(): String = JSONObject()
            .put("mode", mode.storageValue)
            .put("blockResponse", blockResponseMode.goValue)
            .put("dynamicResponse", JSONObject()
                .put("enabled", dynamicBlockResponseConfig.enabled)
                .put("requestThreshold", dynamicBlockResponseConfig.requestThreshold)
                .put("windowSeconds", dynamicBlockResponseConfig.windowSeconds)
                .put("nxDomainDurationSeconds", dynamicBlockResponseConfig.nxDomainDurationSeconds))
            .put("providers", JSONArray().apply {
                providers.forEach { provider ->
                    put(JSONObject()
                        .put("id", provider.id)
                        .put("protocol", provider.protocol.goProtocol)
                        .put("server", when (provider.protocol) {
                            DnsProtocol.DNS, DnsProtocol.DOT -> provider.hostPort()
                            DnsProtocol.DOH -> ""
                        })
                        .put("url", provider.url))
                }
            })
            .toString()

    private val DnsProtocol.goProtocol: String
        get() = when (this) {
            DnsProtocol.DNS -> "PLAIN"
            DnsProtocol.DOH -> "DOH"
            DnsProtocol.DOT -> "DOT"
        }

    private fun DnsProvider.hostPort(): String =
        if (host.contains(':') && !host.startsWith('[')) "[$host]:$port" else "$host:$port"

    private val BlockResponseMode.goValue: String
        get() = when (this) {
            BlockResponseMode.NXDOMAIN -> "NXDOMAIN"
            BlockResponseMode.NODATA -> "NODATA"
            BlockResponseMode.REFUSED -> "REFUSED"
            BlockResponseMode.ZERO_ADDRESS -> "CUSTOM_IP"
        }

    private fun packageUid(packageName: String): Int? =
        runCatching { context.packageManager.getPackageUid(packageName, 0) }.getOrNull()

    private companion object {
        const val TAG = "GoInspectionTunnel"
    }
}

data class HttpsDnsConfigSnapshot private constructor(
    val providers: List<DnsProvider>,
    val mode: DnsResolutionMode,
    val blockResponseMode: BlockResponseMode,
    val dynamicBlockResponseConfig: DynamicBlockResponseConfig
) {
    companion object {
        fun create(
            providers: List<DnsProvider>,
            mode: DnsResolutionMode,
            blockResponseMode: BlockResponseMode,
            dynamicBlockResponseConfig: DynamicBlockResponseConfig
        ): HttpsDnsConfigSnapshot {
            require(providers.isNotEmpty()) { "DNS provider list must not be empty" }
            require(mode == DnsResolutionMode.SINGLE || mode == DnsResolutionMode.PRIMARY_BACKUP) {
                "Go tunnel DNS does not support ${mode.storageValue}"
            }
            val selected = if (mode == DnsResolutionMode.SINGLE) listOf(providers.first()) else providers
            return HttpsDnsConfigSnapshot(selected, mode, blockResponseMode, dynamicBlockResponseConfig)
        }
    }

}

private fun buildDnsLogMessage(
    appName: String,
    resolvedIP: String,
    blockedBy: String,
    responseTimeMs: Long
): String? = listOfNotNull(
    appName.takeIf { it.isNotBlank() }?.let { "app=$it" },
    resolvedIP.takeIf { it.isNotBlank() }?.let { "resolved=$it" },
    blockedBy.takeIf { it.isNotBlank() }?.let { "blocked_by=$it" },
    responseTimeMs.takeIf { it > 0 }?.let { "elapsed=${it}ms" }
).joinToString(", ").takeIf { it.isNotEmpty() }

private fun String.toHttpRequestOutcome(): HttpRequestOutcome = when (this) {
    "blocked" -> HttpRequestOutcome.BLOCKED
    "rewritten" -> HttpRequestOutcome.REWRITTEN
    "decryption_failed" -> HttpRequestOutcome.DECRYPTION_FAILED
    "invalid" -> HttpRequestOutcome.INVALID
    else -> HttpRequestOutcome.ALLOWED
}

private class ConnectionOwnerUidResolver(context: Context) : UIDResolver {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun resolveUID(
        protocol: Long,
        localIP: String,
        localPort: Long,
        remoteIP: String,
        remotePort: Long
    ): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Process.INVALID_UID.toLong()
        return runCatching {
            connectivityManager.getConnectionOwnerUid(
                protocol.toInt(),
                InetSocketAddress(InetAddress.getByName(localIP), localPort.toInt()),
                InetSocketAddress(InetAddress.getByName(remoteIP), remotePort.toInt())
            ).toLong()
        }.getOrDefault(Process.INVALID_UID.toLong())
    }
}

private class AppPackageResolver(context: Context) : AppUidResolver {
    private val packageManager = context.packageManager

    override fun packageForUid(uid: Long): String =
        packageManager.getPackagesForUid(uid.toInt())?.firstOrNull().orEmpty()
}
