package com.albaconecta.app

import androidx.biometric.BiometricPrompt
import com.albaconecta.app.util.isUserInitiatedCancellation
import com.albaconecta.app.util.shouldShowBiometricLogin
import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricLoginPolicyTest {

    @Test fun showBiometricLoginIsTrueOnlyWithAllConditionsMet() {
        assertEquals(true, shouldShowBiometricLogin(enabled = true, available = true, userExists = true))
    }

    @Test fun showBiometricLoginIsFalseWhenNotEnabled() {
        assertEquals(false, shouldShowBiometricLogin(enabled = false, available = true, userExists = true))
    }

    @Test fun showBiometricLoginIsFalseWhenHardwareMissing() {
        assertEquals(false, shouldShowBiometricLogin(enabled = true, available = false, userExists = true))
    }

    @Test fun showBiometricLoginIsFalseWithoutAccount() {
        assertEquals(false, shouldShowBiometricLogin(enabled = true, available = true, userExists = false))
    }

    @Test fun userCanceledIsUserInitiatedCancellation() {
        assertEquals(true, isUserInitiatedCancellation(BiometricPrompt.ERROR_USER_CANCELED))
    }

    @Test fun negativeButtonIsUserInitiatedCancellation() {
        assertEquals(true, isUserInitiatedCancellation(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
    }

    @Test fun lockoutIsNotUserInitiatedCancellation() {
        assertEquals(false, isUserInitiatedCancellation(BiometricPrompt.ERROR_LOCKOUT))
    }
}
