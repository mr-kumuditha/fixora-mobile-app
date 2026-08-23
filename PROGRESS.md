# TechFix — Progress Log

Read this plus CLAUDE.md at the start of every new session before doing anything else.

## Block 1 — Setup (done, verified)
- Gradle scaffold: Gradle wrapper 8.9, version catalog, Kotlin/Compose, SDK 35
- Stack wired and compiling together: Compose, Navigation Compose, Firebase (Auth, Firestore), Supabase-kt (Postgrest + Storage), Room, CameraX, Coil, Play Services Location, Credential Manager
- Design tokens in core/designsystem/: light/dark color schemes, one type scale, 8dp spacing grid, corner radius scale
- Role-based nav skeleton: UserRole enum, SessionViewModel, TechFixNavHost (null -> auth gate, CUSTOMER -> customer graph, staff roles -> shared staff graph), placeholder screens exercising it
- `./gradlew assembleDebug`: BUILD SUCCESSFUL, real compile confirmed
- Environment: JAVA_HOME in ~/.zshenv was broken (bad command substitution), fixed, resolves to JDK 17
- Firebase project techfix-mobile-app (944303819849) wired, package com.techfix.app, real google-services.json in place, Firestore + Auth enabled in console, deny-by-default firestore.rules deployed
- Storage decision changed: moved from Firebase Storage to Supabase Storage (free-tier constraint, Firebase Storage now requires the Blaze plan)
- Supabase client wired via BuildConfig from local.properties, only SUPABASE_URL and SUPABASE_ANON_KEY present, secret/service_role key deliberately excluded
- Open item carried into Block 2: SHA-1/SHA-256 fingerprints need adding in Firebase Console (Project Settings > Your apps > Add fingerprint) for Google Sign-In, then re-pull google-services.json

## Block 2 — Authentication (reported finished, not yet logged in detail)
Prompted for: email/password register/login/logout, Google Sign-In via Credential Manager, Firestore user doc created on first sign-in with role defaulting to CUSTOMER, both auth methods behind a repository interface, wired to existing SessionViewModel/TechFixNavHost routing.

Status: needs the actual verification report before this section can be marked done. Specifically:
- Which parts are compile-verified only vs runtime-tested
- Whether email/password was runtime-tested against the real Firebase project
- Whether Google Sign-In could be tested yet (depends on the SHA-1/SHA-256 fingerprints being added)
- Whether the Firestore user doc with role field is confirmed created on sign-in

## Block 3 — Not started
