# Fixora — Build Progress

Running log of what's built and how far each piece has actually been verified.
Verification levels used below:

- **Runtime-verified** — exercised on the physical Pixel 3 against the real backend.
- **Compile-verified** — builds and links, but the code path was never executed.
- **Not started** — no code yet.

---

## Block 1 — Project setup ✅ Runtime-verified (builds and installs)

- Gradle project scaffolded: Kotlin, Compose, compileSdk 35, minSdk 26, Java 17.
- Version catalog (`gradle/libs.versions.toml`) covering Compose, Navigation,
  Firebase, Supabase, Room, CameraX, Coil, Play Services Location,
  Credential Manager.
- Layered package structure: `core/designsystem`, `core/data`, `core/navigation`,
  `domain`, `ui`.
- Role-based navigation skeleton: `UserRole`, `SessionViewModel`, `FixoraNavHost`.
  Routes null → auth, CUSTOMER → customer graph, staff roles → shared staff graph.
- `assembleDebug` passes.

## Block 2 — Authentication ✅ Runtime-verified (3/3 instrumented tests pass)

- `AuthRepository` interface in `domain/auth/`, `FirebaseAuthRepository` in
  `core/data/auth/`. ViewModels never call Firebase directly.
- Email/password register, sign-in, sign-out via Firebase Auth.
- Google Sign-In via Credential Manager — **compile-verified only**, no
  interactive flow exercised on device.
- On first sign-in by either method, creates `users/{uid}` in Firestore with
  role defaulting to `CUSTOMER` if absent. Staff roles are assigned by editing
  that document; there is no self-service staff signup.
- `LoginScreen` / `RegisterScreen` wired through `SessionViewModel` routing.
- Firestore rules: a signed-in user may read/write only their own `users/{uid}`.
  Everything else deny-by-default until Block 3.

Instrumented tests (`FirebaseAuthRepositoryTest`, real Firebase project,
physical Pixel 3): `registerCreatesFirestoreUserDocWithCustomerRole`,
`signInAfterRegisterResolvesSameRoleFromExistingDoc`,
`signInWithWrongPasswordFails` — all passing.

## Branding + design system ⚠️ Compile-verified only

- App renamed **TechFix → Fixora**, tagline "Smart Device Repair Platform".
  Display branding only — `applicationId` stays `com.techfix.app` so the
  existing Firebase config and Google OAuth client keep working.
- Launcher icons generated from `Assets/app Logo.PNG`: adaptive
  (foreground + `#2B2E3B` background sampled from the art) plus legacy
  mipmaps at all five densities. Logo also shown on both auth screens.
- Design tokens reconciled to the spec in `CLAUDE.md`: indigo `#4F46E5`
  primary, orange `#FF7A45` accent, separately tuned dark palette, Inter
  (bundled variable font), 8dp spacing grid, 8/12/20dp radii.

`compileDebugKotlin` passes. The on-device light/dark render check of the
auth screens and the launcher icon has **not** been done — the Pixel 3
disconnected from USB before it could run.

## Block 3 — Data model and seed data ✅ Runtime-verified (5/5 instrumented tests pass)

- Domain interfaces added, following the `AuthRepository` pattern (interface
  in `domain/`, impl in `core/data/`): `ServiceRepository`, `BranchRepository`,
  `RepairRequestRepository`, `PaymentRepository`, `TechnicianRepository`,
  `SparePartRepository`. Wired through a new `RepositoryProvider` (same
  hand-rolled lazy-singleton style as `AuthRepositoryProvider`, no DI framework).
- Firestore, backed by `Firestore*Repository` classes: `services` (category,
  name, description, basePrice), `branches` (name, `location{lat,lng}`,
  address), `repairRequests` (customerId, serviceId, nested deviceDetails,
  issueDescription, imageUrls, branchId, technicianId, status, createdAt
  server-timestamp, scheduledAt), `payments` (repairRequestId, amount, method,
  status, receiptId, createdAt). `observeRepairRequest` exposes a `Flow` off a
  snapshot listener, ready for the Block 6 tracking screen.
- Supabase Postgres, backed by `Supabase*Repository` classes, schema and seed
  in `docs/supabase/schema.sql`: `technicians` (name, branch_id,
  category_skills text[], available), `spare_parts` (name, category,
  compatible_categories text[]), `spare_part_stock` (part_id, branch_id,
  quantity, unique per part+branch). RLS enabled, anon/authenticated
  read-only for now — no write policy exists yet, since nothing writes to
  these tables before Block 7.
