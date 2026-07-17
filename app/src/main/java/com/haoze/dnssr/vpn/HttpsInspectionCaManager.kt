package com.haoze.dnssr.vpn

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Locale

object HttpsInspectionCaManager {
    private const val KEY_ALIAS = "dnssr_https_inspection_ca_v1"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val SUBJECT = "CN=DNSSR User CA,O=DNSSR"
    private const val VALIDITY_DAYS = 3650L
    const val EXPORTED_CERTIFICATE_NAME = "DNSSR-User-CA.cer"
    private const val CERTIFICATE_MIME_TYPE = "application/x-x509-ca-cert"

    @Synchronized
    fun ensureCertificate(): X509Certificate {
        val keyStore = loadKeyStore()
        (keyStore.getCertificate(KEY_ALIAS) as? X509Certificate)
            ?.takeIf(::isInspectionCa)
            ?.let { return it }

        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        val now = System.currentTimeMillis()
        val notBefore = Date(now - DAY_MS)
        val notAfter = Date(now + VALIDITY_DAYS * DAY_MS)
        val subject = X500Name(SUBJECT)
        val keyPair = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE).run {
            initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(3072)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(javax.security.auth.x500.X500Principal(SUBJECT))
                    .setCertificateSerialNumber(randomSerial(now))
                    .setCertificateNotBefore(notBefore)
                    .setCertificateNotAfter(notAfter)
                    .build()
            )
            generateKeyPair()
        }

        val certificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            randomSerial(now),
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        val extensionUtils = JcaX509ExtensionUtils()
        certificateBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        certificateBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign or KeyUsage.digitalSignature)
        )
        certificateBuilder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extensionUtils.createSubjectKeyIdentifier(keyPair.public)
        )
        certificateBuilder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extensionUtils.createAuthorityKeyIdentifier(keyPair.public)
        )
        val holder = certificateBuilder.build(
            JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        )
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(holder.encoded.inputStream()) as X509Certificate
        certificate.verify(keyPair.public)
        keyStore.setEntry(
            KEY_ALIAS,
            KeyStore.PrivateKeyEntry(keyPair.private, arrayOf(certificate)),
            null
        )
        return certificate
    }

    @Synchronized
    fun reset(): X509Certificate {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        return ensureCertificate()
    }

    fun certificateDer(): ByteArray = ensureCertificate().encoded

    fun hasExportedCertificateInDownloads(context: Context): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            downloadCertificateSelection,
            downloadCertificateSelectionArgs,
            null
        )?.use { return it.moveToFirst() }
        return false
    }

    fun deleteExportedCertificatesInDownloads(context: Context): Int =
        context.contentResolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            downloadCertificateSelection,
            downloadCertificateSelectionArgs
        )

    fun exportCertificateToDownloads(context: Context): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, EXPORTED_CERTIFICATE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, CERTIFICATE_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create certificate file in Downloads")
        return try {
            resolver.openOutputStream(uri, "w")?.use { it.write(certificateDer()) }
                ?: error("Unable to open certificate file in Downloads")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Synchronized
    fun privateKey(): PrivateKey {
        ensureCertificate()
        return loadKeyStore().getKey(KEY_ALIAS, null) as PrivateKey
    }

    fun fingerprintSha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(certificateDer())
        .joinToString(":") { "%02X".format(Locale.ROOT, it.toInt() and 0xff) }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun isInspectionCa(certificate: X509Certificate): Boolean =
        certificate.basicConstraints >= 0 && certificate.subjectX500Principal.name.contains("DNSSR User CA")

    private val downloadCertificateSelection =
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
    private val downloadCertificateSelectionArgs = arrayOf(
        "${Environment.DIRECTORY_DOWNLOADS}/",
        "$EXPORTED_CERTIFICATE_NAME%"
    )

    private fun randomSerial(seed: Long): BigInteger = BigInteger.valueOf(seed).shiftLeft(16)
        .or(BigInteger.valueOf((Math.random() * 65535).toLong()))

    private const val DAY_MS = 24 * 60 * 60 * 1_000L
}
