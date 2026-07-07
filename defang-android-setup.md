# Defang — Android Project Setup
*Companion to [defang-launcher-prd.md](defang-launcher-prd.md)*

---

## Table of contents

| # | Section |
|---|---|
| 1 | [Language and tooling](#1-language-and-tooling) |
| 2 | [Project structure](#2-project-structure) |
| 3 | [Folder layout](#3-folder-layout) |
| 4 | [AndroidManifest — permissions and services](#4-androidmanifest--permissions-and-services) |
| 5 | [Key dependencies](#5-key-dependencies) |
| 6 | [The two critical services](#6-the-two-critical-services) |
| 7 | [i18n file layout](#7-i18n-file-layout) |
| 8 | [Build configuration](#8-build-configuration) |

---

## 1. Language and tooling

| Choice | Rationale |
|---|---|
| **Kotlin** | Google's first-class Android language since 2019. All Jetpack libraries are Kotlin-first. Jetpack Compose (modern UI) is Kotlin-only. New project in Java in 2026 is building against the grain. |
| **Android Studio** | Official IDE, built on IntelliJ. Best Compose preview, Layout Inspector, and Profiler. No serious alternative for Android-native development. |
| **Jetpack Compose** | Modern declarative UI toolkit, replaces XML layouts. Better for this app specifically because overlay screens (intent gate, HUD, end-card) are drawn programmatically anyway — Compose fits that model naturally. |
| **MVVM + Clean Architecture** | Standard pattern for Android. Keeps the business logic (session rules, extension limits, DnD reading) testable and separate from the UI and Android services. |

Minimum SDK: **API 26 (Android 8.0)**. Required for `AutomaticZenRule` DnD reading and `NotificationListenerService` reliability. Target SDK: latest stable (API 35 as of writing).

---

## 2. Project structure

Single-module app to start. Do not split into multi-module until the project is large enough that build times justify it — premature modularisation is a common time sink on solo projects.

```
defang/
├── app/
│   ├── src/
│   │   ├── main/
│   │   ├── test/          # Unit tests
│   │   └── androidTest/   # Instrumented tests
├── gradle/
│   └── libs.versions.toml  # Version catalog — all dependency versions in one place
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 3. Folder layout

All Kotlin source lives under `app/src/main/kotlin/com/defang/launcher/`. Package structure follows Clean Architecture layers.

```
com/defang/launcher/
│
├── DefangApplication.kt          # Application class, Hilt entry point
│
├── di/                           # Dependency injection (Hilt modules)
│   ├── AppModule.kt              # Database, DataStore, repositories
│   └── ServiceModule.kt         # Overlay manager, DnD reader
│
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── DefangDatabase.kt          # Room database
│   │   │   ├── dao/
│   │   │   │   ├── SessionDao.kt          # Read/write session records
│   │   │   │   └── AppConfigDao.kt        # Per-app tier + limit settings
│   │   │   └── entity/
│   │   │       ├── SessionEntity.kt       # id, packageName, startTime, endTime, intentDeclared
│   │   │       └── AppConfigEntity.kt     # packageName, tier, sessionLimitMins, cooldownMins, etc.
│   │   └── datastore/
│   │       └── PreferencesDataStore.kt    # Global prefs: onboarding done, daily extension used, etc.
│   └── repository/
│       ├── SessionRepository.kt
│       └── AppConfigRepository.kt
│
├── domain/
│   ├── model/
│   │   ├── AppTier.kt            # Enum: UTILITY, WATCHED, BROWSER_SOCIAL, BROWSER_ADULT, BROWSER_UTILITY
│   │   ├── ContentTrack.kt       # Enum: GENERAL, SOCIAL, ADULT
│   │   ├── AppConfig.kt          # Domain model (not Room entity)
│   │   └── Session.kt
│   └── usecase/
│       ├── GetWatchedAppsUseCase.kt
│       ├── RecordSessionUseCase.kt
│       ├── GetDailyExtensionStatusUseCase.kt   # Has the user used their one extension today?
│       ├── SelectContentTrackUseCase.kt        # GENERAL / SOCIAL / ADULT based on package/domain
│       └── IsDnDActiveUseCase.kt               # Reads AutomaticZenRule, returns true/false
│
├── service/
│   ├── accessibility/
│   │   └── DefangAccessibilityService.kt   # Detects foreground app, triggers intent gate,
│   │                                        # applies grayscale overlay
│   ├── notification/
│   │   └── DefangNotificationListenerService.kt   # Intercepts + batches notifications
│   └── overlay/
│       ├── OverlayManager.kt               # Manages SYSTEM_ALERT_WINDOW views
│       ├── IntentGateOverlay.kt            # Full-screen interstitial + countdown + tidbit
│       ├── SessionTimerOverlay.kt          # HUD during session
│       └── EndCardOverlay.kt               # Session summary + offline prompt + extension flow
│
├── ui/
│   ├── launcher/
│   │   ├── LauncherActivity.kt             # The actual home screen
│   │   ├── LauncherViewModel.kt
│   │   └── LauncherScreen.kt              # Compose
│   ├── onboarding/
│   │   ├── OnboardingActivity.kt
│   │   ├── OnboardingViewModel.kt
│   │   └── screens/
│   │       ├── WhatItIsScreen.kt
│   │       ├── WhatItIsNotScreen.kt
│   │       ├── TheLoopScreen.kt
│   │       ├── WhySmallTasksScreen.kt
│   │       └── SetupScreen.kt
│   ├── settings/
│   │   ├── SettingsActivity.kt
│   │   ├── SettingsViewModel.kt
│   │   ├── SettingsScreen.kt
│   │   └── apptier/
│   │       ├── AppTierScreen.kt            # "Watched / Utility / Browser" assignment UI
│   │       └── AppTierViewModel.kt
│   ├── awareness/
│   │   └── AwarenessLibraryScreen.kt       # "What we know about them" — full tidbit list
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt                        # MaterialTheme wrapper, dark/light, follows system
│       └── Type.kt
│
└── util/
    ├── DnDScheduleReader.kt                # Wraps NotificationManager / AutomaticZenRule
    ├── UsageStatsHelper.kt                 # Wraps UsageStatsManager for session tracking
    ├── ContentTrackSelector.kt             # Maps packageName / domain → ContentTrack enum
    └── TidbitSelector.kt                  # Pseudo-random rotation, no same-day repeat logic
```

---

## 4. AndroidManifest — permissions and services

```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />

<!-- Accessibility Service -->
<service
    android:name=".service.accessibility.DefangAccessibilityService"
    android:exported="true"
    android:label="@string/accessibility_service_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>

<!-- Notification Listener Service -->
<service
    android:name=".service.notification.DefangNotificationListenerService"
    android:exported="true"
    android:label="@string/notification_listener_label"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>

<!-- Launcher activity — must declare MAIN + HOME to appear as a launcher -->
<activity
    android:name=".ui.launcher.LauncherActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

`res/xml/accessibility_service_config.xml` — must be specific about what the service does. Vague descriptions cause Play Store rejection and user distrust:

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds"
    android:canRetrieveWindowContent="false"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100"
    android:packageNames="" />
```

`android:packageNames=""` means it monitors all apps (needed to detect foreground app changes). This will be flagged during Play review — have the accessibility service description string (`accessibility_service_description` in strings.xml) written clearly and specifically.

---

## 5. Key dependencies

Managed via `gradle/libs.versions.toml` (version catalog). All versions in one file; no version numbers scattered across build files.

```toml
[versions]
kotlin = "2.0.21"
agp = "8.7.0"
compose-bom = "2024.12.01"
hilt = "2.52"
room = "2.6.1"
datastore = "1.1.1"
coroutines = "1.9.0"
lifecycle = "2.8.7"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-activity = { group = "androidx.activity", name = "activity-compose", version = "1.9.3" }

# Hilt (dependency injection)
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }

# Room (local database — session history, app config)
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# DataStore (lightweight key-value prefs — onboarding state, daily extension flag)
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Coroutines + Flow
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Lifecycle / ViewModel
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.21-1.0.28" }
```

Room and Hilt both require annotation processing. Use **KSP** (Kotlin Symbol Processing), not KAPT — KSP is faster and is the current standard. KAPT is being deprecated.

---

## 6. The two critical services

Everything else in the app is standard Android. These two services are the architectural core and the biggest source of fragility.

### DefangAccessibilityService

Responsibilities: detect when a watched app comes to the foreground, trigger the intent gate overlay, apply the grayscale overlay during sessions, detect when the user leaves a watched app to end the session.

Key implementation notes:
- `onAccessibilityEvent(event)` fires on `TYPE_WINDOW_STATE_CHANGED`. Read `event.packageName` to detect foreground app changes.
- The service must be running at all times. Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and walk the user through granting it during onboarding. On Samsung / Xiaomi / OnePlus, additionally link to the OEM-specific battery settings page (there are libraries for this, e.g. `AutoStarter`).
- The grayscale overlay is a `WindowManager`-attached `View` with a `Paint` using `ColorMatrix` set to a grayscale matrix, drawn over the watched app. On Android 14+, prefer `Window.setColorMode()` if accessible without root.
- The overlay for the intent gate is also managed here (or delegated to `OverlayManager`), triggered immediately when a watched app is detected as foreground before the app has a chance to render.

### DefangNotificationListenerService

Responsibilities: intercept notifications from watched apps, suppress them, re-post as batched summary at the user's configured delivery windows.

Key implementation notes:
- `onNotificationPosted(sbn)` fires for every notification. Check `sbn.packageName` against the watched-app list.
- Cancel the original with `cancelNotification(sbn.key)` and store in Room (or in-memory queue if Room is overkill).
- Use `AlarmManager` with `setExactAndAllowWhileIdle` to trigger re-posting at the delivery window. `WorkManager` is not precise enough for scheduled notification delivery.
- Address-book bypass: check `sbn.notification.extras` for `EXTRA_PEOPLE_LIST` or sender data and cross-reference with `ContactsContract`. Only suppress if sender is not in contacts.
- The service must hold a notification with `startForeground()` to survive on Android 14+ under background restrictions.

---

## 7. i18n file layout

All user-facing strings in `res/values/strings.xml`. Awareness content in a separate file to keep the main strings file navigable.

```
res/
├── values/
│   ├── strings.xml               # UI strings: labels, onboarding copy, settings, overlays
│   └── strings_awareness.xml     # Tidbit library + offline prompts (separate for translator clarity)
├── values-nb/                    # Norwegian (smoke-test locale)
│   ├── strings.xml
│   └── strings_awareness.xml
└── values-de/                    # Add locales as translations become available
    └── strings.xml
```

Naming convention for awareness strings:

```xml
<!-- strings_awareness.xml -->
<string name="tidbit_social_pulltorefresh">Pull-to-refresh was deliberately modelled on a slot machine lever…</string>
<string name="tidbit_social_infinite_scroll">Infinite scroll was invented by Aza Raskin in 2006…</string>
<string name="tidbit_adult_coolidge">The Coolidge effect is a real neurological phenomenon…</string>
<string name="tidbit_general_dopamine">Dopamine is not a pleasure chemical…</string>
<string name="prompt_offline_tidy_table">Tidy the coffee table.</string>
<string name="prompt_offline_walk">Take a walk around the block.</string>
```

`TidbitSelector.kt` loads the string array by content track at runtime using resource IDs. This means adding a new locale requires only a translation file — no code changes.

---

## 8. Build configuration

`app/build.gradle.kts` (abbreviated):

```kotlin
android {
    namespace = "com.defang.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.defang.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }
}
```

Enable R8/ProGuard for release builds (`isMinifyEnabled = true`). This matters for a sideloaded APK — smaller file, harder to reverse-engineer the content library strings.

For GitHub distribution: add a GitHub Actions workflow (`.github/workflows/build.yml`) that builds a signed release APK on each push to `main` and attaches it to a GitHub Release. Users download the APK directly from the Releases page. No Play Store account required to ship.

---

## 9. Google developer verification lockdown — September 2026

**This is a launch blocker, not a future concern. The lockdown takes effect in approximately 85 days from the date of this document.**

Starting September 2026, Google will silently push an update to all certified Android devices that blocks installation of apps from developers who have not registered with Google, paid a fee, agreed to their Terms of Service, surrendered government-issued ID, and provided signing key evidence. This applies to all apps — not just Play Store apps. Sideloaded APKs from GitHub are directly affected.

Defang will not comply with this registration requirement. The requirement is antithetical to the purpose of the app and asks the developer to hand personal identification to the same class of corporation the app is designed to push back against.

**What happens to users after September 2026:**

Users who already have Defang installed are unaffected — the block applies to installation, not execution. Users who want to install it after the lockdown will need to complete Google's "escape hatch" flow: nine steps including enabling Developer Mode (seven taps on the build number), a mandatory 24-hour cooling-off period, and multiple dismissal screens. This flow runs through Google Play Services, not the Android OS, meaning Google can tighten or remove it at any time without an OS update.

F-Droid, which is one of Defang's target distribution channels, has called this requirement an existential threat. The situation is ongoing.

**FreeDroidWarn library**

Add [`FreeDroidWarn`](https://github.com/woheller69/FreeDroidWarn) to the project. This library, maintained by the open Android community and promoted by [keepandroidopen.org](https://keepandroidopen.org), displays a warning to users explaining the upcoming lockdown and what they will need to do to keep installing apps from unregistered developers. It gives users enough lead time to understand what's coming before it arrives.

Add to `libs.versions.toml`:
```toml
# FreeDroidWarn — warns users about Google's September 2026 developer verification lockdown
freedroidwarn = { group = "org.woheller69", name = "freedroidwarn", version = "latest.release" }
```

Integrate it in `DefangApplication.kt` or in the `SettingsScreen` under a dedicated "About / Distribution" section. Do not hide it — the user deserves to know this information clearly and early, ideally on first launch after onboarding.

The warning should be honest and non-alarmist: explain what Google is doing, what it means for Defang specifically, what the user will need to do after September 2026 to continue installing updates, and where to find more information. Text is in `strings.xml` and therefore translatable.

**Alternative distribution: F-Droid**

F-Droid does not require Google developer registration and is one of the clearest paths to continued sideload-friendly distribution after the lockdown. Submit Defang to F-Droid as early as possible — the review process takes time and it is better to be listed before September than scrambling after. F-Droid has its own signing process which is separate from Google's registration requirement.

**Recommended distribution priority:**
1. GitHub Releases (APK) — now, immediately
2. F-Droid — submit as soon as a stable release exists
3. Google Play — only if Google reverses or softens the registration requirement; not worth the compliance cost otherwise
