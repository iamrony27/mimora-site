package com.mimora.app.feature.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.mimora.app.feature.settings.SettingsViewModel
import kotlinx.coroutines.launch

/** Drive action requested from the Settings card or a screen's top bar. */
sealed interface DriveAction {
    data object Connect : DriveAction
    data object Sync : DriveAction
    data object Restore : DriveAction
    data object Refresh : DriveAction
    data class RestoreFile(val fileId: String, val phrase: String) : DriveAction
}

/**
 * Shared Google authorization launcher. One instance serves the Settings card
 * and every top bar, so consent is handled the same way everywhere.
 */
@Composable
fun rememberDriveAuthorizer(viewModel: SettingsViewModel): (DriveAction) -> Unit {
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<DriveAction?>(null) }

    fun run(action: DriveAction, token: String) {
        when (action) {
            DriveAction.Connect -> viewModel.onGoogleDriveConnected(token)
            DriveAction.Sync -> viewModel.uploadToGoogleDriveApi(token)
            DriveAction.Restore -> viewModel.openGoogleDriveRestoreDialog(token)
            DriveAction.Refresh -> viewModel.fetchGoogleDriveApiBackups(token)
            is DriveAction.RestoreFile ->
                viewModel.restoreFromGoogleDriveApi(token, action.fileId, action.phrase)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.resultCode == Activity.RESULT_OK) {
            val token = viewModel.completeGoogleDriveConsent(result.data)
            if (token != null && action != null) {
                run(action, token)
            } else {
                viewModel.onGoogleDriveAuthError("Google did not return an access token.")
            }
        } else {
            viewModel.onGoogleDriveAuthError("Sign-in was cancelled.")
        }
    }

    return { action ->
        scope.launch {
            try {
                when (val outcome = viewModel.authorizeGoogleDrive()) {
                    is GoogleDriveAuth.Outcome.Token -> run(action, outcome.accessToken)
                    is GoogleDriveAuth.Outcome.ConsentNeeded -> {
                        pendingAction = action
                        consentLauncher.launch(
                            IntentSenderRequest.Builder(outcome.intentSender).build()
                        )
                    }
                }
            } catch (e: Exception) {
                viewModel.onGoogleDriveAuthFailure(e)
            }
        }
    }
}
