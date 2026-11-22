package com.mimora.app.feature.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.mimora.app.BuildConfig
import java.security.MessageDigest

/**
 * Lightweight tamper check. A repackaged APK must be re-signed, which changes
 * its signing certificate. The release build pins the expected SHA-256 of that
 * certificate and refuses to run when it does not match.
 *
 * This is a deterrent against casual repackaging, not a guarantee against a
 * determined attacker who can patch bytecode. R8 obfuscation and the
 * Keystore-backed secrets are the primary defences.
 */
object AppIntegrity {

    fun isSigningCertificateTrusted(context: Context): Boolean {
        // Debug builds use the well-known Android debug key; skip the check.
        if (BuildConfig.EXPECTED_CERT_SHA256.isBlank()) return true

        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val certificate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.firstOrNull()
            } ?: return false

            val digest = MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray())
            val actual = digest.joinToString(":") { "%02X".format(it) }
            actual.equals(BuildConfig.EXPECTED_CERT_SHA256, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
}