- Firestore rules extended past the Block 2 `users/{uid}` rule: catalog
  (`services`, `branches`) read-by-any-signed-in-user / write-by-ADMIN;
  `repairRequests` read/write scoped to the owning customer or any staff
  role; `payments` create-once (no update rule, receipts aren't editable).
  Self-elevation is blocked — a user can create their own doc but only with
  `role == 'CUSTOMER'`, and can't change `role` on update. Deployed live via
  `firebase deploy --only firestore:rules,firestore:indexes` (also added two
  composite indexes for the `repairRequests`/`payments` ordered queries).
- Seed data: 12 services across all four categories (mobile, laptop, desktop,
  tablet) in `FirestoreSeedData` (androidTest source — not compiled into the
  app); both branches with real Colombo/Galle coordinates; 3 technicians per
  branch in `schema.sql` with deliberately uneven skills/availability (Galle
  has no DESKTOP technician at all, one technician per branch marked
  unavailable); 9 spare parts with per-branch stock deliberately uneven
  (several parts sit at 0 at one branch and stocked at the other) so Block 5's
  matching logic has real gaps to resolve, not just distance.
- A seed `ADMIN` account (`techfix-seed-admin@example.com`) was created to
  satisfy the write-rules above; its Firestore role was hand-flipped to
  `ADMIN` in the console (self-elevation is blocked by rule, by design — same
  pattern as Block 2's "no self-service staff signup"). Its credentials sit
  in `local.properties` as `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD`
  (gitignored), passed to the instrumented test via
  `testInstrumentationRunnerArguments`, never compiled into the app.

`Block3DataLayerTest` (androidTest, real Firebase + real Supabase, physical
Pixel 3): signs in as the seed admin, seeds Firestore idempotently, then
queries every collection/table through the actual repository classes —
`branchesCollectionHoldsBothSeededBranches`,
`servicesCollectionCoversAllFourCategories`,
`repairRequestsCollectionRoundTrips` (create/read/update-status/list-by-
customer/list-by-branch, then deletes the test doc; also round-trips a
payment the same way), `techniciansTableHoldsSeededStaffPerBranch`,
`sparePartsAndStockAreSeededUnevenlyAcrossBranches` — 5/5 passing. Full
suite (this + the existing Block 2 auth tests) also run clean: 8/8. No
regressions. `assembleDebug` still passes.

## Block 4 — Service catalog + booking start ⚠️ Compile-verified only

- Service Catalog (`ServiceCatalogScreen`/`ViewModel`): loads all services
  from `RepositoryProvider.services`, then filters/groups client-side by
  search text and category chip (dataset is 12 docs — no server-side query
  needed). Loading/empty/error/content states per the design system: animated
  skeleton cards while loading, icon+message+retry on error, icon+message+
  "Clear filters" when a search/filter yields nothing.
- Service Detail (`ServiceDetailScreen`/`ViewModel`): category, name,
  description, base price, "Book Repair" CTA in the accent color. Same
  loading/error states.
- Book Repair steps 1-3 (`BookRepairScreen`/`BookRepairViewModel`), stopped
  before branch selection as instructed — step 3's "Continue to Branch &
  Schedule" button is permanently disabled with a caption explaining it's
  Block 5's job:
  - Step 1: device category (locked to the service's category, since the
    booking started from a specific service), brand, model, optional serial.
  - Step 2: free-text issue description, 500-char cap.
  - Step 3: photos. CameraX capture (full-screen `CameraCaptureDialog`) and
    system Photo Picker (`PickMultipleVisualMedia`, up to 5) both feed the
    same path — compress via `ImageCompressor` (downscale to 1600px longest
    edge, JPEG q80, both on `Dispatchers.IO`) — then upload through the new
    `ImageUploadRepository` / `SupabaseImageUploadRepository` to the
    Supabase `repair-images` bucket, storing the returned public URL. Each
    thumbnail shows uploading/failed(retry)/uploaded state; failed uploads
    retry individually; removing an image best-effort deletes the uploaded
    object too.
  - Step 3's "Continue to Branch & Schedule" originally hardcoded
    `enabled = false`. Changed after device testing (2026-08-21) to the
    placeholder variant the brief also allowed: it now enables on
    `canAdvanceFromStep3` (at least one photo UPLOADED and none still
    in flight) and shows a snackbar saying branch matching arrives next,
    with the caption above it explaining why it's disabled when it is
    ("Add at least one photo" / "Waiting for photos to finish uploading").
    Still does not navigate anywhere — that stays Block 5's job.
  - Draft state lives only in `BookRepairViewModel` (in-memory) — no Room
    persistence yet, that's explicitly Block 8's "one draft repair request"
    job, not this one.
- Navigation: `Graph.CUSTOMER` converted from a single composable into a
  nested graph (`HOME → CATALOG → SERVICE_DETAIL/{id} → BOOK_REPAIR/{id}`),
  same pattern as the existing `authGraph`. `CustomerHomeScreen` restyled
  off placeholder text onto a proper card + accent CTA.
- New `docs/supabase/storage_setup.sql` — bucket `repair-images` (public
  read) plus anon insert/select policies. **Not yet run against the live
  Supabase project** (no CLI/MCP access from this session) — same manual
  step as `schema.sql` was in Block 3. Photo upload will fail with a bucket-
  not-found error until this is run in the Supabase SQL editor.
- Build-classpath fix, unrelated to app behavior: `ProcessCameraProvider`
  returns a Guava `ListenableFuture`, which didn't resolve at *compile*
  time even though CameraX itself was already an approved dependency (Guava
  was on the runtime classpath via Firestore, not the compile one). Fixed
  by adding `androidx.concurrent:concurrent-futures-ktx` (for a clean
  `.await()`) and pinning `com.google.guava:guava:32.1.3-android` directly
  — matches the version already resolved transitively, so no version skew.
  Flagging per CLAUDE.md's "no new dependencies without asking" — these are
  compile-classpath glue for an already-locked-in library, not a new
  product-level dependency, but wanted it visible rather than silent.

`compileDebugKotlin` and `assembleDebug` both pass clean (no warnings).
**No physical device was connected this session** (`adb devices` empty) —
none of Block 4 has been runtime-verified: the catalog list, service
detail, camera capture, gallery picker, compression, or the Supabase
Storage upload have only been read through, not exercised. Needs a full
on-device pass before this can be marked verified, including confirming
`storage_setup.sql` has been applied.

## Block 5 — GPS branch matching, map, submit ⚠️ Compile-verified + 8/8 JVM unit tests (no device pass yet)

- **Maps key handling.** `MAPS_API_KEY` is read out of `local.properties`
  (gitignored) in `app/build.gradle.kts` and injected two ways: as a
  `manifestPlaceholders` value substituted into the
  `com.google.android.geo.API_KEY` meta-data in `AndroidManifest.xml`, which
  is where the Maps SDK actually reads it, and as a `BuildConfig.MAPS_API_KEY`
  field. No key literal is in any source file — verified by grepping the tree
  (the only `AIza` hit outside `build/` is the pre-existing Firebase key in
  `google-services.json`, which is a different key) and by reading the merged
  debug manifest to confirm the placeholder resolved.
- **New dependencies** (flagged per CLAUDE.md): `com.google.maps.android:maps-compose`
  6.1.2, as instructed, plus `com.google.android.gms:play-services-maps` 19.0.0
  declared explicitly rather than taken transitively, so the version is pinned
  here instead of by maps-compose.
- **Location.** `domain/location/` holds `Coordinates`, a pure-Kotlin haversine
  `distanceKmBetween`, and a `LocationRepository` whose result is a three-case
  sealed interface — `Available` / `PermissionDenied` / `Unavailable` — rather
  than a nullable, because the branch picker says something different for each
  and none of them is an error. `core/data/location/FusedLocationRepository`
  implements it over the fused provider: asks for a fresh fix with an 8s
  timeout, falls back to `lastLocation`, and maps every failure (including a
  `SecurityException` from a permission revoked mid-call) onto a result case
  instead of throwing. `RepositoryProvider.location(context)` holds the single
  instance, keyed off the application context.
- **Matching.** `domain/matching/MatchBranchesUseCase` — the graded piece, and
  deliberately not in the ViewModel or behind a direct Maps/Supabase call. It
  depends only on `BranchRepository`, `TechnicianRepository`, and
  `SparePartRepository`, so it has no idea technicians come from Firestore and
  stock comes from Supabase. Per branch it queries both backends
  concurrently, then scores a weighted sum of three 0..1 sub-scores:
  technician availability (weight 0.45), compatible-part stock (0.35), and
  distance (0.20, a smooth `1 / (1 + km / 30)` decay rather than a threshold).
  Both availability terms have a high floor (0.8) plus a smaller depth bonus,
  which makes "can cover this at all" nearly binary and depth a tie-breaker.
  Availability therefore carries 0.8 against distance's 0.2 — a near branch
  that cannot do the job loses to a far branch that can, while distance still
  decides between equally capable branches. With no location, distance is held
  at a neutral 0.5 for every branch rather than being treated as zero, and
  `distanceKm` comes back null rather than 0. `BranchMatchResult` exposes
  `allBranchesBlocked` so the "no branch can start it right now" case offers
  the best-ranked branch with a wait instead of dead-ending, per architecture
  doc §8 point 5.
- **Branch picker.** Built as step 4 of the existing Book Repair flow
  (`BranchPickerStep.kt`), replacing Block 4's disabled stub — that matches
  architecture doc §5, which specifies Book Repair as one multi-step flow
  including location/branch, and it avoids sharing draft state across nav
  destinations. Entering step 4 requests location permission once, then runs
  the use case either way. Contents: a 220dp map with both branch pins and the
  customer's position, framed to a `LatLngBounds` of all pins on load; branch
  cards showing name, address, calculated distance, free-technician count and
  parts-in-stock count, with the recommended branch pilled "Best match" and
  the selected one preselected; a drop-off slot defaulting to tomorrow 10:00
  with M3 date + time pickers. All four screen states are present (skeleton
  loading, error with retry, empty, content), and the map is styled from the
  Fixora palette in both light and dark via `res/raw/map_style_*.json` rather
  than left on default Maps colours.
- **Submit.** Confirming writes the completed `RepairRequest` to Firestore
  through the existing `RepairRequestRepository` — device details, issue,
  Supabase image URLs, matched `branchId`, chosen `scheduledAt`, status
  `PENDING`, `technicianId` left null (naming a technician is a Branch Manager
  action in Block 7). Success shows a confirmation pane with a short reference
  and a Done button that pops the whole booking flow; there is no tracking
  screen to route to until Block 6.
- **Step indicator** is now four steps (Device, Issue, Photos, Branch) and the
  step-3 button reaches step 4 instead of showing the Block 4 placeholder
  snackbar.

`MatchBranchesUseCaseTest` (local JVM, `app/src/test/`, no device or network):
fakes mirror `docs/supabase/schema.sql` and `FirestoreSeedData` row for row,
including the deliberate gaps, so the assertions describe what the app will
actually show against the live backends. 8/8 passing:
`desktop repair beside the Galle branch is still sent to Colombo` (the Galle
DESKTOP gap beating 0 km — the case the whole rule exists for),
`mobile repair beside the Galle branch stays at Galle because both can handle it`
and `the same mobile repair from Colombo flips the recommendation` (distance
still decides when availability ties, so availability is not a blunt
override), `unavailable technicians do not count towards a branch`,
`out of stock parts are reported separately from parts not carried`,
`with no location the ranking falls back to availability alone`,
`when no branch can start the job it still offers the closest match`, and
`distance between the two seeded branches is realistic`. Uses `runBlocking`
from the coroutines dependency already present — no new test dependency.

`compileDebugKotlin` and `assembleDebug` both pass clean, no warnings.

## Block 6 — Repair tracking + repair history ⚠️ Compile-verified only (no device connected)

- **Timeline widened to the nine stages named in the brief.** `RepairStatus`
  was seven values (PENDING, ASSIGNED, IN_PROGRESS, AWAITING_PARTS,
  READY_FOR_PICKUP, COMPLETED, CANCELLED); the tracking screen was specified
  against nine (Submitted, Confirmed, Received, Diagnosis, Approved, In
  Progress, Quality Check, Ready, Completed). The enum now carries those nine
  as `isTimelineStage = true`, plus AWAITING_PARTS and CANCELLED off the
  timeline. **This renamed two existing values** — PENDING → SUBMITTED and
  ASSIGNED → CONFIRMED — so `fromRaw` maps both old raw strings onto the new
  values and repair requests already written to Firestore still read back
  correctly. `assignTechnician` now writes CONFIRMED, and `Block3DataLayerTest`
  was updated to assert SUBMITTED. Flagging it because the requirement
  analysis doc never listed the nine stages; the instruction to build them did.
- **Repair Tracking Detail** (`RepairTrackingScreen` / `RepairTrackingViewModel`):
  status comes off `RepairRequestRepository.observeRepairRequest`, which is a
  Firestore snapshot listener, so the timeline moves when staff advance the
  repair — no refresh control anywhere on the screen. Service and branch names
  are fetched once, the first time a snapshot names them, rather than on every
  status push. Retry restarts the listener rather than resuming it.
- **The timeline itself** (`RepairTimeline.kt`) is driven by one animated
  progress float, so the connecting line fills downward and the stage dots
  cross-fade in step (250–280ms, inside the design system's 300ms ceiling) —
  the status does not snap. Only the active stage expands its explanation, so
  nine stages don't read as a wall of text. AWAITING_PARTS is not a stage of
  its own: it holds at In Progress and surfaces there as a hold chip.
  CANCELLED replaces the timeline with a cancelled pane instead of showing
  eight greyed-out stages.
- **Reusable status chip** (`RepairStatusUi.kt`) — label, one-line
  description, icon, and colour per status in one place, used by tracking,
  history, and the Home card, so a status never reads differently depending on
  the screen. Container colour is animated, not swapped, so a live change
  reads as a change. Same domain/UI split as `DeviceCategoryUi`: the enum
  itself stays free of presentation.
- **Active repair on Home** (`CustomerHomeViewModel`, rewritten
  `CustomerHomeScreen`): a list read finds the newest non-terminal repair,
  then the same live listener keeps its status current, so Home and the
  tracking screen can't disagree. Card shows device, service, status chip, an
  animated stage-progress bar, and a Track repair CTA. Refreshed on every
  entry to Home so a repair booked elsewhere in the session is picked up.
  A failed check degrades to a caption, not a full-screen error — the rest of
  Home still works. Home also gained a Repair history entry card.
- **Repair History** (`RepairHistoryScreen` / `RepairHistoryViewModel`): the
  customer's finished repairs, newest first, from
  `getRepairRequestsForCustomer`. "Finished" is `status.isTerminal` —
  COMPLETED *and* CANCELLED, because a cancelled repair is no longer
  trackable and filtering on COMPLETED alone would make it vanish from the
  app entirely. Filtering is client-side: the existing `customerId` +
  `createdAt` composite index already serves the read and one customer's list
  is small. Cards use the first repair photo as their thumbnail, falling back
  to the device-category icon.
- **History Detail** (`RepairHistoryDetailScreen` /
  `RepairHistoryDetailViewModel`): final status, device info, reported issue,
  dates, and a tappable photo gallery (tap opens the image full-size). Cost
  prefers the SUCCESS payment record for the request and falls back to the
  service's base price **explicitly labelled "Estimated cost — no payment
  recorded"**, so nothing on the screen passes an estimate off as a charge.
  Block 7's payment flow is what populates the real figure.
- All three screens carry the four required states — animated skeleton while
  loading, message + retry on error, icon + message + action when empty,
  content — and crossfade from loading into content rather than hard-cutting.
- **Navigation**: `CustomerRoutes` gained `TRACKING/{requestId}`, `HISTORY`,
  and `HISTORY_DETAIL/{requestId}`. The booking flow's submitted pane now
  leads somewhere: its primary button is Track repair (pops the booking flow,
  then opens the timeline, so Back lands on Home rather than back inside a
  submitted request), with Back to home as the secondary action.
- No new repository, no new repository method, and no new dependency. The two
  screens read through `RepairRequestRepository` as instructed; service,
  branch, and payment names come from the repositories that already existed.

`compileDebugKotlin`, `assembleDebug`, and `testDebugUnitTest` all pass clean,
no warnings. **No physical device was connected this session** (`adb devices`
empty), so despite the instruction to test on the Pixel 3, none of Block 6 has
been runtime-verified — see the follow-ups below for exactly what still needs
an on-device pass.

## Block 7 — Simulated payment + staff screens ✅ COMPLETE — physically verified end to end on the Pixel 3 against the real backend, 49/49 JVM unit tests

### Payment (customer side)

- **Entry point.** Repair Tracking Detail grows one action, and only when the
  device is actually ready: a "Pay now" card appears on
  `READY_FOR_PICKUP`. It disappears on its own after a successful payment,
  because paying is what moves the repair to COMPLETED.
- **One flow, one ViewModel.** `PaymentScreen` / `PaymentViewModel` under
  `ui/customer/payment/`, built as six panes on a single destination
  (Summary → Method → Demo details → Processing → Result → Receipt) rather
  than six routes, matching the architecture doc's "Payment
  Summary/Method/Processing/Result (one flow)". Back inside the flow steps
  back a pane; from the first pane it leaves the screen.
- **Labelled as a demo throughout**, per CLAUDE.md. The demo banner sits
  outside the pane switcher so it is on screen at every step, the pay button
  reads "Pay Rs. N (demo)", the receipt says so, and the failure copy says
  explicitly that nothing was charged.
- **Card form is format-validated only** (`DemoCard.kt`): 16 digits grouped in
  fours as typed, Luhn checksum, MM/YY expiry that rejects a past month, a
  3-digit CVV, and a non-blank name. **No card detail is stored, transmitted,
  or written to Firestore** — the receipt record keeps only amount, method,
  and a generated receipt id, and the receipt line shows last-four from
  in-memory state, nothing more. Cash on pickup skips the details pane
  entirely.
- **The failure path is an explicit switch on the form** ("Simulate a declined
  payment"), not a magic card number. There is no processor to decline
  anything, so a hidden trigger would have been a fiction; a labelled switch
  is honest and makes the failure branch reachable in the demo video in one
  tap. A declined attempt still writes a FAILED payment record.
- **Processing is a fixed 1.8s delay**, not a network call.
- **On success**: a SUCCESS `payments` document is written through the
  existing `PaymentRepository`, then the repair is moved to COMPLETED, which
  is what lands it in Repair History and gives History Detail a real cost
  instead of the "Estimated cost" fallback Block 6 had to show. If the receipt
  writes but the status update fails, the receipt stands and the pane carries
  a warning rather than pretending the payment failed.
- **Reopening a paid repair** opens straight on the receipt instead of letting
  it be paid twice — the flow checks for an existing SUCCESS payment on load.

### Staff (one screen set, role-gated)

- **`StaffContext`** is the gate. Admin, Branch Manager, and Technician stay
  three distinct roles in the data model, and every behavioural difference
  between them is a flag on this one class (`canAssign`, `canEditStock`,
  `seesAllBranches`, `seesOnlyOwnRepairs`) rather than a separate screen set —
  which is the decision locked in CLAUDE.md. It is built from the signed-in
  `AuthUser`.
- **`AuthUser` gained `branchId` and `technicianId`**, read off the existing
  `users/{uid}` document. Both are optional and are set the same way `role`
  already was — by an Admin editing that document, since there is no
  self-service staff signup. `branchId` scopes the queue and stock view to one
  branch; `technicianId` (a Firestore `technicians` document id) links a staff
  login to the technician repairs are assigned to. **Both degrade
  gracefully**: a staff record missing `branchId` gets the unscoped view, and
  a Technician with no `technicianId` sees their branch's work rather than an
  empty screen. `SessionViewModel` now carries the whole `AuthUser` instead of
  just the role, and still exposes `role` for the routing that already existed.
- **Staff Dashboard** — four counts (New requests, In progress, Ready for
  pickup, and Assigned to me when the login is linked to a technician) plus
  entry points. The appointment-queue entry only appears for a role that can
  assign; the others are shared. Loading / error / content states.
- **Appointment Queue** (`StaffAppointmentsScreen`) — the two slices on one
  screen: **New** is the SUBMITTED queue the brief asks for, **Active** is
  everything already moving. A Technician gets only the Active tab, already
  narrowed to their own repairs. All four states present.
- **Appointment Detail / Assignment** — the branch options come from
  `MatchBranchesUseCase`, the Block 5 rule itself rather than a second copy of
  it, so a manager confirming a branch sees the same technician and
  spare-part cover the automatic match was scored on (free technicians, parts
  in stock, and a warning when a branch cannot start the job). It is invoked
  with a **null location** on purpose: the staff member is not standing where
  the customer is, so distance is held neutral and availability alone orders
  the list. Confirming writes branch + technician + CONFIRMED. Changing branch
  clears a technician who does not work there rather than keeping an invalid
  assignment. For a Technician the same card renders read-only, saying whose
  action it is.
- **Status advance** — one button, no forms, walking exactly the range the
  brief names: RECEIVED → DIAGNOSIS → APPROVED → IN_PROGRESS → QUALITY_CHECK
  → READY. Encoded as `RepairStatus.nextStaffStage`. SUBMITTED has no advance
  (it needs assigning first) and **READY has none either — staff can never
  mark a repair COMPLETED, only a successful payment does that**, which is what
  keeps the payment flow load-bearing rather than decorative. A repair held on
  AWAITING_PARTS resumes at IN_PROGRESS instead of dead-ending. A Technician
  is blocked from advancing a repair assigned to someone else.
- **Technician & Spare Parts** — the combined two-tab screen from the
  architecture doc's screen list, reading through the Supabase repositories
  Block 3 built. Technicians are read-only (roster changes are not a screen
  action, and no write policy exists for that table). Stock is the one
  editable number, through a stepper rather than a free-text field, gated on
  `canEditStock` so a Technician sees the numbers without the controls. An
  Admin gets a branch chip picker; everyone else is pinned to their branch.

### Data layer

No new repository, as instructed. Four additions to existing ones, flagged
because they are interface changes:

- `RepairRequestRepository.getAllRepairRequests()` — the unscoped staff read
  for an Admin. Deliberately not a per-status query: the staff screens slice
  the same list several ways and each server-side filter would need its own
  composite index for a dataset this size, so it is one ordered read filtered
  in the ViewModel. **No new Firestore index was needed.**
- `RepairRequestRepository.assignTechnician` **gained a `branchId` parameter**
  — confirming an appointment can move it to a different branch than the one
  the customer's booking matched, so writing the technician without the branch
  would have been half the action. No existing caller was affected
  (`Block3DataLayerTest` doesn't use it). It now runs in a **Firestore
  transaction** and returns `Result<RepairStatus>`: the status to write depends
  on the status already stored (see the audit fixes below), so reading and
  writing have to happen as one step, and the caller gets back what the write
  actually settled on.
- `SparePartRepository.updateStock(partId, branchId, quantity)` — an upsert on
  `(part_id, branch_id)`, because a part never stocked at a branch has no row
  at all and the read path already reports that as quantity 0.
- `RepairRequest` gained **`completedAt`**, closing the Block 6 gap logged
  below. `updateStatus` stamps it with a server timestamp when and only when
  the status reaches COMPLETED; History Detail now shows a real completion
  date. Repairs finished before the field existed simply don't show the row.

### Firestore rules and Supabase policies

- **Firestore rules needed no change.** The Block 3 rules already give staff
  read/update across `repairRequests` and allow the owning customer to create
  a `payments` document, which is exactly what this block does.
- **Supabase needs one new policy file**: `docs/supabase/staff_write_policies.sql`
  grants insert + update on `spare_part_stock` only. It is separate from
  `schema.sql` on purpose — schema.sql drops and recreates the tables, so
  re-running it would wipe the seed data. `technicians` and `spare_parts` stay
  read-only. **Not yet run against the live project** — stock updates will
  fail with an RLS error until it is.
  Stated plainly in the file and here: the app authenticates against Firebase,
  not Supabase, so every request arrives as `anon` and Postgres cannot tell a
  Branch Manager from a customer. The role gating on stock edits is enforced
  in the app (`StaffContext.canEditStock`), not by RLS. That is the honest
  trade-off for a coursework demo with a publishable anon key; a production
  build would put Supabase behind Firebase-issued JWTs and have the policy
  check the claim.

### Audit findings and fixes (2026-08-22)

An audit was run against the **live** backends rather than against this
document. It confirmed the read paths and permissions, and found three
defects, all now fixed:

1. **Reassignment reset the customer's timeline.** `assignTechnician` wrote
   `status = CONFIRMED` unconditionally, and the assignment card is shown for
   any status a manager can act on — so reassigning a technician on an
   IN_PROGRESS repair dragged the customer's live timeline back to Confirmed.
   Fixed: the status move is now `RepairStatus.afterAssignment` (SUBMITTED →
   CONFIRMED, everything else unchanged), applied **inside a Firestore
   transaction** that reads the current status and writes in one step, so a
   concurrent status advance can't be clobbered between a read and a write.
   The status field is only written when it actually changes. The repository
   returns the resolved status, so the ViewModel reports "confirmed" or
   "reassigned, status unchanged" from what the transaction really did rather
   than assuming.
2. **Payment could charge Rs. 0.** `amount` fell back to `0.0` when the
   service lookup failed, and nothing stopped the flow — it would have written
   a SUCCESS receipt for nothing and marked the repair COMPLETED. Fixed:
   `amount` is now `Double?` with no default, and a failed lookup blocks the
   flow with an error state.
3. **Payment had no status guard.** Only the tracking screen hiding the button
   kept an unready repair out of the flow, which is not a guard. Fixed: the
   rule is enforced in the ViewModel, re-checked at every step that moves the
   flow forward, not only on load.

Both payment rules live in one pure function, `PaymentEligibility.blockReason`,
so they are directly testable. Price is checked **before** status on purpose:
otherwise a broken lookup on an in-progress repair would tell the customer to
wait, hiding a real failure behind a normal-looking message.

### Verification — build and unit tests

`compileDebugKotlin`, `assembleDebug`, `compileDebugAndroidTestKotlin`, and
`testDebugUnitTest` all pass clean, no warnings. The physical end-to-end
result is recorded further down.

JVM unit tests, **49/49 passing** (8 from Block 5, 41 for Block 7, no new test
dependency):

- `DemoCardTest` (11) — card grouping and the 16-digit cap, Luhn accepting
  4242…4242 and 4111…1111 and rejecting a single mistyped digit, short- and
  empty-number messages, a past expiry rejected while the current month is
  accepted, months outside 01–12 rejected, CVV length, receipt last-four only.
- `PaymentEligibilityTest` (8) — a ready repair with a known price is payable;
  a failed lookup and a zero/negative amount are both refused; price checked
  before status; **every** status other than READY refused, driven off the
  enum so a new status can't silently become payable; completed and cancelled
  get their own wording; an already-paid repair opens its receipt whatever its
  status.
- `RepairStatusStaffAdvanceTest` (9) — the six-stage staff walk in order,
  SUBMITTED not advanceable pre-assignment, READY not advanceable (payment
  completes a repair, not staff), terminal states going nowhere,
  AWAITING_PARTS resuming, and the four reassignment cases including
  "SUBMITTED is the only status assignment changes at all".
- `SnapshotListenerRetryTest` (7) — the live-tracking regression: UNAVAILABLE
  and the other transient codes re-listen, every terminal code gives up, a
  non-Firestore throwable is terminal so a mapping bug can't loop, and the
  backoff doubles, caps, and never overflows to a negative delay.
- `StaffContextTest` (6) — every role-gating flag.

**Live backend checks (reads and permissions only):**

- Staff queries run against real Firestore as a `BRANCH_MANAGER` token: both
  `getRepairRequestsForBranch('colombo')` and the unscoped
  `getAllRepairRequests()` are **allowed** — the unfiltered admin read passes
  despite `isStaff()` doing a per-document `get()`. No missing index.
- A staff account **is** allowed to update `repairRequests`, and **is
  correctly refused** creating a `payments` document (the rule requires the
  caller to own the repair). Both probed with no-op writes.
- Supabase policies from `staff_write_policies.sql` are live and correctly
  scoped: `spare_part_stock` upsert **succeeds**, `technicians` and
  `spare_parts` updates are **blocked** (verified with
  `Prefer: return=representation` — a bare `204` from PostgREST is ambiguous,
  it is also returned when RLS matches zero rows).

### Runtime bug found in physical testing, and fixed (2026-08-22)

The first physical end-to-end run got as far as SUBMITTED → CONFIRMED →
RECEIVED on the customer's tracking screen, then **stopped updating** while the
technician carried on advancing the repair.

**It was not the write path, and not the UI.** The live documents confirmed the
technician's writes had landed correctly: field `status`, raw value
`READY_FOR_PICKUP` matching the enum name exactly, `technicianId` set, right
collection, right document. `RepairStatus.fromRaw` maps every value the staff
screens can write, and the timeline renders straight off `status` with no
cached progress value.

**Root cause — `observeRepairRequest` ended its stream on the first listener
error and never re-listened.** A Firestore `addSnapshotListener` callback that
reports an error is spent: the registration is dropped and nothing further
arrives on it. The flow called `close(error)` on that, which terminates the
`callbackFlow` permanently. The collector in `RepairTrackingViewModel`
completed, and nothing re-subscribed for the life of the screen.

Two things then made it invisible rather than obvious:

- `RepairTrackingViewModel` catches the failure into `errorMessage`, but
  `RepairTrackingScreen` only shows the error pane when
  `errorMessage != null && request == null`. Once any snapshot had arrived,
  `request` was non-null, so the screen kept rendering the last status it had
  received with no error and no retry offered.
- `CustomerHomeViewModel` swallows listener failures outright (`.catch { }`),
  so the Home card froze the same way.

The trigger is any transient drop — the backend briefly unavailable, a deadline
exceeded, an auth-token refresh — which is close to guaranteed over the minutes
a repair takes to walk its stages. That is why the early stages worked and the
later ones did not: the listener was still alive early on, and dead later.

**Fix:** `observeRepairRequest` now retries. `retryWhen` re-runs the flow
builder, which registers a *new* listener, and Firestore delivers the current
document straight away — so the screen catches up on everything it missed while
disconnected. Backoff doubles from 1s to a 30s ceiling so a genuinely down
backend isn't hammered. Terminal failures (rules denial, not signed in,
malformed query) are still rethrown, because re-listening would fail
identically and the error belongs on screen instead. The policy lives in
`SnapshotListenerRetry` rather than inline, keyed on the SDK's code *names* so
it can be unit-tested on the JVM — the Firestore `Code` enum's class
initialiser needs the Android runtime.

Every caller of `observeRepairRequest` gets the fix: tracking, the Home active-
repair card, and any future listener on that method.

Files changed, and why:

- `core/data/repair/SnapshotListenerRetry.kt` **(new)** — which listener
  failures are worth re-establishing, and the backoff schedule.
- `core/data/repair/FirestoreRepairRequestRepository.kt` — `observeRepairRequest`
  gained the `retryWhen`. Nothing else in the file changed.
- `test/.../core/data/repair/SnapshotListenerRetryTest.kt` **(new)** — 7 tests,
  the first of which is UNAVAILABLE specifically, the code behind this bug.

**Fix confirmed on device 2026-08-22**: the customer's tracking screen now
follows the technician through DIAGNOSIS → APPROVED → IN_PROGRESS →
QUALITY_CHECK → READY_FOR_PICKUP without being reopened.

**Still open, deliberately not changed** (it masked the bug but did not cause
it): `RepairTrackingScreen` ignores `errorMessage` whenever a request is
already loaded, so a *terminal* listener failure — rules denial, signed out —
would still freeze the screen silently. The recoverable case, which is what
actually bit, is fixed. Worth closing separately: it wants a "reconnecting"
indication rather than replacing good content with an error pane.

### ✅ Physical end-to-end verification (2026-08-22)

Run on the physical Pixel 3 against the **real** Firebase and Supabase
projects — not an emulator, not a mock. The full chain was exercised in one
pass:

- customer creates an appointment
- Admin confirms it
- the assigned technician receives it
- **customer tracking updates in real time**
- technician progresses DIAGNOSIS → APPROVED → IN_PROGRESS → QUALITY_CHECK →
  READY_FOR_PICKUP
- **the customer side reflects every one of those changes without reopening
  the screen** — the specific failure that Block 7's runtime bug caused
- technician reassignment preserves the current status
- invalid payment attempts are blocked
- a successful payment completes against the real backend
- a receipt is created
- `completedAt` is recorded
- Repair History displays the actual paid amount, not the estimate

**Corroborated in the live database afterwards.** The three write paths that
the audit had found completely unexercised now hold real data:

| Previously | Now |
|---|---|
| `payments`: 0 documents | 1 — receipt `FX-WJZ28ZSN`, `SUCCESS`, amount 4800 |
| repairs with a `technicianId`: 0 | 1 — assignment recorded |
| repairs with `completedAt`: 0 | 1 — stamped `2026-08-22T07:14:45.829Z` |
| nothing past SUBMITTED | the request reached `COMPLETED` |

The payment document references the same repair request that carries the
`completedAt` stamp, so the two halves of the completion are linked as
designed, not coincidentally both present.

One nuance worth carrying into the demo, stated plainly rather than rounded
up: the successful payment on record used **cash on pickup**. The card pane's
validation was exercised (invalid attempts blocked), but a successful *card*
payment does not appear in the data. Worth one more pass before the demo
video if the video is going to show the card route.

### Defects found and closed in this block

All four were found after the first implementation pass, and all are now fixed
**and** physically verified:

1. **Reassignment reset the customer's timeline** (audit) — `assignTechnician`
   wrote `CONFIRMED` unconditionally. Fixed with
   `RepairStatus.afterAssignment` inside a Firestore transaction. Verified on
   device: reassigning preserves the current status.
2. **Payment could charge Rs. 0** (audit) — `amount` fell back to `0.0` on a
   failed service lookup. Fixed: `amount` is `Double?` with no default and the
   flow blocks. Verified on device: invalid attempts are blocked.
3. **Payment had no status guard** (audit) — only the tracking screen hiding
   the button kept an unready repair out of the flow. Fixed in the ViewModel,
   re-checked at every step. Verified on device.
4. **Customer tracking stopped updating mid-repair** (found in the first
   physical test) — the Firestore snapshot listener ended its stream on the
   first error and never re-listened, so the screen silently froze on the last
   status it had received. Fixed with a retry/reconnection policy
   (`SnapshotListenerRetry`) that re-registers the listener on recoverable
   failures with backoff from 1s to a 30s cap, and still surfaces terminal
   failures. Verified on device: the customer side now follows the technician
   through all five remaining stages without the screen being reopened.

### Final verification result

- **49/49 JVM unit tests passing**, no new test dependency
- `compileDebugKotlin`, `assembleDebug`, `compileDebugAndroidTestKotlin`,
  `testDebugUnitTest` all clean, no warnings
- **Physical end-to-end test on the Pixel 3 against the real backend: passed**

**BLOCK 7: COMPLETE.**


## Block 8 — Offline cache + design QA ⚠️ Compile-verified + 57/57 JVM unit tests (no device pass yet)

No new features, no new dependencies. Room was already wired in Block 1 with
no entities; this is the block that uses it.

### Offline (Room)

`core/data/local/FixoraDatabase.kt` holds the whole of the app's local
storage and deliberately nothing more — two tables, matching the locked
scope in CLAUDE.md ("Room caches only the service catalog and one draft
repair request. No sync queue."). Repair requests, payments, technicians and
spare-part stock are **not** cached; they stay authoritative in Firestore and
Supabase. Destructive migration is on, on purpose: both tables are
disposable, so carrying migrations for them would be ceremony over a cache.

- **Service catalog cache.** `CachingServiceRepository` is a decorator over
  the existing `FirestoreServiceRepository` — the Firestore class was not
  touched. Network first, Room second: every successful fetch overwrites the
  cache, and a failed fetch is only surfaced as an error when the cache is
  empty too. Deliberately network-first rather than cache-first, because
  prices and descriptions are what the customer is deciding on, so a stale
  catalog is a fallback and never the default. A successful fetch *replaces*
  the cache rather than merging, so a service deleted upstream stops being
  bookable offline. Cache writes are best-effort — a disk failure must not
  turn a good live read into an error. Opening a Service Detail warms the
  cache for that one service, so a service reached by deep link is covered
  as well as one reached through the list.
- **The offline state is visible, not silent.** `ServiceRepository` gained
  two `…WithSource` reads returning `CachedRead(value, fromCache)`. They have
  **default implementations** that report a live read, so the Firestore class
  needed no change and the nine call sites that don't care about provenance
  were untouched — only Catalog and Service Detail changed. Both now show a
  `OfflineNotice` strip ("Offline — showing your saved catalog") in the
  warning token. It is a notice, not an error state: the content below it is
  real, just not fresh.
- **Booking draft.** One row, fixed primary key — starting a booking for a
  different service replaces the previous draft rather than keeping a list,
  which is the "one draft" rule. `BookRepairViewModel` restores it on entry
  and then autosaves: a single debounced (400ms) collector on the ui state,
  rather than a save call sprinkled through every `on…Change`. Autosave only
  starts *after* the stored draft has been read back, so the empty initial
  state can never overwrite a draft mid-load. Device details, issue text,
  step, chosen branch and schedule slot all survive; the flow resumes on the
  step it was left on, including step 4, where entering re-runs branch
  matching with current availability rather than restoring a stale ranking.
- **Only uploaded photos are kept in a draft, and the customer is told.** A
  `content://` URI from the photo picker is readable only for the life of the
  process that was granted it, so restoring one after the app was killed
  would give a broken thumbnail and a retry that could never succeed. Photos
  that reached Supabase are restored from their public URL (`BookingImage`
  now prefers `remoteUrl` over `localUri` for the thumbnail). Photos that
  hadn't finished are dropped — and the count is stored, so the restore
  snackbar says "N photos hadn't finished uploading, so add them again"
  rather than quietly showing fewer photos than were added.
- **The draft is cleared** on a successful submit (before the navigation
  callback, so reopening the flow can't resurrect a submitted booking), when
  the customer empties the form again (leaving the row would resurrect fields
  they had deliberately cleared), and on sign-out.
- **Wiring.** `RepositoryProvider.initialize(context)` is called from
  `FixoraApp.onCreate`, which is what lets the Room-backed repositories stay
  plain `by lazy` like the rest instead of taking a Context at every call
  site. `FixoraApp` had been an empty `Application` until now.

`CachingServiceRepositoryTest` (local JVM, in-memory fake DAO, 8 tests): a
live read is labelled live and written to the cache; the catalog still loads
from the cache when the network read fails, and is labelled offline; a single
service falls back too; opening a detail warms the cache; a failed read with
an empty cache is an error rather than an empty catalog; a fetch removes
services deleted upstream; category filtering falls back too; a cached row
whose category no longer maps onto the enum is dropped rather than guessed
at. These pin down the decision logic above the DAO — they do **not** prove
Room reads and writes correctly with the radio actually off.

### Design QA — five defects found and fixed

Swept every screen from Blocks 2-7. The rebrand itself came back clean: no
teal or other pre-reconciliation colour anywhere, no user-facing "TechFix"
string left (only the `com.techfix.app` package and `applicationId`, which
stay by design so the Firebase config and Google OAuth client keep working),
no `fontFamily` override outside the design system, every icon from the
Rounded set, and every screen's Scaffold on the background token. All twelve
data-driven screens carry loading / empty / error / content.

What was actually wrong:

1. **Status chips were barely readable in light mode.** `OnStatusLight` was
   white for all three status tokens, so chip labels sat at roughly 2.3:1 on
   the success green and 2.1:1 on the warning amber — well under the 4.5:1 a
   13sp label needs, on chips that appear on Home, tracking, history and the
   staff queue. Split into `OnSuccessLight` / `OnWarningLight` /
   `OnErrorLight`, each a very dark tint of its own hue so the chip still
   reads as green/amber/red rather than going neutral. Dark mode was already
   correct and is unchanged — its status colours are lighter, and the
   near-black token sits on all three at better than 6:1.
2. **Amber and orange used as *ink* were worse still** — around 2:1 on a
   light surface. Added `accentOnSurface` / `successOnSurface` /
   `warningOnSurface` to `ExtendedColors`: darker steps of the same hues for
   light mode, and the existing fill colours reused in dark mode, where they
   already read as light text on a dark surface. Applied to the "no
   technician free" and "no compatible part" warnings on the staff
   appointment detail, the payment "receipt written but status update
   failed" line, and the "Best match" pill on the branch picker.
3. **The Book Repair step indicator used `Color.White` on `primary`.** In
   dark mode primary is a light indigo, so the step numbers and tick nearly
   vanished. Now `onPrimary`. The remaining hardcoded white/black in that
   file is scrim over camera preview and photo thumbnails, which is correct
   in both themes and was left alone.
4. **The root `Surface` used the surface token as the screen ground.** Every
   Scaffold screen sets `background` as its container, but the two auth
   screens have no Scaffold, so Login and Register were sitting on white
   (#FFFFFF) while the rest of the app sat on #F7F8FA. Fixed in
   `MainActivity`.
5. **Service Detail was the one content screen with a spinner and a hard
   cut**, and it showed the base price in the accent colour — which is both
   off-spec (accent is reserved for the action the customer is meant to take
   next, and the Book Repair button below it is that action) and, at 2.4:1,
   the least readable text on the screen. Now a skeleton in the shape of the
   real content, a crossfade into it, and the price in indigo, which is what
   the catalog card already used.

Two entries in "Known gaps" below were **stale, not defects**:
`CustomerHomeScreen` and `StaffDashboardScreen` were described as still
carrying Block 1 placeholder styling. Blocks 6 and 7 rewrote both; they are
fully on the design tokens. Removed from the list.

### Verification

- `compileDebugKotlin`, `assembleDebug`, `compileDebugAndroidTestKotlin` and
  `testDebugUnitTest` all pass clean, no warnings.
- Room's KSP processor generates `FixoraDatabase_Impl`, `ServiceCacheDao_Impl`
  and `DraftRepairRequestDao_Impl` — confirmed in `app/build/generated/ksp`,
  so the schema and DAO queries are valid, not just syntactically fine.
- **57/57 JVM unit tests passing** (49 from Blocks 5 and 7, 8 new), no new
  test dependency.
- **No on-device verification.** As instructed, on-device testing was left
  for a manual pass. See the follow-ups below for exactly what that pass has
  to cover — in particular, nothing here proves the cache actually serves the
  catalog with the connection off.

---

## Block 9 — Bottom navigation + Home redesign ⚠️ Compile-verified + 57/57 JVM unit tests (no device pass yet)

Customer-side only. The staff screen set and its navigation were not touched.

### Bottom navigation

- **`CustomerTab`** (`ui/customer/CustomerBottomNav.kt`) is the single list of
  the five top-level customer destinations — Home, Services, Book Repair,
  My Repairs, Profile — and the only thing that decides whether the bar is
  shown. A route not in that set is a drill-down (service detail, the booking
  flow, tracking, payment, a history entry) and renders full-screen with its
  own Back arrow, exactly as before.
- **One bar, hoisted to `FixoraNavHost`**, not one per screen: the `NavHost`
  now sits inside a `Scaffold` whose `bottomBar` is the M3 `NavigationBar`, so
  the bar is a single instance that stays put across a tab switch instead of
  five that animate in and out. That outer Scaffold is declared with
  `contentWindowInsets = WindowInsets(0, 0, 0, 0)` on purpose — every screen
  inside still runs its own Scaffold and handles the status bar itself, so the
  outer one contributes only the height of the bar and the bar applies the
  navigation-bar inset itself. Auth and the staff graph run through the same
  Scaffold and never show the bar.
- **Icons follow the design system**: outlined when inactive, filled when
  active. Compose ships the filled weight as `Icons.Rounded` and the outlined
  weight as `Icons.Outlined`; there is no outlined *Rounded* variant in the
  bundled Material Symbols set, so that pair is the closest available match
  rather than a second icon family. Selected item uses the primary-container
  indicator with primary text, unselected uses the secondary-text token.
- **Tab switching pops rather than stacks** (`popUpTo(HOME) { saveState }` +
  `launchSingleTop` + `restoreState`), so Back from any tab leaves for Home
  instead of walking the tabs the customer happened to visit, and each tab
  keeps its own scroll position and filters.
- **"Book" is the visible label** on the Book Repair tab — "Book Repair" does
  not fit a fifth of a phone-width bar without truncating. The full name is
  the content description TalkBack reads and the screen's own title.

### Routes

- `CustomerRoutes.PROFILE` and `CustomerRoutes.BOOK_START` are new.
- `CustomerRoutes.CATALOG` **gained an optional `category` query argument**
  (`customer/catalog?category={category}`) so a Home service tile can open the
  catalog already filtered. `catalog(categoryName)` builds it; the unfiltered
  form is still a plain `customer/catalog` and both match the one registered
  destination. An unknown category name falls back to the unfiltered catalog
  rather than throwing on `valueOf`.
- **The Book Repair tab is the catalog screen in booking mode**, not a second
  screen and not an invented "choose a device" step: a booking is always for a
  specific service, so the tab lists services under the title "Book a Repair"
  with a hint line, and a tap goes straight into the booking flow instead of
  via the service detail. `ServiceCatalogScreen` took three new parameters
  (`title`, `hint`, `showBack`), all defaulted, so the existing call site was
  unaffected.

### Home redesign

`CustomerHomeScreen` was rewritten and `CustomerHomeViewModel` with it.

- **Stat row** — Total / Active / Done for the signed-in customer. All three
  are derived from the one repair list the ViewModel already read, not stored
  separately, so a status arriving on the live Firestore listener moves the
  card and the counters together and they cannot drift apart. **Done is
  COMPLETED only**: a cancelled repair is finished but not done, and counting
  it would tell the customer work happened that didn't. Each stat shows a
  shimmer placeholder while loading and crossfades to its number.
- **"Our Services"** — a 2×2 grid over `DeviceCategory`, the four categories
  the catalog is actually built from, each tile in a different palette accent
  (indigo / orange / green / amber) with the tint as the fill and the matching
  `…OnSurface` token as the ink, per the Block 8 fill-vs-ink rule. Tapping one
  opens the catalog filtered to that category.
- **"Recent Repairs"** — the live repair if there is one, otherwise the most
  recent finished one, as a card with the device, service, status chip and
  (while active) the animated stage-progress bar and a Track repair action; a
  finished one offers View details instead. With no repairs at all it shows the
  empty state the design system asks for: image, "No repairs yet", and a Book
  Repair CTA in the accent colour.
- **Two quick links** — Track repair and My repairs. Both open screens the app
  has; nothing here links to a feature that doesn't exist. Track repair opens
  the timeline directly when something is live and the repair list otherwise.
  (There is no customer-facing Branches screen to link to — branches appear
  inside the booking flow's step 4 map — so it was not used as a quick link.)
- **Sign-out moved off Home** onto Profile, which is where the account lives.
  The behaviour is unchanged, including clearing the saved booking draft
  before signing out.
- A failed read still degrades to a caption under the stats rather than
  replacing the screen with an error, and the live-listener failure is still
  swallowed so the card keeps showing the last known status.

### Profile (new screen)

`ui/customer/profile/ProfileScreen.kt` — the account (email, role, initial
avatar), links to My repairs and Browse services, an About card carrying the
app version from `BuildConfig` and the plain statement that **payments are a
simulated demo, nothing is charged and no card details are stored**, and
sign-out. Deliberately thin: no settings toggle that isn't wired to anything.

### My Repairs

Naming a tab "My Repairs" and then showing only finished repairs would have
been wrong, so `RepairHistoryScreen` / `RepairHistoryViewModel` were widened
rather than renamed: the ui state now carries `activeRepairs` alongside the
existing terminal `repairs`, the list shows live repairs first (tapping one
opens the tracking timeline, not the history detail) with section headers only
when both halves have content, and the empty state covers "no repairs at all"
instead of "no past repairs". The read, the client-side filtering and the
index it uses are unchanged. **Flagged because it is more than the bottom bar
asked for** — the tab name is what made it necessary.

### Imagery

Five photographs bundled as local drawables in `res/drawable-nodpi`, not a
live API call: Home has to render identically offline and in the demo video,
and an unauthenticated photo API would be both a new network dependency and a
source that can change between runs. Each is downloaded pre-compressed at the
width it is actually drawn at (500–1000px); all five together are ~230KB.
Sources are listed in the KDoc of `ui/customer/HomeImagery.kt` as well as here.

All five are from **Pexels**, under the Pexels License (free to use,
commercial use allowed, attribution not required — credited for the report's
references section):

| Drawable | Used for | Title / photographer | Source |
|---|---|---|---|
| `img_repair_bench.jpg` | Home hero banner | "Smartphone repair tools on a workbench" — Fotografia Lui Vlad | https://www.pexels.com/photo/smartphone-repair-tools-on-a-workbench-31862953/ |
| `img_track_repair.jpg` | Track repair quick link | "A hand fixing an electronic device using screwdriver" — Tima Miroshnichenko | https://www.pexels.com/photo/a-hand-fixing-an-electronic-device-using-screwdriver-6755075/ |
| `img_repair_history.jpg` | My repairs quick link | "Close up of man repairing a computer" — IT services EU | https://www.pexels.com/photo/close-up-of-man-repairing-a-computer-7639374/ |
| `img_no_repairs.jpg` | "No repairs yet" empty state | "Cracked screen of a smartphone" — Towfiqu barbhuiya | https://www.pexels.com/photo/cracked-screen-of-a-smartphone-11921157/ |
| `img_technician_laptop.jpg` | Recent-repair thumbnail fallback | "Technician repairing laptop's internal components" — Jobelle Meana | https://www.pexels.com/photo/technician-repairing-laptop-s-internal-components-37489058/ |

Photos used as a backdrop for text (hero, quick links) carry a scrim gradient
so the copy holds contrast in both themes rather than fighting the picture.

### Verification

- `compileDebugKotlin` (with `--rerun-tasks`), `assembleDebug` and
  `testDebugUnitTest` all pass clean, **no warnings**.
- **57/57 JVM unit tests still passing** — no regressions. No new tests were
  added: this block is composition and navigation wiring, and the one piece of
  new logic (the Home counts) is derived state on a data class rather than a
  rule worth pinning.
- The five drawables are confirmed present in the built APK
  (`res/drawable-nodpi-v4/img_*.jpg`).
- **No on-device verification** — left for the manual pass, as instructed.
  What that pass has to cover: the bar showing on exactly the five tabs and
  nowhere else (especially that it is absent from the booking flow, tracking,
  payment and every staff screen), the outer/inner Scaffold inset handling
  leaving no double padding or content under the bar, tab state actually
  restoring, Home's stat counts against a real account, a Home tile opening a
  filtered catalog with a working Back arrow, the empty state on a fresh
  account, and a light/dark pass on Home, the bar and Profile.

---

## Block 10 — Sign In / Sign Up redesign ⚠️ Compile-verified + 66/66 JVM unit tests (no device pass yet)

**UI only.** `AuthRepository`, `FirebaseAuthRepository` and `AuthViewModel`
were not touched — not one line. Both screens still take exactly the callbacks
Block 2 gave them, so the register / sign-in / Google paths and the
`users/{uid}` bootstrap are the same code that passed Block 2's instrumented
tests.

### Layout

- **`AuthComponents.kt` (new)** holds what the two screens share, so they stay
  one design instead of two that drift: the screen frame, the brand header
  (logo, wordmark, tagline), the form card, the text field, the error banner,
  the primary button, and the "or" divider.
- Both screens are now **logo + wordmark + tagline over a single surface card**
  holding the whole form, capped at 420dp wide, rather than controls stacked
  loose down the background.
- **Fields are `OutlinedTextField` styled off the design tokens**, not the
  Material defaults: 8dp input radius, surface-variant container, border token
  when unfocused and primary when focused, secondary-text labels, a leading
  icon per field, and a show/hide toggle on the password. IME is wired —
  Next moves email → password, Go submits from the password field.
- **Sign in** gained a "Forgot password?" link, and **the link is honest about
  what it can do**: password reset does not exist in the data layer, and this
  block explicitly excluded auth logic, so tapping it reveals an inline line
  saying reset isn't part of this build and to ask a branch. Flagged here
  rather than quietly wiring `sendPasswordResetEmail`, which would have been
  exactly the change the instruction ruled out. It is a one-method addition
  whenever it is wanted.
- Secondary link between the two screens is now a sentence plus a text button
  ("New here? Create an account" / "Already have an account? Sign in").
- **No Google button on sign-up**, unchanged from Block 2: the first Google
  sign-in already creates the `users/{uid}` record, so a "Sign up with Google"
  button would be the same call under a second name.

### Inline validation

- **`AuthFormValidation.kt` (new)** — pure functions, no Android dependency:
  empty email, malformed email, empty password, and password shorter than
  Firebase's six-character minimum. Deliberately **not**
  `android.util.Patterns.EMAIL_ADDRESS`, which is a stub in a JVM unit test
  and would throw rather than match; the regex here is loose on purpose
  (catch a typo, don't be the authority on what an address is — the server
  still decides).
- **Validation state lives in the screens**, as `remember`ed local state, not
  in `AuthViewModel` — that is what keeps this a visual pass. A field's message
  appears when the field is left or the form is submitted, never while the
  value is still being typed.
- A submit that fails validation **does not call the auth layer at all**, so a
  blank or malformed form costs no network round trip and returns no raw
  Firebase string.

### Google Sign-In button

- **`ic_google_g.xml` (new)** — the official multi-colour Google "G",
  converted verbatim from Google's own SVG (Copyright (c) 2016 Google Inc.,
  the asset shipped with FirebaseUI at
  `https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg`, same
  artwork as `https://developers.google.com/identity/images/g-logo.png`). Path
  data and fill colours are unchanged; the only element dropped is the
  source's transparent bounding-box rect, which drew nothing.
- **`GoogleSignInButton.kt` (new)** is built to the Identity Services branding
  guidelines rather than approximated: light fill `#FFFFFF` / stroke `#747775`
  / text `#1F1F1F`, dark fill `#131314` / stroke `#8E918F` / text `#E3E3E3`,
  Android padding 12dp before the logo, 10dp between logo and text, 12dp
  after, 14sp Medium at 20sp line height, 20dp logo, 40dp height, 4dp
  (rectangular variant) corner radius.
- **Two deliberate deviations from the Fixora design system, both because the
  brand spec owns this control**: the button does not use the surface or
  primary tokens in either theme, and its text is the platform default
  (Roboto Medium) rather than bundled Inter — Google Sans Medium, which the
  spec names, is not redistributable. The corner radius is Google's 4dp, not
  Fixora's 8dp button radius. Everything else on both screens stays on the
  design system.
- No new dependency: `play-services-auth`'s legacy `SignInButton` widget was
  not pulled in for this; the app only has Credential Manager + googleid,
  which is what Block 2 signs in with.

### Motion

- **Entrance**: the whole form fades in with a short upward slide, 260ms, one
  movement rather than a per-row stagger (fussy at this size).
- **Press**: `Modifier.pressScale` (new, in `core/designsystem/PressScale.kt`)
  — the design system's 0.96x scale-down, applied through `graphicsLayer` so a
  pressed button doesn't re-measure and shove its neighbours. Used by the
  primary button and the Google button, and available to the rest of the app.
- **Loading**: the primary button swaps its label for a spinner in place, so
  the control that was tapped is the one showing progress. Every field, link
  and the Google button disable while a call is in flight. The old separate
  `CircularProgressIndicator` below the form is gone.
- **Error**: a form-level failure from Firebase shows as an inline strip in
  the error token, expanding in over 180ms, plus a **280ms four-swing shake**
  (`Modifier.shake`) on the rejected fields and the strip. No dialog, nothing
  to dismiss — the customer can fix the field the message names. A failed
  local validation shakes the same way without any call being made.
- Every animation is inside the design system's 300ms ceiling.

### Verification

- `compileDebugKotlin --rerun-tasks`, `assembleDebug` and `testDebugUnitTest`
  all pass clean, **no warnings**.
- **66/66 JVM unit tests passing** — the 57 from earlier blocks plus 9 new
  `AuthFormValidationTest` cases: empty vs malformed email get different
  messages, addresses with no `@`/no domain/leading text are rejected,
  subdomain and plus-tag addresses pass, surrounding whitespace doesn't
  invalidate an address, empty vs short password get different messages, a
  password exactly at the minimum passes, spaces count towards password length
  (they do in Firebase), and the form is submittable only when both pass.
- The G-logo vector parses at build time (an invalid `pathData` fails
  `aapt`), and the drawable is packaged in the APK.
- **No on-device verification** — left for the manual pass, as instructed.
  What that pass has to cover: both screens in **light and dark** (the Google
  button in particular, since it is the one control that does not follow the
  app palette, and the dark-mode `#131314` fill against Fixora's `#0F1115`
  background), the entrance/press/shake motion at real frame rates, the
  inline messages appearing on blur rather than mid-typing, the keyboard
  Next/Go wiring, the password visibility toggle, and a real failed sign-in
  (wrong password) showing the banner + shake rather than the old plain line.

---

## Block 11+12 — Profile, technician CRUD, staff rebuild, animation pass ⚠️ Compile-verified only (no device pass)

- **Customer Profile:** shows name, email, optional phone, and role; name and phone are editable through the existing Firebase auth repository and Firestore `users/{uid}` record. Added a persisted light/dark theme toggle here, plus the existing repair links and sign-out action.
- **Admin Technician CRUD:** upgraded the technician tab to an admin-managed roster. Create and edit use the same form with branch selection, multi-select device skills, availability, and required-name/skill validation. Writes now use Firestore through the existing `TechnicianRepository`; delete requires confirmation. Non-admin staff retain the read-only roster.
- **Staff navigation and visual rebuild:** added a role-appropriate Material3 `NavigationBar` for Dashboard, Queue/My repairs, and Technicians. Dashboard count tiles, queue cards/summary, appointment detail cards, inventory cards, empty/error/loading states, and technician cards now use the customer-side surface hierarchy, icon containers, status chips, and consistent card spacing.
- **Animation pass:** added splash-to-app crossfade, 200ms navigation fade-through, booking step crossfade, photo collection/add/remove motion, success-pane entrance, payment processing pulse/progress, and queue-item entrance motion. Existing repair status chip/timeline animation and screen skeleton crossfades remain in place. The technician form uses a Material3 bottom sheet and delete uses a rounded confirmation dialog.
- **Technician backend transition:** complete. The runtime repository is Firestore-only, the old Supabase write repository was removed, and the undeployed technician RLS/custom-claims design is obsolete and must not be applied.
- **Verification:** `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest` pass. The JVM suite is 71/71 after replacing the obsolete Supabase persistence test with focused Firestore checks. No device was connected for the current pass.
- **Confirmed-persistence refresh fix:** Firestore create/update perform a server re-read and verify the stable document id and all editable fields, including both Boolean availability values. The ViewModel re-reads the selected branch and only closes the form or shows success after that read succeeds; failures keep the form open with a short message.

## Obsolete Firebase-JWT technician attempt removed — 2026-08-23

- Firebase Cloud Functions/custom claims were rejected because the project must remain on the free Firebase plan. No Function was deployed, no claim backfill ran, and no Firebase-JWT technician or Storage SQL migration was applied live.
- Deleted the complete local Functions implementation and removed the Functions deployment block from `firebase.json`.
- Removed custom-claim waiting from Firebase login and removed the Firebase ID-token `accessToken` bridge from the shared Supabase client. Firebase Authentication once again depends only on Firebase Auth plus the Firestore `users/{uid}` record.
- The undeployed `technician_admin_rls.sql` migration was removed; `storage_setup.sql` is retained only as a non-executable obsolete notice. Supabase technician data and live repair-image policies were not modified.
- Active architecture: Firebase Authentication + Firestore + Firestore Security Rules for technician CRUD. No Functions, custom claims, or Firebase-to-Supabase token bridge is required.
- Cleanup verification passed: Firebase email/password authentication plus the ADMIN Firestore role read succeeded against the live project; Firestore rules compiled in the local emulator with JDK 22; Android Kotlin compilation, 70/70 JVM tests, and the 29 MB debug APK build passed.
- No physical device was connected during cleanup, so UI/device verification remained pending at that point.

## Firestore technician migration — 2026-08-23 ✅ Live migration verified

- Deployed secure `technicians/{technicianId}` Firestore rules: every authenticated application role can read for branch matching/assignment; only a user whose existing `users/{uid}.role` is `ADMIN` can create, update, or delete. The rules validate the document id, branch, skills, availability, timestamps, and allowed field set.
- Added `FirestoreTechnicianRepository` behind the unchanged `TechnicianRepository` interface. Create/update perform a server re-read and verify every technician field; delete confirms the server document is absent. `RepositoryProvider` now uses it and the obsolete Supabase write implementation was removed.
- Added the explicit local migration utility `scripts/migrate_supabase_technicians_to_firestore.sh`. It requires exactly the six validated Supabase rows, preserves each UUID, authenticates as the configured Firebase ADMIN, skips exact matches, reports conflicts without overwriting, never deletes Supabase data, and independently re-reads all six Firestore documents before returning success.
- Live migration result: `migrated=6 skipped=0 conflicted=0 failed=0 verified=6 source=6`. Two later idempotency/integrity passes each returned `migrated=0 skipped=6 conflicted=0 failed=0 verified=6 source=6`.
- Preserved UUIDs: `44b87a2d-417f-4947-a8c9-ceeacafeaca1`, `a369866b-1bec-4ce2-b6a0-ed0e4b74dcd8`, `f3181e79-2aa4-40f2-be4e-7fe0159bd8e9`, `ebacb5ef-2c26-4cd8-9a5d-5891d5bef933`, `c5228f45-8daa-48f1-bee1-e87245bea225`, and `f492bb78-edbb-43ac-9ba3-1f4138b04bb5`. Name, branch, skills, and Boolean availability matched the Supabase source for every document.
- Live disposable CRUD/rules test passed: Admin create, read, both availability directions, name/branch/skills edit, and delete succeeded; Branch Manager, Technician, and Customer reads succeeded while create/update/delete were denied; anonymous read was denied. Temporary records were deleted.
- Final verification: Firebase ADMIN email/password sign-in and role read succeeded during the live test; Kotlin and instrumentation-test compilation passed; 71/71 JVM tests passed; `app-debug.apk` (29 MB) and the debug Android-test APK built successfully. `adb devices` returned no connected devices, so physical-device/UI verification is pending.

## Infrastructure state

| Service | Status |
|---|---|
| Firebase Auth | Live. Email/password + Google enabled. |
| Firestore | Live, `(default)` database. Technician rules are deployed and all six migrated documents are verified. Runtime technician CRUD uses Firestore. |
| Firebase Storage | **Not used** — replaced by Supabase Storage. |
| Supabase Postgres | Live. `technicians`, `spare_parts`, and `spare_part_stock` remain intact. Technician rows are an untouched migration archive; no technician JWT/RLS migration was applied. Stock still uses the legacy Block 7 policy. |
| Supabase Storage | Live. `repair-images` bucket + existing policies are unchanged; upload was confirmed on device 2026-08-21. The abandoned Firebase-JWT policy draft was never applied. |
| Google Maps SDK | Key in `local.properties`, injected via manifest placeholder + `BuildConfig` (Block 5). Rendering **not yet confirmed on device**. |
| Room | Live in code (Block 8). `fixora.db`, two tables: `cached_services` (catalog read cache) and `draft_repair_request` (one in-progress booking). Nothing else is cached locally. **Not yet exercised on a device.** |

Secrets live in `local.properties` (gitignored) and reach code through
`BuildConfig`. Only the Supabase anon key ships in the app; the service_role
and secret keys are deliberately not in this repo.

---

## Not started

- **Demo video and report.** Everything in the build order is now written;
  what remains is the manual device pass listed below, then recording and
  writing up.

---

## Known gaps / follow-ups

- Block 6 has **no on-device verification** — the Pixel 3 was not connected
  this session (`adb devices` empty), so the instruction to test on the
  physical device could not be carried out. What needs an on-device pass:
  the Firestore listener actually pushing a status change into the timeline
  (flip `status` in the Firebase console and watch the line and chip animate
  without touching the app), the active-repair card on Home tracking that
  same change, history showing a repair only once it is COMPLETED or
  CANCELLED, the empty-history state on a fresh account, Supabase image URLs
  loading in the history gallery over a real connection, and a light/dark
  pass on all three new screens.
- ~~Nothing writes a status past SUBMITTED yet~~ — done in Block 7: the staff
  Appointment Detail screen advances the timeline, and payment moves it to
  COMPLETED.
- ~~`RepairRequest` has no `completedAt` field~~ — added in Block 7.
- Google Sign-In run on the Pixel 3 (2026-08-21): fails with
  `GetCredentialException: During begin sign in, failure response from one
  tap: 16: [28439] User disabled the feature.` Root cause confirmed against
  matching reports (Google Issue Tracker #300063577, MetaMask Mobile #19149)
  — this is the signed-in Google account's "Sign-in prompts" setting being
  off, not an app bug. Fix is account-side: on the test device, Settings →
  Google → Manage your Google Account → Security → turn "Sign-in prompts"
  back on (or https://myaccount.google.com on that account). App code
  (`setFilterByAuthorizedAccounts(false)`) is already correct; nothing to
  change there. Once re-enabled, re-run to confirm the happy path, and
  separately still confirm the release SHA-1 gap below.
  Code change made in response: `signInWithGoogle` now maps Credential
  Manager failures (cancelled / no account on device / sign-in prompts
  disabled) to a `GoogleSignInUnavailableException` carrying a
  display-ready message, so the login screen shows plain guidance instead
  of the raw `16: [28439] ...` Play Services string. Compile-verified only
  — the mapped messages have not been seen on device yet.
- Password reset has no implementation. The sign-in screen shows a "Forgot
  password?" link that says so plainly rather than pretending to send an
  email (Block 10 was a UI pass with the auth layer out of scope). Closing it
  is one repository method (`sendPasswordResetEmail`) plus a screen state.
- The release SHA-1 is not registered — Google Sign-In will fail in a release
  build until it is.
- The new branding and design tokens need an on-device light/dark check once
  the Pixel 3 is reconnected.
- The design system spec lives in `CLAUDE.md`, not `docs/DESIGN_SYSTEM.md`.
  Worth moving if the docs folder is meant to be the single source.
- ~~Technician CRUD still points at Supabase~~ — resolved 2026-08-23. Runtime
  CRUD uses Firestore and deployed rules enforce ADMIN-only writes. The
  removed technician RLS draft must not be recreated or applied.
- No `spare_part_stock` foreign key to `branches` — `branch_id` is a plain
  text column ('colombo'/'galle') since the branch records live in
  Firestore, not Postgres, so Postgres can't enforce it referentially.
- `SEED_ADMIN_EMAIL`/`SEED_ADMIN_PASSWORD` in `local.properties` are a
  throwaway test-only account, not a real staff login — don't reuse it as
  a demo admin in the report/video without changing the password first.
- Do not run `docs/supabase/storage_setup.sql`; it is an obsolete Firebase-JWT
  draft. The live `repair-images` bucket and existing policies are unchanged.
- Block 4 has zero device verification (no Pixel 3 connected this session).
  First on-device pass should cover: catalog search/filter against real
  Firestore data, camera permission prompt + capture, gallery multi-select,
  a failed-upload retry, and confirming compressed image sizes are
  reasonable over a real connection.
- ~~Book Repair's draft is lost if the app process dies mid-booking~~ — done
  in Block 8. One caveat carried forward rather than hidden: photos that had
  not finished uploading are **not** restored (their `content://` permission
  dies with the process), and the restore snackbar says so.
- Block 5 has **no on-device verification yet** — the Pixel 3 was not
  connected in time this session. What still needs an on-device pass: the map
  actually rendering (a wrong or unrestricted key shows a blank grey tile, not
  an error), the location permission prompt and the denied path, a real GPS
  fix producing sensible distances, and a real Firestore write on confirm.
  The matching rule itself is covered by the 8 JVM tests, so a device pass is
  about the map, GPS, and the write — not the scoring.
- The Maps API key was pasted into a chat transcript while being set up, and
  as of this session it is **not restricted** in Google Cloud Console. Restrict
  it to Android apps with package `com.techfix.app` and the debug SHA-1 (and
  the release SHA-1 once that exists), or rotate it. An unrestricted browser-
  usable Maps key is billable by anyone who finds it.
- The map is embedded inside a `LazyColumn` on step 4, so a vertical drag that
  starts on the map pans the map rather than scrolling the list. Standard for
  an embedded map, but worth a look during the device pass in case it feels
  like the page is stuck.
- `MatchBranchesUseCase` scoring weights (0.45 technician / 0.35 parts / 0.20
  distance, 30 km half-score) are a judgement call, not derived from the
  brief. They are named constants on the companion object so they are easy to
  point at in the report and easy to defend or change in the viva.
- ~~**Block 7 has no on-device verification**~~ — **done 2026-08-22.** The
  full assign → advance → pay chain was run on the physical Pixel 3 against
  the real backend and passed; see the Block 7 verification section above for
  what was covered and the live-data corroboration. Setup that it depended on,
  also done: `staff_write_policies.sql` applied and verified
  (`spare_part_stock` writable, `technicians` / `spare_parts` still blocked),
  and eight staff logins created — a `BRANCH_MANAGER` and three `TECHNICIAN`
  accounts for each of `colombo` and `galle`, every technician linked to a
  stable technician UUID now used as the Firestore document id, credentials in `staff-accounts.local.md`
  (gitignored).
  Two things the pass did **not** cover, carried forward rather than assumed:
  a successful **card** payment (the receipt on record is cash on pickup), and
  a light/dark check on the five new Block 7 screens.
- A Technician login has to be linked to a Firestore `technicians` document id by hand
  for "assigned to me" to mean anything. That mapping is a deliberate
  simplification: technicians and auth metadata use separate Firestore
  documents, and nothing generates the link. Worth naming in the report as a known seam
  rather than hiding it — the fallback (show the whole branch) keeps the app
  usable without it.
- `getAllRepairRequests()` is an unfiltered collection read, and the Firestore
  `isStaff()` rule does a `get()` on `users/{uid}` per document evaluated. Fine
  at coursework scale (a handful of documents); it would need a denormalized
  authorization strategy or a status-scoped query before it was anything but.
- Stock edits are gated in the app, not in Postgres — see the Supabase policy
  note above. Anyone holding the anon key could write `spare_part_stock`
  directly.
- The payment "processing" step is a fixed 1.8s delay and the decline is a
  switch on the form. Both are deliberate and labelled, but they are the two
  places a viva question is most likely to land, so they're worth being ready
  to defend as simulation rather than pretending otherwise.
- `Payment.createdAt` is written as a Firestore server timestamp but the
  receipt pane shows the local clock for the one it just created (it doesn't
  re-read the document). The two could differ by the round-trip; it only
  affects the "Paid on" line immediately after paying, and re-opening the
  receipt shows the server value.
- **Block 8 has no on-device verification, and one item in it cannot be
  checked any other way: whether the offline cache actually serves the
  catalog with the connection off.** The JVM tests cover the fallback
  decision logic above the DAO; they say nothing about Room on a real device.
  The airplane-mode pass needs to be, in this order: open Catalog online once
  so the cache is written, kill the app, turn on airplane mode, reopen —
  Catalog should list the same services with the offline strip showing, and
  opening one should still reach Service Detail. Then confirm a **cold**
  install with no connection still shows the error state with a retry, not an
  empty list, since that is the branch that distinguishes "no cache" from
  "empty catalog".
- The draft cache needs its own device pass: fill in steps 1-3, force-stop
  the app from Settings (not just backgrounding it, which does not kill the
  ViewModel), reopen the same service, and confirm the fields, the step, and
  the uploaded photos come back — and that submitting then reopening the flow
  starts clean rather than resurrecting the submitted booking.
- The five design fixes in Block 8 are **compile-verified only**. Four of them
  are colour-contrast changes whose whole point is how they look, so they
  want an eye on a real screen in both themes — particularly the status chips
  (Home, tracking, history, staff queue) in **light** mode, and the Book
  Repair step indicator in **dark** mode.
- Room is on `fallbackToDestructiveMigration`. Correct for a cache and a
  disposable draft, but it means any future schema change silently drops both
  tables. Worth one sentence in the report rather than leaving it to be found.
- The offline notice is only on Catalog and Service Detail, because those are
  the only screens with a cache behind them. Every other screen still shows
  its normal error state with no connection, which is honest — there is no
  saved data to offer — but it does mean the app has two different
  no-connection appearances depending on the screen. Deliberate, not an
  oversight.

## Premium Customer Profile redesign — 2026-08-23 ⚠️ Code-verified, device pass pending

- Replaced the combined read/edit card with a dedicated premium Profile tab
  and a drill-down Edit Profile screen. The profile now has a restrained
  header, image-led identity card, grouped account/quick-action/preferences/
  support sections, an About sheet, and a destructive sign-out treatment.
  Unsupported notification, password, location, and help actions were not
  invented.
- Edit Profile now has a minimal app bar with Back/Save, a centered photo
  section, one personal-information surface, design-system-styled name and
  phone fields, read-only Firebase email verification status, disabled Save
  when unchanged, inline validation, in-button saving, duplicate-submit
  prevention, and a discard confirmation for Android Back or the app-bar Back
  action when information is unsaved.
- Profile images use the existing live Supabase `repair-images` bucket and its
  one-folder JPEG convention: `<firebaseUid>/profile_<uuid>.jpg`. CameraX and
  the Android Photo Picker both feed the existing `ImageCompressor`, then the
  existing `ImageUploadRepository` abstraction. The visible image changes
  only after the immutable upload and the Firestore `users/{uid}.photoUrl`
  update both succeed; failure keeps the previous image. Coil supplies crop,
  loading, provider-photo/initials fallback, and decode-error fallback.
- The Supabase Android client remains anonymous by the locked architecture.
  Storage objects are therefore immutable and public-read, while the active
  profile-photo pointer is owner-only in Firestore. “Remove photo” deletes
  that pointer (and restores a Firebase provider photo or initials); it does
  not grant anonymous Storage delete access, so superseded objects remain in
  the bucket. No new dependency, custom claim, Function, or Blaze service was
  introduced.
- `AuthUser` and the existing auth repository were extended rather than
  duplicated: refresh, email-verification/provider-photo resolution, custom
  photo state, profile-field update, and photo-pointer update all stay behind
  `AuthRepository`. Name/phone changes continue to preserve uid, email, role,
  branchId, and technicianId.
- Firestore self-update rules are deployed live. A user may change only
  `name`, `phone`, `photoUrl`, and server `updatedAt`; role and every staff
  authorization field are excluded by an affected-field allowlist. The local
  Auth + Firestore emulator suite passed owner name/phone update, photo set and
  removal, role-change denial, branch/technician-field denial, and cross-user
  update denial.
- Verification: `testDebugUnitTest` passes **86/86**, including new focused
  profile validation/change-state and Supabase object-path tests;
  `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `assembleDebug`,
  `assembleDebugAndroidTest`, and `lintDebug` pass. The debug APK is 31 MB.
- `adb devices -l` returned no connected device. Camera/gallery selection,
  live Supabase profile upload, restart persistence, visual light/dark QA,
  touch/keyboard behavior, and the unsaved-change dialog remain pending on a
  physical Android device. Build and automated verification passed.
  Physical-device testing remains pending.

## Secure Admin Inventory Management — 2026-08-23 ⚠️ Code-verified, backend deployment pending

- Added a dedicated Admin Inventory destination while preserving the existing
  read-only parts view for Branch Managers and Technicians. The Admin screen
  includes actual-data metric cards, branch/category/status filters, search,
  quantity/name/date sorting, item details, create/edit, restore/archive, and
  a validated stock-adjustment sheet.
- Inventory status is derived from the stored quantity and configured minimum:
  in stock, low stock, out of stock, or unavailable. Inventory value is shown
  only when every active item has a real unit cost. Recent activity renders
  only persisted adjustment rows.
- Added the Supabase migration
  `supabase/migrations/20260823193000_admin_inventory_management.sql` for
  private item metadata, safe availability/archive fields, and an immutable
  adjustment history. Stock changes run in a transaction, reject negative or
  excessive results, and use a unique request id for retry idempotency.
- Added the `inventory-admin` Supabase Edge Function. It receives the Firebase
  ID token, uses it to read the caller's own Firestore `users/{uid}` document
  through the deployed Security Rules, requires `role == ADMIN`, and only then
  calls service-role-only Postgres RPCs. No Supabase service credential is
  present in Android, no RLS bypass exists in the client, and no anonymous
  INSERT/UPDATE/DELETE policy was added.
- Android uses a new `AdminInventoryRepository` boundary backed by the Edge
  Function. The old `SparePartRepository` remains the public read path used by
  branch matching and non-Admin staff. Duplicate taps are stopped by an atomic
  submission gate, and backend requests also use idempotency ids.
- Verification: 102/102 JVM tests pass, including inventory metric, threshold,
  filter/sort, form, adjustment-boundary, permission, and duplicate-submission
  coverage. Eight Node security-contract tests pass for missing/invalid tokens,
  Admin acceptance, Branch Manager/Technician/Customer rejection, removal of
  broad stock policies, and service-role-only RPC grants. The existing 17
  Firebase emulator assertions still pass. `compileDebugKotlin`, `lintDebug`,
  `assembleDebug`, and `assembleDebugAndroidTest` pass.
- The migration and Edge Function are **not deployed**: this workstation has
  no Supabase access token (`supabase projects list` returns
  `LegacyPlatformAuthRequiredError`). Until they are deployed together, the
  live Admin screen will fail safely and no insecure fallback is enabled.
  No Android device is connected, so live CRUD, both-theme visual QA, and
  process-restart persistence remain pending.

## Technician roster replacement and assignment hardening — 2026-08-23 ✅ Live migration verified

- Replaced the active roster with Kasun, Kavishka, and Ravidu in Colombo and
  Tharusha, Rivini, and Nethmi in Galle. Each new Firebase Auth account has an
  exact reciprocal `users/{uid}` → `technicians/{technicianId}` link, matching
  branch, `TECHNICIAN` role, active status, availability, and supported device
  skills. No passwords or credentials are stored in the application.
- Archived all six old technician documents (`active=false`,
  `available=false`, timestamped `archivedAt`) instead of deleting them.
  Seven legacy `users` documents were retained for audit/history, four old
  enabled Auth accounts were disabled without deletion, and the completed
  Nuwan repair remains linked to the archived Nuwan technician ID.
- The one `APPROVED` Colombo repair was reassigned to new Kasun only after all
  six account links had been verified. A later live operational update moved
  that repair to `READY_FOR_PICKUP` and Kavishka; the newer workflow state was
  preserved rather than overwritten during final audit.
- Assignment now requires an active, available, same-branch technician with
  the repair category skill and a valid reciprocal login link. The app checks
  the candidate from the Firestore server before assignment, writes the exact
  stable technician ID, re-reads from the server, and reports success only
  after exact persistence is confirmed. Technician repair reads use only the
  linked stable ID and server data—never name or branch-only matching.
- Technician login fails closed unless Auth → user → technician linkage,
  role, branch, active state, and IDs all match. Admin technician branch edits
  update both linked documents atomically. Archived technicians remain
  resolvable for historical staff views but cannot appear in assignment lists.
- The Admin Technician Management cards now show initials, name, linked email,
  branch, skills, availability, account-link state, assigned-repair count, and
  premium status badges. Admin edit/archive actions preserve history;
  permanent technician deletion is blocked.
- The final Firestore rules are deployed to `techfix-mobile-app`. **31/31**
  Auth/Firestore emulator assertions pass, including reciprocal-link branch
  transactions, assignment eligibility, Branch Manager scope, technician
  self-assignment denial, cross-technician read denial, protected user fields,
  and permanent-delete denial.
- Verification: **109/109** JVM tests pass; `compileDebugAndroidTestKotlin`,
  `lintDebug` (0 issues), `assembleDebug`, and `assembleDebugAndroidTest` pass.
  The reversible live suite passed all six logins and links, Admin and Branch
  Manager assignments, wrong-branch/wrong-skill/unavailable/unlinked
  exclusions, exact-ID visibility isolation, logout/login refresh, and
  persistence. Cleanup was confirmed with zero disposable test artifacts.
- No Android device was connected. Physical UI/theme/accessibility checks and
  six-account end-to-end interaction on an installed app remain pending.
