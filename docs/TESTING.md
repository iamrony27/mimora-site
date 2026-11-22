# MIMORA Automated Testing Documentation

## Test Suite Overview

MIMORA includes comprehensive automated unit tests covering business rules, date guards, data models, PIN hashing, recovery phrase derivation, and authenticated AES-256-GCM encryption/decryption.

---

## Test Cases and Verification

### 1. `DateRulesTest`
- **`isDateAllowedForWriting_disallowsFutureDates`**: Verifies that any date strictly after today (e.g. tomorrow, next year) returns `false` and is blocked from diary writing.
- **`isDateAllowedForWriting_allowsTodayAndPastDates`**: Verifies that today, yesterday, and any past historical dates return `true`.
- **`dateFormattingAndParsing_roundtripsCorrectly`**: Ensures ISO-8601 local date formatting and parsing roundtrips with 100% fidelity.
- **`diaryEntry_flagsComputedCorrectly`**: Verifies dynamic computation of `hasText`, `hasPhoto`, `hasVoice`, and `isEmpty` properties on diary records.

### 2. `SecurityManagerTest`
- **`generate24WordRecoveryPhrase_generatesExact24ValidWords`**: Verifies that generated recovery phrases contain exactly 24 valid words selected from the BIP-39 dictionary.
- **`validateRecoveryPhrase_rejectsInvalidPhrases`**: Verifies rejection of phrases with incorrect word counts or invalid/misspelled words.
- **`pinHashingAndVerification_succeedsWithCorrectPin`**: Verifies PBKDF2 (100,000 rounds) salt and hash generation, ensuring valid PINs verify and incorrect PINs are rejected.
- **`recoveryPhraseEncryptionDecryption_roundtripsSuccessfully`**: Verifies that plaintext diary data encrypted with a 24-word phrase decrypts to the exact original plaintext.
- **`recoveryPhraseDecryption_failsWithWrongPhrase`**: Verifies that attempting decryption with a different recovery phrase throws an authentication failure exception without leaking data.
- **`recoveryPhraseDecryption_failsOnTamperedCiphertext`**: Verifies that bit-flipping or tampering with ciphertext triggers an AEAD authentication tag mismatch.

---

## Running the Tests

```bash
./gradlew testDebugUnitTest
```

### Execution Results
- **Total Tests**: 10
- **Failures**: 0
- **Ignored**: 0
- **Success Rate**: 100%
- **Duration**: ~2 seconds
- **HTML Report**: `app/build/reports/tests/testDebugUnitTest/index.html`
