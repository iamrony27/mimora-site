# ADR 001: MIMORA Architecture and Technical Foundation

## Status
Accepted

## Context
MIMORA is a private personal diary and memory journal Android application designed with an offline-first architecture. The application supports rich diary entries (text, photos, audio notes), calendar browsing, task/reminder management, activity statistics, and optional secure cloud backup/restore.

## Architectural Decisions

### 1. UI Layer: Jetpack Compose & Material 3
- **Choice**: Modern declarative UI with Jetpack Compose, Material 3 theming, and dynamic color support (Android 12+), backed by branded light/dark fallback palettes.
- **Rationale**: Compose provides type-safe, reactive UI composition with built-in accessibility semantics, smooth animations, and decoupled UI state rendering.

### 2. State & Flow: Kotlin Coroutines & StateFlow
- **Choice**: Unidirectional Data Flow (UDF) utilizing Kotlin `StateFlow` and `SharedFlow` within Android `ViewModel` components.
- **Rationale**: Guarantees predictable UI updates, lifecycle awareness via `collectAsStateWithLifecycle()`, and testable view models.

### 3. Local Data Persistence: Room & DataStore (Planned for Phase 8)
- **Choice**: Room Database with SQLite for relational entity persistence (entries, tags, tasks, media references), and Jetpack DataStore for user preferences.
- **Rationale**: Type-safe queries, migration support, and full offline-first durability.

### 4. Security & Cryptography: Android Keystore (Planned for Phase 9)
- **Choice**: Android Keystore backed symmetric/asymmetric encryption primitives. No home-grown crypto.
- **Rationale**: Hardware-backed security key protection with biometric authentication integration.

### 5. Media Handling (Planned for Phase 5)
- **Choice**: Android Photo Picker (zero broad storage permission footprint) and localized sandbox app storage for voice recordings and thumbnails.
- **Rationale**: Minimal permission surface area and strict user privacy.

### 6. Cloud Backup (Planned for Phase 10)
- **Choice**: Opt-in, encrypted Google Drive app data storage with explicit user consent, conflict resolution, and restore verification.
- **Rationale**: Preserves user privacy and prevents silent data exfiltration.

## Consequences
- Fast, resilient offline experience.
- Strict adherence to Material 3 design tokens and accessibility guidelines.
- Clear separation between UI, domain, and data layers.
