# MIMORA System Architecture

## Overview
MIMORA is designed as an offline-first, privacy-first personal journal with a strict unidirectional data flow (UDF) MVVM architecture.

```
       ┌────────────────────────────────────────────────────────┐
       │                   Presentation Layer                   │
       │  Jetpack Compose + Material 3 + Unidirectional Flow    │
       │  (Home, Calendar, Tasks, Statistics, Settings)        │
       └───────────────────────────▲────────────────────────────┘
                                   │ StateFlow / Events
       ┌───────────────────────────┴────────────────────────────┐
       │                    ViewModel Layer                     │
       │  (HomeViewModel, CalendarViewModel, TasksViewModel,    │
       │   StatisticsViewModel, SettingsViewModel)              │
       └───────────────────────────▲────────────────────────────┘
                                   │
       ┌───────────────────────────┴────────────────────────────┐
       │                    Repository Layer                    │
       │  (DiaryRepository, MediaRepository, TaskRepository,   │
       │   StatisticsRepository, UserPreferencesRepository)     │
       └───────────────────────────▲────────────────────────────┘
                                   │
       ┌───────────────────────────┴────────────────────────────┐
       │               Data & Persistence Layer                 │
       │  • SQLite Database (MimoraDatabaseHelper)              │
       │  • DataStore Preferences                               │
       │  • Android Keystore & SecurityManager                  │
       │  • WorkManager (Task Notifications)                    │
       │  • Internal File Storage (photos/, voice_notes/)       │
       └────────────────────────────────────────────────────────┘
```

## Layers and Responsibilities

### 1. Presentation Layer (`com.mimora.app.feature.*`)
- Built exclusively with Jetpack Compose using Material 3 specifications.
- Consumes immutable `UiState` via `collectAsStateWithLifecycle()`.
- Emits user actions upward to ViewModels.
- Follows minimum 48dp touch targets (`MimoraTouchTargets.minTarget`) and accessibility semantics.

### 2. Domain & Business Rules (`com.mimora.app.core.model.*`)
- `DateRules.isDateAllowedForWriting(targetDate)`: Strictly enforces that entries can only be authored for current or past dates.
- Computing diary entry completeness (`hasText`, `hasPhoto`, `hasVoice`).
- Streak algorithms: Continuous date iteration calculating current and historical longest writing streaks.

### 3. Repository Layer (`com.mimora.app.data.repository.*`)
- Single source of truth for business data.
- Bridges relational database operations with reactive Kotlin `Flow`.
- Handles file I/O for photos and audio recordings within app sandboxed directories.

### 4. Database Layer (`com.mimora.app.database.*`)
- Native SQLite via `SQLiteOpenHelper` with foreign keys enabled (`PRAGMA foreign_keys = ON`).
- Automatic cascade deletion: deleting an entry removes linked media references.
- Indexed lookup columns (`entry_date`, `due_date`) ensuring $O(\log n)$ performance.

### 5. Security & Cryptography Layer (`com.mimora.app.feature.security.*`)
- Hardware-backed Android Keystore for local device encryption.
- 24-word BIP-39 mnemonic recovery phrase generation and validation.
- Key derivation via `PBKDF2WithHmacSHA256` (100,000 iterations, 16-byte random salt).
- Authenticated AES-256-GCM encryption with 12-byte unique IV and 128-bit authentication tag.

### 6. Dependency Injection (`com.mimora.app.core.di.*`)
- Managed via `AppContainer` pattern rooted in `MimoraApplication`.
- Provides testable, modular, and leak-free singletons for repositories, database, and preferences.
