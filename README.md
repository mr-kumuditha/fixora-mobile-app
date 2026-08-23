# Fixora

A modern Android mobile application for device repair booking and repair tracking.

## Overview

This GitHub version contains the Fixora customer application. Customers can create an account, browse repair services, submit a photo-assisted repair booking, receive a branch recommendation, track repair progress, review repair history, manage their profile, and complete the demonstration payment flow.

The original integrated local project is preserved separately. Technician and administration source files are intentionally not included in these GitHub branches.

## Customer Features

- Email/password registration and login
- Google sign-in
- Device category, brand, model, and service selection
- Multi-step repair booking with issue details and preferred date/time
- Camera and gallery photo attachment with client-side compression
- GPS-assisted branch recommendation using distance and spare-part availability
- Real-time repair tracking and status timeline
- Active and completed repair history
- Profile and profile-photo management
- Light and dark theme preference
- Simulated card or cash-on-pickup payment
- Room-backed service catalogue cache and resumable repair draft

## Technology Stack

- Kotlin and Android SDK 35
- Jetpack Compose, Material 3, and Navigation Compose
- MVVM with presentation, domain, and data layers
- Firebase Authentication and Cloud Firestore
- Supabase Postgres for spare-part availability
- Supabase Storage for repair images
- Room for local catalogue caching and repair drafts
- CameraX, Android Photo Picker, Google Maps, and location services
- Kotlin Coroutines, Kotlin Serialization, Ktor, and Coil

## Project Structure

```text
.
├── app/
│   ├── src/main/java/com/techfix/app/
│   │   ├── core/          # Data implementations, navigation, and design system
│   │   ├── domain/        # Customer models, contracts, and branch matching
│   │   └── ui/            # Authentication and customer Compose screens
│   ├── src/main/res/      # Android resources
│   ├── src/test/          # JVM unit tests
│   └── src/androidTest/   # Customer Android/Firebase integration tests
├── docs/supabase/         # Customer-side Supabase setup SQL
├── firestore.rules        # Customer-only Firestore authorization rules
├── firestore.indexes.json # Required Firestore indexes
└── releases/              # Reviewed demonstration APK
```

## Architecture

Fixora uses layered MVVM. Compose screens and ViewModels form the presentation layer, backend-independent models and repository contracts form the domain layer, and Firebase, Supabase, Room, location, and storage implementations form the data layer. `MatchBranchesUseCase` ranks branches using distance and compatible spare-part availability.

## Build Instructions

### Prerequisites

- Android Studio with Android SDK 35
- JDK 17
- Firebase Android client configuration at `app/google-services.json`
- An uncommitted `local.properties` containing the SDK path and required public client configuration:

```properties
sdk.dir=/absolute/path/to/Android/sdk
SUPABASE_URL=your_public_supabase_url
SUPABASE_ANON_KEY=your_publishable_or_anon_key
MAPS_API_KEY=your_restricted_android_maps_key
```

Never use a Supabase `service_role` key, Firebase Admin credential, password, or private signing key in the Android project.

### Android Studio

Open the repository root in Android Studio, allow Gradle sync to complete, select the `app` configuration, and run it on an emulator or Android device.

### Command Line

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## APK

The reviewed debug demonstration build is stored at:

```text
releases/Fixora-v1.0-debug.apk
```

It is a debug coursework artifact, not a production-signed Play Store release.

## Security

Private credentials, service-role keys, Firebase Admin credentials, environment files, `local.properties`, and private signing files are not included. `app/google-services.json` is the Firebase Android client configuration, not a Firebase Admin service account. Public Android client keys must still be restricted in their provider consoles.

## Screenshots

<!-- Add verified customer-flow screenshots here. -->
