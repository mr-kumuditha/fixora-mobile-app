# TechFix — Requirement Analysis and Final Project Scope

This builds on the architecture doc already agreed. It's written to drop straight into the report's Requirement Analysis section.

## 1. Problem Statement

TechFix operates two repair branches, Colombo and Galle, handling phone, laptop, desktop, and tablet repairs. Customers currently have no dedicated way to check which branch can actually take on a given repair, book it, and follow its progress. Matching a request to a branch that has both a free technician and the right spare part in stock is done manually, which slows things down and leads to jobs being sent to a branch that can't complete them. TechFix needs a mobile system that lets customers book and track repairs, and lets staff manage the repair pipeline across both branches from one tool.

## 2. Project Objectives

- Let customers search services, submit a repair request with photos, and get matched to the branch that can actually handle it
- Base branch matching on live technician and spare-part availability, not distance alone
- Give customers a real-time view of repair progress
- Give Admin, Branch Manager, and Technician a shared, role-aware tool to review, assign, and update repairs
- Provide a realistic simulated payment flow tied to repair completion
- Keep catalog browsing and an in-progress booking usable without a live connection

## 3. Final Project Scope

**In scope:**
- Customer registration, login, profile
- Service catalog with search and filtering
- Repair booking with camera/gallery images, GPS-based branch matching, date/time selection
- Real-time repair tracking
- Repair history
- Simulated payment flow
- Shared staff tool covering appointment review, branch/technician assignment, spare-part status, gated by role
- Local caching of the service catalog and one in-progress draft booking for offline use

**Out of scope for this build:**
- Real payment processing
- Push notifications
- Multi-language support
- Reporting/analytics dashboards
- Branches beyond Colombo and Galle
- Full offline sync queue (only the catalog cache and one draft survive being offline, nothing else is queued for later sync)

## 4. Actors

| Actor | Role |
|---|---|
| Customer | Books repairs, tracks status, views history, pays (simulated) |
| Technician | Views assigned jobs, updates repair status |
| Branch Manager | Reviews appointments, assigns branch/technician |
| Admin | Manages services, pricing, spare-part stock |

Admin, Branch Manager, and Technician are distinct roles at the data and permission level, sharing one screen set that adapts to the logged-in role.

## 5. Functional Requirements

**Authentication**
- FR-1: The system shall let a customer register with email and password.
- FR-2: The system shall let any user log in through Firebase Authentication.
- FR-3: The system shall route a logged-in user to the customer flow or the staff flow based on their role.
- FR-4: The system shall let a user reset a forgotten password.
- FR-5: The system shall let a user log out.

**Service Catalog**
- FR-6: The system shall let a customer search and filter repair services by category.
- FR-7: The system shall show service detail including estimated pricing.

**Repair Booking**
- FR-8: The system shall let a customer select device category, brand, and model.
- FR-9: The system shall let a customer describe the issue in free text.
- FR-10: The system shall let a customer attach one or more photos by camera or gallery.
- FR-11: The system shall let a customer use their current location to find a matching branch.
- FR-12: The system shall select the best branch using distance combined with technician and spare-part availability, not distance alone.
- FR-13: The system shall let a customer pick a preferred date and time.
- FR-14: The system shall let a customer review and submit the completed request.

**Repair Tracking**
- FR-15: The system shall show the current repair stage in real time, from submission through collection.

**Repair History**
- FR-16: The system shall let a customer view past repairs with status, cost, date, device details, and images.

**Payment**
- FR-17: The system shall let a customer select a demo payment method and enter format-validated demo payment details.
- FR-18: The system shall never process a real charge, and shall label the flow as a demo throughout.
- FR-19: The system shall generate a receipt/payment record on a successful demo payment.

**Staff**
- FR-20: The system shall show staff a set of actions determined by their role (Admin, Branch Manager, or Technician).
- FR-21: The system shall let a Branch Manager or Admin review an incoming appointment and confirm or reassign its branch and technician.
- FR-22: The system shall let a Technician update the status of their assigned repairs.
- FR-23: The system shall let an Admin view and update spare-part stock levels.

**Offline**
- FR-24: The system shall cache the service catalog locally so it can still be browsed without a connection.
- FR-25: The system shall let a customer draft a repair request offline and resume it once reconnected.

## 6. Non-Functional Requirements

- **Performance:** the catalog list shall load from local cache instantly when offline, and within a couple of seconds from Firestore when online.
- **Usability:** one consistent design system across the app, light and dark themes, clear validation feedback on forms.
- **Security:** credentials handled entirely by Firebase Authentication, no secrets committed to source, Firestore and Supabase access rules restrict data by role.
- **Reliability:** user-friendly states for no internet, a failed image upload, and a denied GPS permission, never a raw stack trace shown to the user.
- **Compatibility:** targets Android SDK 35.
- **Maintainability:** layered architecture (presentation, domain, data) with the branch-matching logic isolated in its own use case, independent of which backend holds the data.

## 7. Use Cases

| ID | Use Case | Actor | Description |
|---|---|---|---|
| UC-1 | Register/Login | Customer, Staff | Create an account or sign in through Firebase Auth |
| UC-2 | Search Services | Customer | Browse and filter the service catalog |
| UC-3 | Book Repair | Customer | Submit device details, issue, photos, and get matched to a branch |
| UC-4 | Track Repair | Customer | View the live status timeline of an active repair |
| UC-5 | View Repair History | Customer | Review past repairs and their details |
| UC-6 | Make Payment | Customer | Complete the simulated payment flow after a repair is ready |
| UC-7 | Review Appointment | Branch Manager, Admin | Confirm or reassign an incoming repair's branch and technician |
| UC-8 | Update Repair Status | Technician | Move an assigned repair through its stages |
| UC-9 | Manage Spare Parts | Admin | View and update spare-part stock levels |

## 8. What's Confirmed vs Still to Decide

Confirmed: scope above, all locked decisions from the architecture doc (Firebase auth, Firebase/Supabase split, three roles with a shared staff UI, 2-day build order).

Nothing left open on requirements. Anything that comes up during the build gets evaluated against this scope before it's added.
