# MIMORA Changelog

## [1.8.1] - 2026-11-22

### Fixed
- **No more duplicate cloud backups**: each 24-word recovery phrase now owns exactly one encrypted archive in Google Drive. Saving with the same phrase updates that file in place instead of creating a new one; legacy timestamped duplicates left by earlier versions are cleaned up automatically.
- **No redundant uploads**: a content fingerprint plus the backup file name are remembered after each successful sync. Saving or syncing an unchanged diary now reports "Already up to date" instead of re-uploading.
- **Explicit saves only**: typing still auto-saves locally, but a cloud backup is created only by the diary's **Save Diary** button or the Settings **Sync now** action.

### Added
- **Google Drive status indicator**: a Drive icon with a green **Online** dot (connected) or red **Offline** dot (not connected) now appears in the title bar, on the Personal Journal card, and in the cloud-sync section. Google Drive icons also replace the generic cloud icons in the backup/sync controls.

## [1.8.0] - 2026-11-22

### Added
- **Evening writing reminder**: MIMORA now asks for notification permission and, once granted, schedules a single gentle nudge at 7:00 PM every day. The reminder can be turned off in Settings → MIMORA & You, and it is automatically re-armed after a device restart. No diary text is ever placed in the notification.
- **Check for updates**: a Settings action queries the public GitHub Releases API and reports whether a newer MIMORA build is available, with a direct link to the releases page. No account, key, or diary data is sent.
- **Star MIMORA prompt**: three days after the first launch, MIMORA asks once — and only once — for a GitHub star. The decision is remembered permanently.
- **Report an issue**: a Settings action opens the GitHub issue form so bugs and ideas can be raised without exposing any personal contact address.
- **UPI donation**: a "Support the developer" action offers the UPI ID `7074018142@ptsbi`, with a payment deep link and a copy button.
- **A note from the developer**: a short, personal message about why MIMORA exists, available in Settings.

## [1.7.1] - 2026-11-22

### Security hardening (production release)
- **Recovery phrase encrypted at rest**: the 24-word master key is now AES-256-GCM encrypted with a non-exportable Android Keystore key before being written to DataStore. Legacy plaintext phrases are still readable, so existing installs are not broken. The clipboard copy now clears itself after 60 seconds.
- **Screenshot / recents protection**: the app sets `FLAG_SECURE`, so diary content, the recovery key, and backups cannot appear in screenshots, screen recordings, or the recent-apps preview.
- **TLS-only networking**: a network security config disables all cleartext traffic; MIMORA only talks to Google APIs over HTTPS.
- **R8 hardening**: the release build now shrinks, optimizes, and obfuscates code and resources. Application class and method names are stripped from the shipped DEX.
- **Tamper detection**: the release build pins its signing certificate SHA-256 and refuses to run if the APK has been modified and re-signed with a different key.
- **Production signing**: release APKs are signed with a dedicated keystore kept outside the repository. The release certificate SHA-1 was registered as a second Android OAuth client so Google sign-in works with the production APK as well as debug builds.
- **No secrets in the APK**: verified no API keys, tokens, keystore material, or account emails are bundled in the release binary; no logging calls remain in app code.

## [1.7.0] - 2026-11-22

### Added
- **Google Drive backup is now open to everyone**: the OAuth consent screen was published to Production, so any Google account can sign in — no test-user list or 7-day expiry. Public homepage and privacy policy pages were created at `https://iamrony27.github.io/mimora-site/` and `https://iamrony27.github.io/mimora-site/privacy.html`. Both requested scopes are non-sensitive, so no verification or user cap applies.
- **Five writing canvas colors**: Settings → Appearance now offers Lavender, Ocean, Meadow, Sunset, and Rose swatches. The chosen palette tints the whole writing page, both text fields, their placeholders, and the photo actions, and is remembered across restarts. Buttons and accent controls throughout the app also change color with the selected palette.
- **Colorful title & body placeholders**: the title and body fields each have their own themed hint instead of a rotating prompt.
- **Chat-style writing wallpaper**: the text area now has a faint repeating doodle pattern (hearts, stars, smileys, notes), tinted by the selected writing color, like a classic chat background.
- **Floating record control**: recording is now a thumb-reachable button fixed at the bottom-right of the writing screen, with a live timer while recording.

