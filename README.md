<p align="center">
  <strong>Octomil Android App</strong><br>
  Companion app for managing on-device AI models.
</p>

<p align="center">
  <a href="https://github.com/octomil/octomil-app-android/actions/workflows/ci.yml"><img src="https://github.com/octomil/octomil-app-android/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/octomil/octomil-app-android/blob/main/LICENSE"><img src="https://img.shields.io/github/license/octomil/octomil-app-android" alt="License"></a>
</p>

## Overview

The Octomil Android app is a companion app that pairs with the [Octomil Android SDK](https://github.com/octomil/octomil-android) to provide a management interface for on-device AI models.

### Features

- **Model Management** — Browse, download, and manage on-device models
- **Device Pairing** — QR/deep-link pairing with the Octomil platform
- **Settings** — Configure SDK behavior, telemetry, and privacy preferences

## Requirements

- Android 8.0+ (API 26)
- Android Studio Hedgehog+
- JDK 17

## Getting Started

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/octomil/octomil-app-android.git
cd octomil-app-android

# Build
./gradlew assembleDebug

# Run tests
./gradlew test
```

## Architecture

The app uses the [Octomil Android SDK](https://github.com/octomil/octomil-android) as a git submodule (composite build).

```
app/src/main/kotlin/ai/octomil/app/
├── MainActivity.kt           # Entry point
├── OctomilApplication.kt     # Application class
├── screens/                   # Compose screens (Home, Pair, ModelDetail, Settings)
├── services/                  # Local pairing server
└── viewmodels/                # ViewModels (Inference, Pair)
```

## License

See [LICENSE](LICENSE) for details.
