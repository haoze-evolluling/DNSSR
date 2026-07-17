package com.haoze.dnssr.vpn

import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.IDN
import java.net.InetAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Locale
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager

class DynamicServerCertificateKeyManager(
    private val defaultAuthority: String
) : X509ExtendedKeyManager() {
    private val cache = object : LinkedHashMap<String, LeafMaterial>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LeafMaterial>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }
    @Volatile
    var selectedAuthority: String? = null
        private set

    override fun chooseServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, socket: Socket?): String =
        selectAlias((socket as? SSLSocket)?.handshakeSession as? ExtendedSSLSession)

    override fun chooseEngineServerAlias(
        keyType: String?,
        issuers: Array<out java.security.Principal>?,
        engine: SSLEngine?
    ): String = selectAlias(engine?.handshakeSession as? ExtendedSSLSession)

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        alias?.let(::materialFor)?.certificateChain

    override fun getPrivateKey(alias: String?): PrivateKey? = alias?.let(::materialFor)?.privateKey

    override fun getServerAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? = null
    override fun getClientAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? = null
    override fun chooseClientAlias(
        keyType: Array<out String>?, issuers: Array<out java.security.Principal>?, socket: Socket?
    ): String? = null
    override fun chooseEngineClientAlias(
        keyType: Array<out String>?, issuers: Array<out java.security.Principal>?, engine: SSLEngine?
    ): String? = null

    private fun selectAlias(session: ExtendedSSLSession?): String {
        val sni = session?.requestedServerNames
            ?.filterIsInstance<SNIHostName>()
            ?.firstOrNull()
            ?.asciiName
        val authority = normalizeAuthority(sni ?: defaultAuthority) ?: defaultAuthority
        selectedAuthority = authority
        materialFor(authority)
        return authority
    }

    @Synchronized
    private fun materialFor(authority: String): LeafMaterial = cache.getOrPut(authority) {
        createLeaf(authority)
    }

    private fun createLeaf(authority: String): LeafMaterial {
        val caCertificate = HttpsInspectionCaManager.ensureCertificate()
        val caPrivateKey = HttpsInspectionCaManager.privateKey()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - HOUR_MS)
        val notAfter = Date(now + LEAF_VALIDITY_MS)
        val subject = org.bouncycastle.asn1.x500.X500Name("CN=$authority,O=DNSSR Inspection")
        val builder = JcaX509v3CertificateBuilder(
            caCertificate,
            randomSerial(),
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        val extensionUtils = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
        )
        builder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(GeneralName(if (isIpLiteral(authority)) GeneralName.iPAddress else GeneralName.dNSName, authority))
        )
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extensionUtils.createSubjectKeyIdentifier(keyPair.public)
        )
        builder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extensionUtils.createAuthorityKeyIdentifier(caCertificate)
        )
        val holder = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(caPrivateKey))
        val leaf = CertificateFactory.getInstance("X.509")
            .generateCertificate(holder.encoded.inputStream()) as X509Certificate
        leaf.verify(caCertificate.publicKey)
        return LeafMaterial(keyPair.private, arrayOf(leaf, caCertificate))
    }

    private fun normalizeAuthority(value: String): String? {
        val candidate = value.trim().trimEnd('.')
        if (candidate.isEmpty()) return null
        if (isIpLiteral(candidate)) return InetAddress.getByName(candidate).hostAddress
        return runCatching { IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }
            .getOrNull()
    }

    private fun isIpLiteral(value: String): Boolean =
        value.contains(':') || value.all { it.isDigit() || it == '.' }

    private fun randomSerial(): BigInteger = BigInteger(159, SecureRandom()).abs().add(BigInteger.ONE)

    private data class LeafMaterial(
        val privateKey: PrivateKey,
        val certificateChain: Array<X509Certificate>
    )

    private companion object {
        const val MAX_CACHE_ENTRIES = 128
        const val HOUR_MS = 60 * 60 * 1_000L
        const val LEAF_VALIDITY_MS = 7 * 24 * HOUR_MS
    }
}