### Changed
- The app is now English-only; the language selection preference and UI were removed.
- The calendar-driven seasonal writing background and the **Prompt** pill were removed from the writing screen. Daily Mood remains as a separate reflection accent.
- Camera and Gallery are now large, always-visible cards at the bottom of the writing card (no scrolling required to find them).

### Fixed
- Attaching a photo or voice note on a brand-new journal no longer crashes with a foreign-key violation; an empty diary row is created for that date before the attachment is saved.
- First-run file restore now passes the selected archive to the restore executor. Previously the key dialog appeared but decryption failed with "No backup archive is loaded", so a reinstall could not be restored from a local `.mimora` file.

## [1.6.0] - 2026-11-22

### Added
- **Fingerprint unlock**: app lock now uses Android BiometricPrompt with `BIOMETRIC_STRONG` and a PIN fallback. Settings shows fingerprint availability and lets you enable it only after a PIN exists.
- **Protected destructive actions**: deleting a photo, voice note, or task now opens a root-level PIN/fingerprint gate. When no PIN exists yet, the dialog creates one inline before the delete is committed.
- **Fullscreen photo preview**: tap any attached photo to open an immersive preview with modern delete and close controls.
- **Direction-aware sync animation**: backup and restore top-bar actions now show distinct sweeping progress rings and breathing halos instead of a generic spinner.

### Changed
- **Modern expressive writing canvas**: Record, Capture, and Gallery are pinned above the writing field so no scrolling is needed to attach media. Seasonal gradients and colorful accents were extended across the writing card and actions.
- Autosave is now the only save path; the redundant **Done & Seal Memory** button was removed.
- All destructive controls now use the same modern delete icon instead of emoji or plain text glyphs.
- App lock now starts locked on cold launch, relocks automatically when MIMORA leaves the foreground, and disabling app lock also clears the fingerprint preference.

### Fixed
- The 24-word restore gate, single timestamped Drive backup, and `appDataFolder` storage remain unchanged.
- Photo thumbnails no longer rely on a bare `✕` delete badge and now expose accessible expand/delete actions.

## [1.5.0] - 2026-11-22

### Added & Enhanced
- **Real "Sign in with Google" for Drive sync**:
  - Replaced the manual "paste an OAuth bearer token" card with Google's current **AuthorizationClient** flow (`play-services-auth`). The token is never stored; each sync requests a fresh one and Google returns it silently.
  - `GoogleDriveAuth` now handles authorization, the consent `PendingIntent`, and resolving the connected account email.
  - Sync now keeps a single archive: the newest backup is overwritten in place with `PATCH` and old duplicates are deleted. Names include the exact sync time (`mimora_backup_yyyy-MM-dd_HH-mm-ss.mimora`), shown in the restore list as local date and time.
  - Settings shows the connected account, plus **Sync now**, **Restore**, and **Disconnect** actions.
  - Animated **backup** and **restore** shortcuts were added to every screen header; the cloud icon spins while syncing and both icons spring on press.
  - The restore dialog and success/error messages are rendered at the navigation root, so top-bar actions work from any screen (not just Settings).
  - Added a Drive restore dialog that lists encrypted backups, lets you pick one, and decrypts it with the 24-word key.
  - First-run onboarding now has a dedicated **Restore from Google Drive** button using the same signed-in flow, plus a separate **Choose backup file instead** fallback.
  - The old Storage Access Framework buttons remain as **Export file** / **Import file** for manual control.
- **Backups now live in `appDataFolder`**:
  - Uploads set `parents: ["appDataFolder"]` and listings use `spaces=appDataFolder`, so archives stay in MIMORA's hidden folder instead of the Drive root.
- **Restore hardening**:
  - Archive paths are reduced to a bare file name before writing, preventing a crafted backup from writing outside the app's media folders.
  - Duplicate media entries in an archive no longer abort the restore.
  - Every restore path requires the full 24-word key; Drive restore buttons stay disabled without it, and first-run onboarding now completes only after decryption actually succeeds. Wrong-key, corrupt-file, and I/O failures show clear messages and leave the database untouched.
- **Upload streaming**: the multipart body is streamed with `setChunkedStreamingMode` instead of being buffered in a second in-memory copy.
- **Unit tests** for Drive listing URL construction (app data folder, sort encoding, field selection).

