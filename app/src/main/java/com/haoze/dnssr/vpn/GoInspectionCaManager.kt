package com.haoze.dnssr.vpn

import android.content.ContentValues
import android.content.Context
import android.os.Build
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
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(EXPORTED_CERTIFICATE_NAME)
        )
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, EXPORTED_CERTIFICATE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "application/x-x509-ca-cert")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)?.use { it.write(certificatePem(context).toByteArray()) }
            ?: error("Unable to open exported certificate")
    }

    fun hasExportedCertificateInDownloads(context: Context): Boolean {
        val projection = arrayOf(MediaStore.Downloads._ID)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(EXPORTED_CERTIFICATE_NAME),
            null
        )?.use { return it.moveToFirst() }
        return false
    }

    fun deleteExportedCertificatesInDownloads(context: Context) {
        context.contentResolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(EXPORTED_CERTIFICATE_NAME)
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
}
