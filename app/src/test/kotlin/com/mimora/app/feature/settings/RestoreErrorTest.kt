package com.mimora.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreErrorTest {

    @Test
    fun `wrong key is reported without leaking crypto detail`() {
        val message = SettingsViewModel.describeRestoreFailure(
            javax.crypto.AEADBadTagException("error:1e000065:Cipher functions:OPENSSL_internal:BAD_DECRYPT")
        )
        assertEquals(
            "Incorrect 24-word private key or a damaged backup. Nothing was restored.",
            message
        )
    }

    @Test
    fun `unsupported archive gives a clear reason`() {
        assertEquals(
            "This file is not a MIMORA encrypted backup, or its format is not supported.",
            SettingsViewModel.describeRestoreFailure(
                IllegalArgumentException("Invalid backup format or unsupported encryption version.")
            )
        )
    }

    @Test
    fun `unknown failures still say nothing was restored`() {
        val message = SettingsViewModel.describeRestoreFailure(Exception("socket closed"))
        assertTrue(message.contains("socket closed"))
        assertTrue(message.contains("Nothing was restored."))
    }
}