### Added & Enhanced
- **Seasonal writing paper**: the dominant diary canvas now follows the selected or automatic monthly season with an animated gradient; Daily Mood remains a separate accent for reflections.
- **Write-pad media actions**: Record, Capture, and Gallery now have distinct accent colors, press feedback, and a live pulsing equalizer + timer while recording.
- **Camera capture hardening**: requests CAMERA permission before launching, handles cancellation/no-camera with clear messages, copies the capture directly from private storage, and removes the temporary file.
- Voice-note cards now show a friendly "Voice note" label with accessible play/stop and delete controls instead of exposing raw UUID filenames.

### Fixed
- The writing canvas now follows the app's selected theme instead of the OS theme, so Light mode stays light even when the system is in dark mode.
- Top-bar backup/restore icons and primary key/backup buttons use high-contrast colors instead of very light tints.
- Statistics now labels the current-month pill with the localized month name instead of a hardcoded "Nov".
- Camera capture no longer crashes when CAMERA permission has not been granted.

## [1.4.0] - 2026-11-22

### Added & Enhanced
- **Onboarding & Reinstall Restore Screen (`OnboardingScreen.kt`)**:
  - Implemented dedicated First-Launch and Post-Reinstall experience triggered when `hasCompletedOnboarding` is false.
  - Features two distinct, beautifully presented options:
    1. **Start a Fresh Journal**: Instantly generates an offline vault key and starts the diary writing space.
    2. **Restore Previous Journal (Reinstall Flow)**: Prominently displayed for returning users. Allows one-tap restoration of all diary entries, photos, and voice notes from a Google Drive or local `.mimora` backup file.
  - Integrated `OpenDocument` Storage Access Framework launcher directly with Google Drive provider.
  - Implemented interactive Decrypt & Restore dialog on first run: validates 24-word private key, decrypts archive with AES-256-GCM, imports all memories and tasks, marks onboarding complete, and seamlessly enters the restored diary.
- **Google Drive Backup API (`GoogleDriveBackupApi.kt`)**:
  - Implemented direct Google Drive REST API v3 client with multipart upload (`uploadType=multipart`), file listing (`q=name contains 'mimora_backup' and trashed=false`), and file download (`alt=media`).
  - Added Google Drive API Sync configuration card in Settings allowing users to connect their Google Account and OAuth Bearer token for automated background cloud syncing.
  - Added `uploadToGoogleDriveApi`, `listGoogleDriveBackups`, and `downloadAndRestoreFromGoogleDriveApi` methods in `BackupManager`.
  - Added INTERNET and ACCESS_NETWORK_STATE permissions to `AndroidManifest.xml`.
  - Tested on Android emulator simulating fresh reinstall via `pm clear`.

## [1.3.0] - 2026-11-22

### Added & Enhanced
- **Vault Master Private Key Protection Flow**:
  - Implemented persistent 24-word master recovery phrase derivation and storage in DataStore preferences (`master_recovery_phrase`).
  - Added **Mandatory 10-Second Security Warning Modal** (`KeySecurityDisclaimerDialog`):
    - Explains zero-knowledge AES-256-GCM encryption and why losing the key results in permanent loss of backups.
    - Enforces a strict 10-second reading timer with real-time countdown (`10s... 1s`) and linear progress bar.
    - Prevents proceeding or dismissing until the 10-second reading period elapses.
  - Added **24-Word Master Private Key Dialog** (`MasterPrivateKeyDialog`):
    - 3-column numbered grid displaying words 1 through 24 in clean monospace chips.
    - One-tap "Copy 24-Word Private Key" button with instant Android OS clipboard integration and visual confirmation badge ("✓ Private Key Copied to Clipboard!").
    - Mandatory confirmation checkbox: "I have copied and saved my 24-word private key in a safe offline location."
    - "Done & Sealed" action to lock and secure the key.
    - Key status badge in Settings dynamically updates to `Secured`.
- **Google Drive & Cloud Storage Backup / Restore via SAF**:
  - Added Storage Access Framework (SAF) integration for direct Google Drive cloud export (`CreateDocument`) and cloud restore (`OpenDocument`).
  - Encrypted archive `.mimora` packages the full SQLite database dump and media attachments (photos and audio notes).
  - Added Decrypt & Restore dialog with 24-word key validation, verifying AEAD authentication tags before restoring data.
  - Added Daily Auto-Backup toggle and last synced timestamp display.

## [1.2.0] - 2026-11-22

