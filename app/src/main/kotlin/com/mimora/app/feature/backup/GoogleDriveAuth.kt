package com.mimora.app.feature.backup

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles Google authorization for the hidden Drive app data folder.
 *
 * We use Google's AuthorizationClient (not the deprecated GoogleSignIn API).
 * The access token is short-lived (about one hour) and is never persisted:
 * call [authorize] again before a Drive operation and Google will usually
 * return a fresh token silently.
 */
class GoogleDriveAuth(private val context: Context) {

    companion object {
        /**
         * Hidden, app-only folder. Documented by Google as a non-sensitive scope.
         * MIMORA cannot see any other file in the user's Drive.
         */
        val DRIVE_APPDATA: Scope = Scope("https://www.googleapis.com/auth/drive.appdata")

        private val USERINFO_EMAIL: Scope =
            Scope("https://www.googleapis.com/auth/userinfo.email")
    }

    sealed interface Outcome {
        data class Token(val accessToken: String) : Outcome
        data class ConsentNeeded(val intentSender: IntentSender) : Outcome
    }

    /**
     * Requests Drive access. If the user has already granted it, this returns
     * a token without showing any UI.
     */
    suspend fun authorize(): Outcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(DRIVE_APPDATA, USERINFO_EMAIL))
            .build()

        val result = Identity.getAuthorizationClient(context)
            .authorize(request)
            .await()

        return if (result.hasResolution()) {
            Outcome.ConsentNeeded(
                requireNotNull(result.pendingIntent) { "Google returned no consent intent" }.intentSender
            )
        } else {
            Outcome.Token(
                requireNotNull(result.accessToken) { "Google returned no access token" }
            )
        }
    }

    /**
     * Reads the token after the consent screen finishes.
     * Call this from the ActivityResult callback.
     */
    fun tokenFromConsent(data: Intent?): String? {
        if (data == null) return null
        return Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(data)
            .accessToken
    }

    /**
     * Turns a Play Services failure into something a person can act on.
     * DEVELOPER_ERROR almost always means this build's signing certificate is
     * not registered as an Android OAuth client in Google Cloud Console.
     */
    fun describeFailure(error: Throwable): String {
        val api = error as? ApiException
            ?: return error.message?.takeIf { it.isNotBlank() } ?: "Unknown Google error"
        return when (api.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "This build is not registered with Google. Add an Android OAuth client " +
                    "for package ${context.packageName} with this build's SHA-1 fingerprint."
            CommonStatusCodes.SIGN_IN_REQUIRED ->
                "Add a Google account to this device, then try again."
            CommonStatusCodes.NETWORK_ERROR ->
                "No network connection. Connect and try again."
            CommonStatusCodes.API_NOT_CONNECTED ->
                "Google Play services is unavailable or out of date."
            else -> "Google error ${api.statusCode}: ${api.message ?: "unknown"}"
        }
    }

    /**
     * Optional: resolves the signed-in account email so the Settings screen can
     * show which Google account is connected. Failures are non-fatal.
     */
    suspend fun fetchAccountEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("https://www.googleapis.com/oauth2/v3/userinfo")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            if (connection.responseCode !in 200..299) {
                return@runCatching null
            }

            val json = JSONObject(
                connection.inputStream.bufferedReader().use { it.readText() }
            )
            json.optString("email").ifBlank { null }
        }.getOrNull()
    }
}
