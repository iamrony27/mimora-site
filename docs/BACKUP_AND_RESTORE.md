# MIMORA Backup & Restore Architecture

## Overview
MIMORA provides an optional, privacy-respecting backup mechanism that packages all user-authored content into a single encrypted archive.

---

## Contents of Backup Archive

### Included in Backup:
- All diary text entries (`title`, `content`, `date`, `createdAt`, `updatedAt`, `isFavorite`)
- Personal tasks and reminders (`title`, `description`, `dueDate`, `reminderTime`, `isCompleted`)
- User-attached photos (`photos/`)
- User-recorded voice notes (`voice_notes/`)
- Metadata manifest (version, timestamp, entry count)

### Excluded from Backup:
- 12 Seasonal writing-page background assets (bundled locally in the app)
- App launcher icons and UI drawables
- App internal cache and system temporary files

---

## Backup Workflow

1. **Serialization**: Diary entries, tasks, and media files are bundled into an in-memory zip stream containing `database.json` and a `media/` directory.
2. **Entropy Generation**: A unique 16-byte salt and a 12-byte initialization vector (IV) are generated via `SecureRandom`.
3. **Key Derivation**: A 256-bit AES key is derived from the user's 24-word recovery phrase using `PBKDF2WithHmacSHA256` (100,000 iterations).
4. **Encryption**: Payload is encrypted with `AES-256-GCM` and tagged with a 128-bit authentication tag.
5. **Storage**: The resulting `.mimora` file is saved locally or queued for upload to Google Drive.

---

## At-Rest Security & Anti-Tamper

- **Android app sandbox**: all diary data, media files, and preferences live in MIMORA's private app directory. `android:allowBackup="false"` blocks ADB/Auto Backup extraction, and no content provider is exported.
- **Recovery phrase at rest**: the 24-word phrase is AES-256-GCM encrypted with a non-exportable Android Keystore key before being written to DataStore. Copying the phrase to the clipboard clears it after 60 seconds.
- **Backups in transit and at rest**: the `.mimora` archive is encrypted with a key derived from the 24-word phrase; Google stores only ciphertext in `appDataFolder`. Tokens are requested per action and never persisted.
- **Screen exposure**: `FLAG_SECURE` keeps diary and key screens out of screenshots, recordings, and the recent-apps preview.
- **Network**: TLS-only (`networkSecurityConfig`, cleartext disabled).
- **Reverse engineering**: the release build is R8-shrunk and obfuscated; its signing certificate is pinned at runtime to deter casual repackaging.
- **Residual risk**: the local SQLite diary database and media files are protected by the Android sandbox but are not individually field-encrypted. A rooted device or a forensic image of an unlocked device can read them directly. This is a deliberate trade-off to avoid a risky storage migration in the production release; field-level/local media encryption can be added in a future version with a full migration test.

## Restore Workflow

1. User selects an encrypted `.mimora` backup — from the signed-in Google Drive `appDataFolder` (also available on first-run onboarding) or from a local file picker — and inputs their 24-word recovery phrase.
2. The decryption engine validates the file header (`MIMORA_ENC_V1`), extracts the salt and IV, and derives the 256-bit key.
3. AES-256-GCM decrypts the payload and validates the authentication tag.
4. **Integrity Guarantee**: If the phrase is incorrect or any byte of the backup has been tampered with, decryption fails immediately without altering the local database.
5. Every restore surface (onboarding, local file, and Google Drive) requires the full 24 words before it can run. Restore buttons stay disabled until 24 words are present, and first-run onboarding is only completed after decryption succeeds — a wrong key can never skip the key gate.
6. Failures are reported in plain language (wrong key/damaged file, unsupported format, incomplete archive, I/O error) and never expose key material. Nothing is written to the database on failure.
7. Entries, tasks, and media files are safely imported into the local SQLite database using `CONFLICT_REPLACE` to prevent duplicate corruption.

---

## Google Drive Cloud Integration

### Authentication & Permissions:
- MIMORA uses Google's **AuthorizationClient** (`play-services-auth`) to request two scopes:
  - `https://www.googleapis.com/auth/drive.appdata` — read/write its own hidden folder only.
  - `https://www.googleapis.com/auth/userinfo.email` — read the account email so Settings can show which account is connected.
