# AutoFlow

**Automate repetitive tasks on Android with customizable, on-device workflows.**

AutoFlow is a native Android application built with Kotlin and Jetpack Compose. It provides a framework for creating automation workflows that connect triggers, conditions, and actions, helping users automate supported tasks directly on their devices.

The project focuses on local processing, configurable automation, reliable background execution, and user-controlled Android permissions.

## Features

- **Custom automation workflows** — organize automation logic around triggers, conditions, and actions.
- **Background execution** — use Android WorkManager to schedule and execute supported automation tasks.
- **Persistent local storage** — store automation configurations and related data using Room.
- **Event-based triggers** — support device and system events, including boot and Bluetooth connection events.
- **File automation** — work with user-selected files through Android's Storage Access Framework (SAF), including file conditions and supported file-processing actions.
- **UI automation** — use Android Accessibility Services for user-configured interface automation when the user explicitly enables the service.
- **Notification-based functionality** — integrate with Android's notification listener capabilities when the user grants access.
- **Instagram data analysis** — parse and analyze supported locally available Instagram-related data without requiring app credentials or a remote service.
- **Automation history and notifications** — provide execution tracking and notifications for supported workflows.
- **Data export and sharing** — support workflow backup, file export, and sharing through Android's file-sharing mechanisms.

> Feature availability depends on the workflow type, Android version, required permissions, and current implementation.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | ViewModels, repositories, separation of concerns |
| Local database | Room |
| Background tasks | Android WorkManager |
| Navigation | Navigation Compose |
| Asynchronous operations | Kotlin Coroutines |
| Dependency management | Gradle Kotlin DSL, version catalog |
| Database code generation | KSP |
| Testing | JUnit, AndroidX Test, Espresso, Compose UI Testing |

## Architecture

AutoFlow separates the user interface from automation execution and persistence.

```text
Jetpack Compose UI
        |
        v
    ViewModels
        |
        v
Repositories and Room Database
        |
        v
  Automation Engine
        |
        +-- Triggers
        +-- Conditions
        +-- Actions
        |
        v
WorkManager and Android System APIs
```

- **UI layer:** screens and interactions built with Jetpack Compose.
- **ViewModel layer:** manages screen state and user interactions.
- **Data layer:** repositories and Room provide persistent local storage.
- **Automation engine:** coordinates workflow execution and evaluates supported conditions.
- **Trigger and action integrations:** connect workflows to supported Android events, file operations, notifications, and UI automation.
- **Background execution:** WorkManager schedules supported work while respecting Android lifecycle and system restrictions.

## Privacy and Permissions

AutoFlow is designed around user-controlled, on-device automation.

- Automation data is stored locally using Room.
- File access uses the Storage Access Framework where applicable, avoiding broad all-files access for those workflows.
- Accessibility access must be enabled by the user in Android settings before accessibility-based automation can run.
- Notification access is granted by the user through Android settings.
- Bluetooth, notification, and other runtime permissions are requested when the relevant functionality requires them.
- Automatic Android cloud backup is disabled; workflow backup is an explicit user action.
- Instagram-related parsing and analysis are designed for local processing without app credentials.

Some automation capabilities require sensitive Android permissions. Users should review each permission and enable only the features they intend to use.

## Requirements

- Android Studio with a compatible Android SDK.
- JDK compatible with the project's Gradle and Android Gradle Plugin versions.
- Android SDK Platform 37 as configured in the project.
- An Android device or emulator running Android 8.0 (API 26) or later.

The project currently declares `minSdk = 26` and `targetSdk = 37`. Consult the project configuration and Android Studio's SDK recommendations when setting up the build environment.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/Dhruwvaviya999/autoflow.git
cd autoflow
```

### 2. Open the project

Open the cloned directory in Android Studio and allow Gradle to sync.

Install any required Android SDK components if prompted.

### 3. Build the application

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
```

### 4. Run the app

Connect an Android device with USB debugging enabled or start an emulator, then run the `app` configuration from Android Studio.

Alternatively, install the debug APK generated under:

```text
app/build/outputs/apk/debug/
```

### 5. Grant permissions as needed

Depending on the workflows you configure, you may need to enable notification access, accessibility access, or specific runtime permissions in Android settings.

AutoFlow should not be assumed to have permission to perform every automation immediately after installation.

## Testing

Run local JVM unit tests:

```bash
./gradlew test
```

Run Android instrumentation tests on a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

These commands run the tests configured in the Gradle project. Instrumentation tests require a compatible Android device or emulator.

## Project Status

AutoFlow has progressed beyond a basic automation prototype, with local persistence, background execution, file-related workflows, and additional Android integrations.

The project has previously completed testing of its core automation flows, file processing, and local data-analysis functionality. Individual features may continue to evolve as the application develops.

## Contributing

Contributions, bug reports, and suggestions are welcome.

1. Fork the repository.
2. Create a branch for your changes.
3. Implement and test your changes.
4. Submit a pull request with a clear description of the improvement.

Please avoid committing secrets, personal data, signing keys, or local configuration files.

## License

Check the repository for a license file before using, modifying, or redistributing AutoFlow. If no license has been added, all rights remain with the copyright holder by default.

---

Built with Kotlin and Jetpack Compose.