### Changed & Refined
- **Flat Borderless & Shadowless Minimal Aesthetic**:
  - Eliminated all card borders and drop-shadows across all screens (Home writing canvas, Enrich Memory cards, Calendar card, Entry cards, Task cards, Metric cards, and Settings cards) to establish a truly serene Japanese stationery aesthetic.
  - Removed drop-shadow from the floating bottom navigation bar for a cleaner, unified flat presentation.
- **Segmented Control Button Groups with Switch Animation**:
  - Implemented `StitchSegmentedControl` using continuous pill tracks with animated color background and text transitions (`animateColorAsState`).
  - Upgraded Settings screen: Theme Mode (`System` | `Light` | `Dark`), Seasonal Mode (`Auto (Month)` | `12 Seasons...`), and Language (`English` | `हिन्दी` | `বাংলা`) to full segmented controls.
  - Upgraded Tasks screen: `Pending` | `Completed` to animated segmented control.
  - Upgraded Calendar screen: `All` | `Photos` | `Voice Notes` to animated segmented control.
- **Daily Mood of the Day Canvas Tinting**:
  - Replaced static weather pill on Home screen with an interactive **Daily Mood** selector (`Calm`, `Radiant`, `Reflective`, `Grateful`, `Peaceful`).
  - Selecting a mood dynamically tints the diary writing canvas with smooth spring color animation (`animateColorAsState`).
- **Modern Vector Drawables (Zero Emoji Style)**:
  - Replaced all emoji icons in UI tags, empty states, and dialogs with clean modern SVG vector drawables:
    - `ic_mood_calm.xml` (Calm tranquility wave)
    - `ic_mood_radiant.xml` (Radiant sun)
    - `ic_mood_reflective.xml` (Reflective crescent moon)
    - `ic_mood_grateful.xml` (Blooming heart)
    - `ic_mood_peaceful.xml` (Botanical leaf)
    - `ic_camera.xml` (Modern camera outline)
    - `ic_sparkle.xml` (Clean 4-point star)
  - Fixed duplicate "Today Today" text badge in date navigation row to a single clean "Today" badge.
  - Replaced sprout `🌱` and lotus `🪷` with clean botanical vector drawables.
  - Replaced fire emoji `🔥` and sparkle `✨` in Statistics screen with modern vector icons.

## [1.1.0] - 2026-11-22

### Added
- **Dynamic Seasonal Wallpaper & 12-Month Theme Selector**:
  - Implemented automatic monthly background wallpaper synchronization: the app ambient wallpaper and writing paper seamlessly adapt according to the active calendar month (Jan–Dec).
  - Added 12 curated botanical season papers in `SeasonalMood` with emojis, descriptions, and calibrated light/dark subtle gradient tones:
    1. January: `❄️ Quiet Frost` (Winter calm with slate undertones)
    2. February: `🪻 Lavender Dawn` (Late winter renewal with lilac mist)
    3. March: `🌱 Meadow Mist` (Early spring air with sage)
    4. April: `🌸 Cherry Blossom` (Morning blossom tint)
    5. May: `🌾 Amber Breeze` (Warm golden sunrise)
    6. June: `☀️ Azure Solstice` (Midsummer serene sky)
    7. July: `🌧️ Monsoon Rain` (Cooling rain mist)
    8. August: `🌿 Verdant Rain` (Lush moss hues)
    9. September: `🍂 Harvest Dawn` (Early autumn golden straw)
    10. October: `🍁 Terracotta Dusk` (Crisp autumn dusk ochre)
    11. November: `🍇 Plum Twilight` (Festive pre-winter rich twilight violet)
    12. December: `🌲 Nordic Pine` (Deep winter solstice evergreen silence)
  - Connected `MainNavigation` to observe `userPreferencesFlow`: in `Auto` mode, it synchronizes with the calendar month or the month of the diary entry being browsed/written; supports manual override to any of the 12 seasons.
  - Added **12 Seasonal Papers Picker Dialog** in Settings (`SettingsScreen.kt`) allowing users to browse, preview, and lock any of the 12 seasons or revert to Auto.
  - Updated Home screen seasonal pill to dynamically display the active month's icon and title (e.g. `🍇 Plum Twilight`).
  - Verified on Android Studio emulator with live captures: dialog picker, seasonal override to `🌱 Meadow Mist`, and reversion to `Auto (Month)`.
