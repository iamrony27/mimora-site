# Architecture Decision Records (ADRs)

## ADR 001: Foundation & Presentation Framework
- **Decision**: Native Kotlin, Gradle 9.1, AGP 9.0.1, Jetpack Compose, Material 3.
- **Rationale**: Declarative UI, reactive state binding, built-in accessibility, and seamless dynamic theming support.

## ADR 002: Minimum SDK Selection (API 26)
- **Decision**: Set `minSdk = 26` (Android 8.0 Oreo), `targetSdk = 37`.
- **Rationale**: `java.time` (`LocalDate`, `LocalTime`, `YearMonth`) natively exists from API 26 onwards without requiring bytecode desugaring libraries or performance penalties, covering >96% of global active Android devices.

## ADR 003: Database Engine (SQLiteOpenHelper Architecture)
- **Decision**: Native Android SQLite engine with custom DAO/Repository abstraction rather than annotation-processor based Room.
- **Rationale**: AGP 9.0.1 and Kotlin 2.3.20 enforce built-in Kotlin support and disable legacy kapt. Android's native SQLite provides 100% offline-first reliability, full ACID compliance, zero code-generation dependencies, and total migration control.

## ADR 004: Audio and Image Storage Sandbox
- **Decision**: Store all media files in internal sandbox directories (`context.filesDir/photos/` and `context.filesDir/voice_notes/`).
- **Rationale**: Zero external storage permissions needed; attachments cannot be tampered with or deleted by external gallery cleaners or other apps.

## ADR 005: 24-Word Recovery Phrase and AES-256-GCM Encryption
- **Decision**: Derive 256-bit AES encryption keys from 24-word phrases using `PBKDF2WithHmacSHA256` with 100,000 iterations and a unique 16-byte random salt.
- **Rationale**: Industry-standard, memory-hard key derivation and authenticated GCM encryption protecting user backup files against tampering and brute-force attacks.

## ADR 006: Separation of Static UI Assets from Backup
- **Decision**: Strictly exclude seasonal backgrounds, launcher icons, and app drawables from backup archives.
- **Rationale**: Backups contain only user-created content (text, user voice notes, user photos, tasks). App-provided decorative graphics remain bundled locally to save cloud bandwidth and storage.
