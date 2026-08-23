# Fixora

A modern Android mobile application for device repair booking, repair tracking, technician workflow management, and administrative service management.

## Overview

Fixora is one connected mobile platform for customers and repair-service staff. Firebase Authentication and Firestore user records provide role-based access, routing customers to the booking experience and authorized staff to a shared operational workspace. The application supports the repair journey from service discovery and photo-assisted booking through branch matching, live status tracking, staff assignment, inventory operations, and a clearly labelled simulated payment flow.

The current coursework scope covers the Colombo and Galle branches. Fixora remains a single Android application; customer, technician, and administration responsibilities are developed through team feature branches rather than separate projects.

## Key Features

### Customer

- Email/password registration and login, Google sign-in, and logout
- Device category, brand, model, and repair-service selection
- Multi-step repair booking with issue details and preferred date/time
- Camera capture and gallery photo attachment with client-side compression
- GPS-assisted branch recommendation using distance, technician skills, and spare-part availability
- Real-time repair tracking and status timeline
- Active repair list, completed repair history, and repair details
- Profile viewing, editing, profile photo management, and theme preference
- Simulated card or cash-on-pickup payment with a Firestore receipt record
- Room-backed service catalog cache and one resumable repair draft

### Technician

- Role-aware staff login
- View repairs assigned to the signed-in technician
- View repair and customer-request details
- Advance permitted repair workflow statuses
- Access shared branch and inventory information allowed by the technician role

### Admin

- Operational dashboard and repair queue monitoring
- Repair detail review, branch confirmation, and technician assignment
- Technician creation, editing, availability, branch, and skill management
- Branch operational overview
- User directory with role filtering
- Spare-part inventory creation, editing, stock adjustment, archive, and restore workflows
- Filtered operational reports and analytics for repairs, recorded demo revenue, branches, and technicians

## User Roles

- Customer
- Technician
- Admin
- Branch Manager, using the shared staff interface with branch-scoped permissions

## Technology Stack

- Kotlin and Android SDK 35
- Jetpack Compose with Material 3 and Navigation Compose
- MVVM with presentation, domain, and data layers
- Firebase Authentication and Cloud Firestore
- Firebase Security Rules and Firestore indexes
- Supabase Postgres for spare-part data and Supabase Storage for repair images
- Supabase Edge Function for authorized admin inventory operations
- Room for the service catalog cache and repair draft
- CameraX and the Android Photo Picker
- Google Play Services Location, Google Maps, and Maps Compose
- Kotlin Coroutines, Kotlin Serialization, Ktor, Coil, and Gradle Version Catalogs

## Team Members

| Member | Module |
|---|---|
| Kumuditha | Customer Module |
| Tharush | Technician Module |
| Kavishka | Admin Module |

## Project Structure

```text
.
├── app/
│   ├── src/main/java/com/techfix/app/
│   │   ├── core/                 # Data implementations, design system, navigation, utilities
│   │   ├── domain/               # Models, repository contracts, and branch-matching rules
│   │   └── ui/                   # Authentication, customer, and shared staff Compose UI
│   ├── src/main/res/             # Android resources, fonts, images, themes, and map styles
│   ├── src/test/                 # JVM unit tests
│   └── src/androidTest/          # Android/Firebase integration tests
├── docs/                         # Requirements, architecture, progress, and Supabase setup SQL
├── gradle/                       # Version catalog and Gradle wrapper
├── scripts/                      # Firebase rule and migration verification utilities
├── supabase/                     # Supabase configuration, migrations, and Edge Function source
├── firestore.rules               # Firestore authorization rules
├── firestore.indexes.json        # Firestore query indexes
└── releases/                     # Reviewed APK artifact only
```

## Architecture

Fixora uses a layered MVVM architecture:

- **Presentation:** Jetpack Compose screens, ViewModels, navigation, and UI state.
- **Domain:** backend-independent models and repository contracts. `MatchBranchesUseCase` contains the branch-selection rules.
- **Data:** Firebase, Supabase, Room, location, and storage repository implementations hidden behind domain interfaces.

Firebase Authentication owns sign-in, while a Firestore user document supplies the application role. Firebase stores application data such as users, branches, services, repair requests, payments, and technicians. Supabase stores spare-part inventory and repair images. Room provides the intentionally limited offline cache.

## Build Instructions

### Prerequisites

- Android Studio with Android SDK 35 installed
- JDK 17
- A valid Firebase Android client configuration at `app/google-services.json`
- A local, uncommitted `local.properties` containing the Android SDK path and required public Android-client configuration:

```properties
sdk.dir=/absolute/path/to/Android/sdk
SUPABASE_URL=your_public_supabase_url
SUPABASE_ANON_KEY=your_publishable_or_anon_key
MAPS_API_KEY=your_restricted_android_maps_key
```

Never place a Supabase `service_role` key, Firebase Admin credential, password, or signing secret in this file or anywhere in the Android source.

### Android Studio

1. Open Android Studio.
2. Select **Open** and choose the repository root.
3. Allow Gradle sync to finish.
4. Select the `app` run configuration and an Android device or emulator.
5. Run the application.

### Command line

```bash
./gradlew assembleDebug
```

Run the JVM tests with:

```bash
./gradlew testDebugUnitTest
```

Some Android and backend integration tests require an emulator, a connected device, or explicitly supplied local test credentials.

## APK

The reviewed debug build is stored at:

```text
releases/Fixora-v1.0-debug.apk
```

This is a debug coursework/demo artifact, not a production-signed Play Store release.

## Security

- Private credentials, Firebase Admin service accounts, Supabase service-role keys, private signing credentials, local account files, and secret environment files are excluded from version control.
- `app/google-services.json` is the Firebase Android client configuration and contains no service-account private key. Re-review it whenever it is replaced.
- The Android app receives only the public Supabase URL and publishable/anon key. Access control must remain enforced by Supabase Row Level Security and server-side authorization.
- The Supabase service-role credential used by the admin Edge Function must be supplied only through the deployed function environment and must never be embedded in the Android app or repository.
- Android client API keys should be restricted in their provider consoles to the intended application/package and enabled APIs.

## Screenshots

<!-- Add verified customer-flow screenshots here. -->

<!-- Add verified technician-flow screenshots here. -->

<!-- Add verified admin-flow screenshots here. -->