- Both scopes are classified by Google as **non-sensitive**, so the app does not need OAuth verification.
- The `drive.appdata` scope restricts the application exclusively to a hidden app-specific folder (`appDataFolder`) inside the user's Google Drive. MIMORA cannot see, read, or modify any other files in the user's Drive.
- Access tokens last about one hour and are **never persisted**. The app stores only the connection flag and the account email. Each backup/restore action requests a fresh token; Google returns it silently after the first consent.
- The 24-word recovery phrase is separate from Google Sign-In and is never sent to Google. Google stores only opaque ciphertext in `appDataFolder`; it cannot decode a MIMORA backup. Signing in or enabling cloud backup never grants a restore without the phrase.

### Drive API details:
- New uploads set `parents: ["appDataFolder"]`.
- Cloud storage uses **one archive per recovery phrase**. The file name is `mimora_backup_<16 hex>.mimora`, derived from the first 8 bytes of the SHA-256 digest of the 24-word key. The same key always targets the same file, so a second device with the same phrase updates that archive rather than creating a duplicate.
- When the stable file exists it is updated in place with `PATCH .../upload/drive/v3/files/{id}?uploadType=multipart`; when it is missing it is created with `POST .../upload/drive/v3/files?uploadType=multipart`. After a successful upload, legacy timestamped files and other duplicates are deleted; other phrases' stable files are preserved.
- Typing only auto-saves locally. A cloud backup is created only when the diary's **Save Diary** button is tapped (or the manual Backup action in Settings). If the journal has not changed since the last successful upload, no request is sent and the app reports "Already up to date". A content fingerprint (excluding volatile `updatedAt` values and encryption nonces) plus the stable file name decide whether an update is needed.
- Listings use `spaces=appDataFolder` and `orderBy=modifiedTime desc`.
- Files in `appDataFolder` are hidden from the Google Drive UI, are scoped to one Google account, and count toward that account's storage quota. Because they are hidden, a user cannot delete them from the Drive app; deleting them requires `files.delete` from the app or revoking access.

### Provisioning Credentials (For Release/Production):
1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Enable the **Google Drive API** in the API Library.
3. Configure the **OAuth consent screen** (External) and add the scopes `https://www.googleapis.com/auth/drive.appdata` and `https://www.googleapis.com/auth/userinfo.email`.
4. Create an **Android OAuth 2.0 Client ID** with package name `com.mimora.app` and your SHA-1 signing fingerprint. Add one client per fingerprint: debug, release upload key, and the Play App Signing SHA-1.
5. No `google-services.json` file is required. That file is only used by Firebase, not by Google Sign-In or the Drive REST API.

### Publishing status — LIVE (Production):
- The OAuth consent screen for project `mimora-515507` is published to **Production**. Any person with a Google account can sign in for backup; there is no test-user list and no 7-day token expiry restriction.
- Both scopes are classified by Google as **non-sensitive**, so no OAuth verification or user cap applies and no "unverified app" warning is shown.
- Published branding URLs:
  - Homepage: `https://iamrony27.github.io/mimora-site/`
  - Privacy policy: `https://iamrony27.github.io/mimora-site/privacy.html`
  - Authorized domain: `iamrony27.github.io`
- Two Android OAuth clients are registered for package `com.mimora.app`:
  - Debug: SHA-1 `12:9D:F8:B0:22:32:F3:0A:8F:DB:71:36:5D:8B:C4:56:8B:CB:4C:CF` (the AOSP debug keystore).
  - Release: SHA-1 `3B:5A:15:54:CB:F3:DA:F0:E3:25:C2:FB:B5:7E:72:13:91:1A:47:F7` (the dedicated production keystore), client named "MIMORA Android Release".
- The release APK pins the release certificate SHA-256 and refuses to run if repackaged and re-signed with another key. OAuth client IDs are bound to the signing certificate, so a repackaged APK cannot reuse MIMORA's Google sign-in.
- While the consent screen was in **Testing**, only listed test users could sign in and tokens expired after 7 days. That restriction no longer applies.


Get the debug SHA-1 with:

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android -keypass android | grep "SHA1:"
```
