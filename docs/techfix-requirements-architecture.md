# TechFix — Requirements and Architecture (Final Scope, 2-Day Build)

Status: scope and architecture locked. Ready for implementation on your go-ahead.

## 1. Understanding of the Assignment

TechFix is a native Android app for a fictional repair company with two branches, Colombo and Galle. The official brief requires customer accounts, service search, appointment booking, repair tracking, repair history, and staff-side handling of branches, technicians, spare parts, and payments, including logic that assigns a repair to a suitable branch based on technician and spare-part availability, not just distance.

The brief lists five optional deliverable areas: GPS/Maps, Web Services/Remote Data, Complex Data Model and Adaptors, Camera/Image Integration, and SQLite/Content Providers/Offline. All five stay in scope, they're what earns the higher marking band and none of them are cut below, only simplified in how much UI wraps around them.

Deliverables: demo video under 5 minutes, a written report, a GitHub repo.

**Real constraint that governs everything below: 2 days.**

## 2. Mandatory vs Confirmed vs Cut

**Mandatory (from the official brief), all still in:**
- Customer registration and login
- Search and browse repair services
- Submit a repair appointment
- Track repair status
- View repair history
- Branch, technician, and spare-part handling
- Payment handling
- Branch assignment logic based on technician and part availability

**Locked decisions:**
- Native Android, Kotlin, SDK 35
- Firebase Authentication for everyone (customers and staff)
- Firebase Authentication and Firestore for application data, including technician CRUD; Supabase remains for spare-part data and repair-image Storage
- Admin, Branch Manager, and Technician kept as separate roles in the data model, but built as one shared staff screen set filtered by role
- GPS-based branch matching, simulated payment, camera/multi-image, light and dark themes

**Cut for time, not for the grade:**
- No offline sync queue, only a read cache (service catalog) and a draft repair request survive a dropped connection
- No separate screen set per staff role, one dynamic set instead
- No elaborate management CRUD (create/edit forms for services, pricing, etc. kept minimal, list + status update only)
- No notifications, no analytics screens

## 3. Actors

- **Customer** — books repairs, tracks status, views history, pays (simulated)
- **Technician** — sees assigned jobs, updates repair status
- **Branch Manager** — approves appointments, assigns technicians and branches
- **Admin** — manages services, pricing, spare parts
- **System (branch-matching logic)** — the core requirement, not a human actor

All three staff roles share one screen set (Staff Dashboard, Appointment Queue, Appointment Detail/Assignment, Technician List, Spare Parts List). What each role can see or do on those screens is controlled by a permission flag stored with the user, not by separate screens. This keeps the three-role requirement real in the data model and the report, without tripling the UI work.

## 4. Feature Modules

1. Auth and Profile
2. Service Catalog
3. Repair Booking (device details, issue, images, date/time, GPS branch match, review, submit)
4. Repair Tracking (live status timeline)
5. Repair History
6. Payments (simulated)
7. Staff (shared, role-gated: appointment review, branch/technician assignment, spare-part status)

## 5. Final Screen List (16 screens)

**Customer (11):** Login/Register, Home, Service Catalog, Service Detail, Book Repair (single multi-step flow: device, issue, photos, location/branch, review), Repair Tracking Detail, Repair History List, Payment Summary/Method/Processing/Result (one flow), Profile/Settings (theme toggle here too).

**Staff, shared across roles (5):** Staff Login, Staff Dashboard, Appointment Queue, Appointment Detail/Assignment, Technician & Spare Parts List (combined single screen with two tabs).

## 6. Architecture

Layered, MVVM:
- **Presentation** — screens, ViewModels, UI state
- **Domain** — use cases holding the actual rules, branch-matching logic lives here, not inside a ViewModel or a Firebase call
- **Data** — repositories hiding whether data comes from Firebase, Supabase, or the local Room cache, so the UI layer never talks to a specific backend directly

Keeping the matching logic in its own use case matters here specifically because it's the one piece of business logic being graded closely, it should be easy to point to in the report and the viva.

## 7. Firebase / Supabase / SQLite Split (Final)

**Firebase:**
- Authentication for everyone (email/password and Google Sign-In)
- Firestore: users, branches, services, categories, pricing, repair requests, repair status, payment records, and technicians
- Firestore Security Rules authorize technician writes from the trusted `users/{uid}.role`; no Cloud Functions, custom claims, or billing upgrade is required

**Supabase, two roles:**
- Postgres, scoped narrowly: spare parts and spare-part stock. The six original technician rows remain untouched as a read-only migration archive after their stable ids were copied to Firestore.
- Storage: repair images (moved off Firebase Storage — decided 2026-08-21, kept both file-storage needs on one non-Firebase SDK instead of splitting them)

**SQLite (Room), scoped narrowly:**
- Cached service catalog for offline browsing
- One draft repair request (in progress, including picked images) so a booking survives a dropped connection

Technicians were moved to Firestore in a verified one-time migration; Supabase copies were not deleted. At runtime, technician availability comes from Firestore and spare-part availability remains in Supabase.

## 8. GPS Branch-Selection Logic

1. Get device location, fallback if permission denied
2. Compute distance to Colombo and Galle
3. Query Firestore for each branch's available technicians and Supabase for required spare-part stock
4. Score branches on distance plus availability, not distance alone
5. If neither branch can currently handle it, show the one with the shorter wait instead of a dead end

## 9. Simulated Payment Flow

Repair Cost → Payment Summary → Method → Demo Payment Details (format-validated, never charged) → Processing (short delay) → Success/Failure → Receipt written to Firestore. UI clearly labeled as a demo payment throughout.

## 10. Camera and Image Flow

CameraX for capture, system Photo Picker for gallery, multiple images with preview and remove, client-side compression, upload to Supabase Storage, URL stored on the repair request (Firestore). Queued locally if offline when a photo is added.

## 11. Design System (minimum viable, not screen-by-screen)

Defined once at the start of Day 1, reused everywhere:
- Primary blue-teal, one accent color, explicit success/warning/error colors, separate light and dark palettes
- One typography scale, one font family
- 8dp spacing grid, consistent corner radius
- A handful of reusable components: status chip, repair card, branch card, timeline component, bottom nav, top app bar with search

## 12. Two-Day Build Order

**Day 1**

- **Block 1, setup:** project scaffold, Gradle deps (Firebase, Supabase client, Room), define the color/type tokens once, base navigation and role-based routing skeleton
- **Block 2, auth:** Firebase register/login/logout, role field on the user record, route to customer flow or staff flow after login
- **Block 3, data + seed:** Firestore collections (services, branches, repairRequests, payments, technicians) and Supabase tables (spare_parts, spare_part_stock), with seed rows for both branches
- **Block 4, catalog + booking start:** Service Catalog, Service Detail, start of Book Repair (device details, issue, camera/gallery image picker)

**Day 2**

- **Block 5, GPS + matching + submit:** location fetch, distance calc, Firestore technician plus Supabase parts availability queries, scoring, branch picker screen, finish and submit the repair request to Firestore
- **Block 6, tracking + history:** Repair Tracking Detail with a Firestore real-time listener on status, Repair History list
- **Block 7, payment + staff:** simulated payment flow end to end, shared Staff screen set (queue, assignment, technician/spare-parts view) gated by role
- **Block 8, offline cache + QA + demo:** Room cache for catalog and draft repair, dark/light pass across all screens, record the under-5-minute demo, write the architecture section of the report

If block 8 runs out of room, cut staff-side polish first, not customer-side. The customer flow and the matching logic are what carry the marks.

## 13. What Happens Next

Scope is locked. Say the word and implementation starts at Block 1.
