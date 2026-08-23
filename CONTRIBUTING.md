# Contributing to Fixora

This GitHub repository currently contains the customer application only. Use `feature/customer` for customer work and merge verified changes into `main` through a Pull Request.

## Workflow

1. Update local references and switch to `main`.
2. Pull the latest changes.
3. Switch to `feature/customer` and merge `main`.
4. Make a focused customer-side change.
5. Build and test the application.
6. Review staged files for generated output and secrets.
7. Commit with a meaningful message.
8. Push `feature/customer` and open a Pull Request to `main`.
9. Merge only after verification.

```bash
git checkout main
git pull origin main
git checkout feature/customer
git merge main
./gradlew assembleDebug
./gradlew testDebugUnitTest
git status
git diff --cached
git push origin feature/customer
```

Do not push credentials, `local.properties`, environment files, service-role keys, Firebase Admin credentials, keystores, passwords, or generated build directories. Do not push directly to `main` unless the team has explicitly agreed.
