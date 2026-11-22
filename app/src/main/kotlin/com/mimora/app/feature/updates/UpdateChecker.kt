package com.mimora.app.feature.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseNotes: String
)

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data class Error(val message: String) : UpdateResult
}

/**
 * Reads the public GitHub Releases API. No token, key or private endpoint is
 * involved, and nothing about the user's diary or account is sent.
 */
object UpdateChecker {
    private const val LATEST_RELEASE =
        "https://api.github.com/repos/iamrony27/mimora-site/releases/latest"

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "MIMORA-Update-Checker")
            try {
                when (connection.responseCode) {
                    200 -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        val version = json.optString("tag_name", "").removePrefix("v")
                        if (version.isBlank()) {
                            UpdateResult.Error("No published release was found.")
                        } else {
                            val info = UpdateInfo(
                                version = version,
                                downloadUrl = json.optString("html_url", RELEASES_URL),
                                releaseNotes = json.optString("body", "")
                                    .lines()
                                    .filter { it.isNotBlank() }
                                    .take(8)
                                    .joinToString("\n")
                                    .ifBlank { "A newer MIMORA build is ready." }
                            )
                            if (isNewer(version, currentVersion)) {
                                UpdateResult.Available(info)
                            } else {
                                UpdateResult.UpToDate
                            }
                        }
                    }

                    else -> UpdateResult.Error("Update service returned ${connection.responseCode}.")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            UpdateResult.Error("Could not reach the update service. Check your connection.")
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").mapNotNull { it.toIntOrNull() }
        val b = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    fun openReleasePage(context: Context, url: String = RELEASES_URL) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private const val RELEASES_URL =
        "https://github.com/iamrony27/mimora-site/releases"
}
