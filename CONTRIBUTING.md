# Contributing to Fixora

Fixora is one Android application maintained by a three-member academic team. Feature branches organize ownership and review; they do not represent separate applications or repositories.

## Branch Ownership

| Branch | Owner | Responsibility |
|---|---|---|
| `feature/customer` | Kumuditha | Customer module |
| `feature/technician` | Tharush | Technician module |
| `feature/admin` | Kavishka | Admin module |

Do not push directly to `main` unless the team has explicitly agreed. Changes should reach `main` through a reviewed Pull Request after the complete application has been verified.

## Before Starting New Work

Replace `feature/your-module` with the branch assigned above.

```bash
git checkout main
git pull origin main
git checkout feature/your-module
git merge main
```

Resolve any merge conflicts carefully. Preserve working code owned by the other modules and confirm that the application still builds as one project.

## Contribution Workflow

1. Pull the latest changes from `main`.
2. Switch to the branch for your assigned module.
3. Make changes only related to your assigned responsibility.
4. Build the complete application and run the relevant tests.
5. Review `git status` and commit with a meaningful message.
6. Push your feature branch.
7. Create a Pull Request from the feature branch into `main`.
8. Review the Pull Request and resolve conflicts with the affected module owner.
9. Merge into `main` only after verification and team approval.

## Build and Test Before Pushing

Run at minimum:

```bash
./gradlew assembleDebug
```

Also run the JVM unit tests when the change can affect application logic:

```bash
./gradlew testDebugUnitTest
```

Use Android or backend integration tests when the affected feature requires them. State honestly in the Pull Request which checks were run and which device-dependent checks remain pending.

## Commit Guidelines

- Use a short, meaningful, imperative message, such as `Improve customer repair history loading`.
- Keep commits focused on one coherent change.
- Do not remove or rewrite another member's module to simplify your branch.
- Do not commit generated build directories, local configuration, account lists, credentials, signing keys, or service-role keys.
- Review staged content before every commit:

```bash
git status
git diff --cached --stat
git diff --cached
```

## Push and Pull Request

For the first push of a feature branch:

```bash
git push -u origin feature/your-module
```

Create a Pull Request targeting `main`, describe the change, list the checks performed, and call out any manual verification still required.
