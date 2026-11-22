package com.mimora.app.feature.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mimora.app.R
import com.mimora.app.design.MimoraShapes
import com.mimora.app.design.MimoraSpacing

/** Single source of truth for whether this device can actually use fingerprint unlock. */
object BiometricSupport {
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
}

private fun promptFingerprint(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context as? FragmentActivity
    if (activity == null) {
        onError("Fingerprint unlock is unavailable here. Use your PIN.")
        return
    }
    if (!BiometricSupport.isAvailable(context)) {
        onError("No fingerprint is enrolled on this device. Use your PIN.")
        return
    }

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED ||
                errorCode == BiometricPrompt.ERROR_USER_CANCELED
            ) {
                return
            }
            onError(errString.toString())
        }

        override fun onAuthenticationFailed() {
            onError("Fingerprint not recognized. Try again or use your PIN.")
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
    }

    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), callback)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(promptInfo)
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        shape = MimoraShapes.medium,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SecurityError(message: String?) {
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Root-level unlock screen. Biometrics are attempted first when enabled, with a
 * always-available PIN fallback so a missing sensor never traps the user out.
 */
@Composable
fun AppUnlockGate(
    biometricEnabled: Boolean,
    pinHash: String,
    securityManager: SecurityManager,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val biometricAvailable = remember { BiometricSupport.isAvailable(context) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val launchBiometric = {
        error = null
        promptFingerprint(
            context = context,
            title = "Unlock MIMORA",
            subtitle = "Use your fingerprint to open your private diary.",
            onSuccess = onUnlocked,
            onError = { error = it }
        )
    }

    LaunchedEffect(biometricEnabled, biometricAvailable, pinHash) {
        if (biometricEnabled && biometricAvailable && pinHash.isNotBlank()) {
            launchBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(MimoraSpacing.large),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = MimoraShapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MimoraSpacing.large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MimoraShapes.large)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_security),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(MimoraSpacing.normal))
                Text(
                    text = "Welcome back",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(MimoraSpacing.extraSmall))
                Text(
                    text = "Your memories are still private.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(MimoraSpacing.large))

                if (biometricEnabled && biometricAvailable) {
                    Button(
                        onClick = launchBiometric,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MimoraShapes.full,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fingerprint),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock with fingerprint")
                    }
                    Spacer(modifier = Modifier.height(MimoraSpacing.normal))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .height(1.dp)
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Text(
                            text = "  or PIN  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .height(1.dp)
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Spacer(modifier = Modifier.height(MimoraSpacing.normal))
                }

                PinField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        error = null
                    },
                    label = "Security PIN"
                )
                Spacer(modifier = Modifier.height(MimoraSpacing.small))
                SecurityError(error)
                Spacer(modifier = Modifier.height(MimoraSpacing.small))

                Button(
                    onClick = {
                        when {
                            pin.length < 4 -> error = "Enter your PIN (at least 4 digits)."
                            securityManager.verifyPin(pin, pinHash) -> {
                                error = null
                                onUnlocked()
                            }
                            else -> {
                                error = "Incorrect PIN. Try again."
                                pin = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MimoraShapes.full
                ) {
                    Text("Unlock diary")
                }
            }
        }
    }
}

/**
 * PIN/fingerprint gate used by destructive actions. If no PIN exists yet, the
 * user creates one inline and the same action continues after verification.
 */
@Composable
fun SecureActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    biometricEnabled: Boolean,
    pinHash: String,
    securityManager: SecurityManager,
    onPinCreated: (String, () -> Unit) -> Unit,
    onAuthenticated: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val needsPinCreation = pinHash.isBlank()
    val biometricAvailable = remember { BiometricSupport.isAvailable(context) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val launchBiometric = {
        error = null
        promptFingerprint(
            context = context,
            title = title,
            subtitle = "Confirm with your fingerprint to continue.",
            onSuccess = onAuthenticated,
            onError = { error = it }
        )
    }

    LaunchedEffect(biometricEnabled, biometricAvailable, needsPinCreation, pinHash) {
        if (biometricEnabled && biometricAvailable && !needsPinCreation) {
            launchBiometric()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_security),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MimoraSpacing.small)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (biometricEnabled && biometricAvailable && !needsPinCreation) {
                    Button(
                        onClick = launchBiometric,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MimoraShapes.full,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fingerprint),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use fingerprint")
                    }
                }

                if (needsPinCreation) {
                    Text(
                        text = "Create a security PIN first. This protects every future delete in MIMORA.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    PinField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            error = null
                        },
                        label = "New PIN (4-6 digits)"
                    )
                    PinField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it
                            error = null
                        },
                        label = "Confirm PIN"
                    )
                } else {
                    PinField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            error = null
                        },
                        label = "Security PIN"
                    )
                }

                SecurityError(error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (needsPinCreation) {
                        when {
                            pin.length < 4 -> error = "PIN must be at least 4 digits."
                            pin != confirmPin -> error = "PINs do not match."
                            else -> {
                                onPinCreated(securityManager.hashPin(pin), onAuthenticated)
                            }
                        }
                    } else {
                        when {
                            pin.length < 4 -> error = "Enter your PIN (at least 4 digits)."
                            securityManager.verifyPin(pin, pinHash) -> {
                                error = null
                                onAuthenticated()
                            }
                            else -> {
                                error = "Incorrect PIN. Try again."
                                pin = ""
                            }
                        }
                    }
                },
                shape = MimoraShapes.full
            ) {
                Text(if (needsPinCreation) "Create PIN & $confirmLabel" else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** Public helper for the Settings fingerprint switch. */
@Composable
fun rememberFingerprintAvailable(): Boolean {
    val context = LocalContext.current
    return remember(context) { BiometricSupport.isAvailable(context) }
}
