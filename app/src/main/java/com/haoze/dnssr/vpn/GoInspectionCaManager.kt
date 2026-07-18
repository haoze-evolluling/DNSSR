package com.haoze.dnssr.vpn

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import tunnel.Engine
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Root CA lifecycle for the Go MITM engine. The private key stays in app-private storage. */
object GoInspectionCaManager {
    const val EXPORTED_CERTIFICATE_NAME = "DNSSR-Go-HTTPS-CA.crt"

    fun certificateDirectory(context: Context): File = File(context.filesDir, "go-mitm").apply { mkdirs() }

    fun certificatePem(context: Context): String {
        val engine = Engine()
        return try {
            engine.startStackMitm(certificateDirectory(context).absolutePath)
        } finally {
            engine.stopStackMitm()
        }
    }

    fun fingerprintSha256(context: Context): String = fingerprint(certificatePem(context))

    fun isInstalled(context: Context): Boolean {
        val expected = fingerprint(certificatePem(context))
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        return store.aliases().asSequence()
            .filter { it.startsWith("user:") }
            .mapNotNull { store.getCertificate(it) as? X509Certificate }
            .any { certificate -> fingerprint(certificate.encoded) == expected }
    }

    fun exportCertificateToDownloads(context: Context) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, EXPORTED_CERTIFICATE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(certificatePem(context).toByteArray()) }
                ?: error("Unable to open exported certificate")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    fun hasExportedCertificateInDownloads(context: Context): Boolean {
        val projection = arrayOf(MediaStore.Downloads._ID)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            downloadCertificateSelection,
            downloadCertificateSelectionArgs,
            null
        )?.use { return it.moveToFirst() }
        return false
    }

    fun deleteExportedCertificatesInDownloads(context: Context) {
        context.contentResolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            downloadCertificateSelection,
            downloadCertificateSelectionArgs
        )
    }

    fun reset(context: Context) {
        certificateDirectory(context).listFiles()?.forEach { file ->
            if (file.name == "ca.crt" || file.name == "ca.key" || file.name == "mitm_blacklist.txt") file.delete()
        }
    }

    private fun fingerprint(pem: String): String {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(pem.byteInputStream()) as X509Certificate
        return fingerprint(certificate.encoded)
    }

    private fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(":") { "%02X".format(it) }

    private val downloadCertificateSelection =
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
            "(${MediaStore.MediaColumns.DISPLAY_NAME} = ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?)"
    private val downloadCertificateSelectionArgs = arrayOf(
        "${Environment.DIRECTORY_DOWNLOADS}/",
        EXPORTED_CERTIFICATE_NAME,
        "DNSSR-Go-HTTPS-CA (%).crt"
    )
}
