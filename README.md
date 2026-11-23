# Attendance Pro — Multi-Store Enterprise Attendance Management System

Production native Android application (Kotlin, Jetpack Compose, Material 3, Hilt DI, Clean Architecture + MVVM) backed by **Firebase Authentication**, **Firebase Realtime Database** (`/attendance/`), **Firebase Cloud Functions (TypeScript)**, **FCM**, **CameraX + ML Kit Barcode (QR & Aztec)**, **Google Play Services Location**, **Room Offline Queue**, and **Cloudinary**.

---

## 1. Configuration & Architecture Summary

| Parameter | Value |
|---|---|
| Firebase Project ID | `chat2-6bd92` |
| Realtime Database URL | `https://chat2-6bd92-default-rtdb.firebaseio.com` |
| Firebase Storage Bucket | `chat2-6bd92.firebasestorage.app` |
| Android `applicationId` | `com.aistudio.helper.vkmxqr` |
| Cloudinary Cloud Name | `dlsiskxua` |
| Cloudinary Public Preset | `ml_default` (profile photos & store/org logos ONLY) |
| Cloudinary Private Uploads | Signed via Cloud Function `signCloudinaryUpload` (never unsigned) |
| Database Root Scope | `/attendance/` ONLY (No Firestore) |

### Role-Based Single APK Access
- **Super Admin**: Full organization scope, "All Stores" selector, store CRUD & non-destructive deactivation, dynamic org branding, user provisioning, privileged payroll unlock, audit logs.
- **Store Admin**: Assigned stores only — store profile/settings, employees & managers, attendance monitor, scanner kiosk & devices, shifts, holidays, leave, corrections, reports, payroll.
- **Manager**: Assigned team/store — team roster, attendance monitor, shift overview, leave review, correction review, store reports.
- **Employee**: Own records only — live dashboard (today status, check-in/out, work duration, break, late, overtime, monthly totals, attendance %, leave balance), dynamic QR/Aztec punch scanner, break management, leave application, correction requests, salary & PDF slips, non-privileged profile edits.

---

## 2. Firebase Setup & `google-services.json`

1. `app/google-services.json` is pre-placed for `chat2-6bd92` (`com.aistudio.helper.vkmxqr`).
2. Never hardcode Firebase API keys or DB URLs in Kotlin source files.
3. Enable **Email/Password** sign-in in Firebase Console → Authentication → Sign-in method (disable public signup on client — users are created solely via `provisionUser` Cloud Function).
4. Deploy RTDB rules scoped strictly to `/attendance/`:
   ```bash
   firebase deploy --only database
   ```

---

## 3. Cloud Functions Deployment (TypeScript)

```bash
cd functions
npm install
npm run build
```

Configure server-side secrets in Firebase Functions environment (NEVER in the Android APK):
```bash
firebase functions:secrets:set CLOUDINARY_API_KEY
firebase functions:secrets:set CLOUDINARY_API_SECRET
firebase functions:secrets:set SCANNER_HMAC_SECRET
```
Or set them in `functions/.env` for local emulator testing:
```env
CLOUDINARY_CLOUD_NAME=dlsiskxua
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
SCANNER_HMAC_SECRET=your_random_64_hex_secret
```

Deploy all functions and RTDB rules:
```bash
firebase deploy --only functions,database
```

---

## 4. Super Admin Bootstrap (First-Run Seed Script)

Download a Firebase Admin SDK service account JSON from Firebase Console → Project Settings → Service Accounts (keep out of Git — listed in `.gitignore`).

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/serviceAccountKey.json
export SUPER_ADMIN_EMAIL="admin@yourcompany.com"
export SUPER_ADMIN_PASSWORD="StrongPassword!2026"
export SUPER_ADMIN_NAME="Organization Super Admin"
npx ts-node scripts/seed.ts
```

This creates:
- `/attendance/organization/{profile,branding,settings,policies}`
- First Super Admin Auth account with verified custom claim `{ role: "super_admin", disabled: false }`
- Initial audit log entry in `/attendance/auditLogs`

---

## 5. GitHub Actions CI/CD Secrets & Base64 Commands

Workflow location: `.github/workflows/android-build.yml`
- Push to `main` / `develop` or PR to `main` → runs `lintDebug testDebugUnitTest assembleDebug` and uploads `attendancepro-debug`.
- Tag `v*` (e.g. `git tag v1.0.0 && git push origin v1.0.0`) → restores release keystore, builds signed `assembleRelease`, uploads `attendancepro-release`, and creates a GitHub Release with attached APKs.

### Encode Secrets Locally
```bash
# Encode google-services.json
base64 -w 0 app/google-services.json

# Generate upload keystore (if creating new)
keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload

# Encode release keystore
base64 -w 0 my-upload-key.jks
```

### Add Repository Secrets (GitHub → Settings → Secrets and variables → Actions)
- `GOOGLE_SERVICES_JSON_BASE64` — base64 string of `app/google-services.json`
- `RELEASE_KEYSTORE_BASE64` — base64 string of `my-upload-key.jks`
- `KEYSTORE_PASSWORD` — keystore password
- `KEY_ALIAS` — `upload`
- `KEY_PASSWORD` — key password

---

## 6. GPS Privacy & Retention Policy

- GPS location is captured **once per punch** (`FusedLocationProviderClient.getCurrentLocation`) strictly when the store's GPS policy requires or allows it (`disabled | optional | required | gps_and_scanner | scanner_only`).
- **No continuous background location tracking** is performed.
- Mock locations (`isMock` / `isFromMockProvider`) are flagged and rejected under required GPS policies.
- Default GPS coordinate retention is configurable under `/attendance/organization/settings/gpsRetentionDays` (default 90 days).

---

## 7. Verification & Tests

```bash
# Android JVM Unit Tests (Attendance engine, overnight shifts, multi-breaks, geofence, payroll)
gradle :app:testDebugUnitTest

# RTDB Security Rules Structural Tests
node --test functions/test/rules.test.js
```
