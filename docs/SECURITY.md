# MIMORA Security and Threat Model

## Philosophy
User diary entries represent deeply personal, private reflections. MIMORA enforces a privacy-first, offline-first security model where data remains exclusively on the user's device unless explicitly exported or backed up with user consent.

---

## Threat Model & Attack Vectors

| Attack Vector | Threat Level | Mitigation Strategy |
| :--- | :--- | :--- |
| **Physical Device Snooping** | Medium | Optional App Lock with PIN or Biometrics; PIN hashed using PBKDF2 (100,000 rounds) with a unique 16-byte random salt. |
| **Unauthorized Cloud Access** | High | Zero cloud storage of plaintext data. All backups are encrypted locally before transmission using AES-256-GCM. |
| **Backup Tampering / Bit-flipping** | High | Authenticated Encryption with Associated Data (AEAD) via AES-256-GCM. 128-bit authentication tag guarantees rejection of tampered archives. |
| **Brute-Force Attack on Backups** | High | 256 bits of entropy via 24-word BIP-39 mnemonic phrase + PBKDF2WithHmacSHA256 (100,000 iterations) with a unique 16-byte random salt per backup. |
| **Log Leakage** | Critical | Secrets, recovery phrases, PINs, and diary contents are never logged to logcat or printed to disk. |
| **Inter-App Data Access** | High | Internal app-specific storage (`context.filesDir`); no external storage permissions requested or required. |

---

## Cryptographic Specification

### 1. Key Derivation Function (KDF)
- **Algorithm**: `PBKDF2WithHmacSHA256`
- **Salt**: 16 cryptographically secure random bytes generated via `SecureRandom` per encryption operation.
- **Iterations**: 100,000 rounds.
- **Derived Key**: 256-bit AES key.

### 2. Authenticated Encryption
- **Algorithm**: `AES/GCM/NoPadding`
- **Key Length**: 256 bits.
- **Initialization Vector (IV)**: 12 cryptographically secure random bytes per backup. Nonces are never reused.
- **Authentication Tag**: 128 bits.
- **Header Authentication**: Header bytes `MIMORA_ENC_V1` are included as Associated Data (`cipher.updateAAD()`) to prevent version mismatch or header swapping.

### 3. Payload Format (`.mimora`)
```
[ 13 bytes : Header "MIMORA_ENC_V1" ]
[ 16 bytes : Salt ]
[ 12 bytes : IV (Nonce) ]
[ N bytes  : Ciphertext + 16-byte GCM Tag ]
```

---

## 24-Word Recovery Phrase
- The recovery phrase consists of 24 words selected from a 2048-word dictionary (256 bits of entropy).
- **Zero Cloud Transmission**: The phrase is never uploaded to Google Drive or any remote server.
- **User Responsibility**: Losing the phrase renders encrypted backups permanently unrecoverable.