- **Stitch Design System Integration ("Serene Sanctuary")**:
  - Extracted color palette, typography tokens, and screen mockups from Stitch Project `14367199922103265142`.
  - Defined brand palette: `StitchPrimary` (`#5D34BE`), `StitchPrimaryContainer` (`#7650D8`), `StitchSecondary` (`#944171`), `StitchSecondaryContainer` (`#FD99CF`), `StitchSurface` (`#FFF7FF`), `StitchDarkBackground` (`#141118`), `StitchDarkSurface` (`#1E1A24`).
  - Added reusable Stitch design tokens and components: `StitchPill`, `WaveformVisualizer`, `SealMemoryButton`, and pulsing `SaveStateIndicator`.
  - Rebuilt **Diary Home Screen** with cloth-bound 24dp elevated journal card, ambient mood pills (Autumn Whispers, Misty 14°C, New Prompt), word count, End-to-End Encrypted badge, Enrich Memory action cards, and Done & Seal Memory button.
  - Rebuilt **Floating Bottom Navigation** with balanced 5-tab floating curved container (Diary, Calendar, Tasks, Stats, Settings) without center `+` button.
  - Rebuilt **Calendar Screen** with filter pills ("All Entries", "Photos", "Voice Notes"), 24dp calendar card with multi-colored indicator dots, legend capsule, and preview cards.
  - Rebuilt **Tasks & Intentions Screen** with segmented pill switcher, "Gentle Pace Today" mindfulness header, and categorized task cards.
  - Rebuilt **Statistics Screen** with hero streak gradient card, 2x2 metric cards with percentage badges, Monthly Activity bar chart, and 5-week reflection consistency heatmap.
  - Rebuilt **Settings Screen** with Personal Journal avatar header, pill chip selectors for theme and language, PIN app lock, and 24-word recovery phrase generator.
  - Tested on Android Studio emulator (`opdownloader_x86_64` / API 35) in both Light and Dark themes with 7 screenshot captures saved in `docs/`.

## [1.0.0] - 2026-11-22

### Added
- **Project Initializer**: Full native Kotlin, Jetpack Compose, Material 3 Android foundation configured with AGP 9.0.1 and Gradle 9.1.0.
- **Branding & Launcher Icon**: Single solid background `#6750A4` adaptive launcher icon with memory journal + cloud backup symbol strictly positioned in the 72dp safe zone; monochrome icon for Material You launchers.
- **Design System**: 16 Material 3-inspired Vector Drawables (`ic_diary`, `ic_calendar`, `ic_task`, `ic_stats`, `ic_settings`, `ic_voice_note`, `ic_photo`, `ic_cloud_backup`, etc.) and design tokens.
- **Seasonal Backgrounds**: 12 subtle, contrast-preserving monthly background gradients (Jan–Dec) with automatic selection and manual override.
- **Diary-First Home Screen**: Multiline text editor, debounced autosave, visual save state indicator (`Saving...`, `Saved`, `Error`), and backward date navigation.
- **Strict Future-Date Guard**: Hard domain and UI restriction preventing any writing or navigation to future dates.
- **Voice Recording & Playback**: Real `MediaRecorder` AAC recording with real-time duration counter, banner feedback, and `MediaPlayer` audio playback.
- **Photo Attachments**: Support for device photo picker and camera capture with local sandbox storage.
- **Calendar & "On This Day"**: Year/Month calendar with entry indicators for text, photo, and audio, plus automated surfacing of memories from previous years.
- **Tasks & Reminders**: Separate personal task manager with completed/pending toggles, due dates, and `WorkManager` notifications.
- **Offline SQLite Database**: Structured relational schema with foreign key cascades, indices, and real-time Flow notifications.
- **Security & Keystore**: Hardware-bound Android Keystore master key, PIN lock with PBKDF2 (100,000 iterations), 24-word recovery phrase generator and validator.
- **Encrypted Backup & Restore**: Authenticated AES-256-GCM encryption with versioned `.mimora` archives, GCM tag verification, and documented Google Drive cloud integration.
- **Localization**: Full translation into English, Hindi (`values-hi`), and Bengali (`values-bn`).
- **Automated Tests**: 10 unit tests covering future-date rules, model completeness, PIN hashing, recovery phrase derivation, AES-256-GCM encryption, decryption, wrong-phrase rejection, and tampering detection.
- **Emulator Verification**: Tested end-to-end on Android Studio emulator (`opdownloader_x86_64` / API 35) covering Diary writing, debounced autosave, calendar grid navigation, tasks completion, real-time statistics calculation, 24-word recovery phrase dialog, and dark theme switching with 8 empirical screenshot captures.
