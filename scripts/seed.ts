/**
 * Attendance Pro — Super Admin Bootstrap & Organization Seed Script (§3, §21)
 *
 * Usage:
 *   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json
 *   export SUPER_ADMIN_EMAIL=amitdaskishanganj@gmail.com
 *   export SUPER_ADMIN_PASSWORD="ChangeMeStrongPass!23"
 *   export SUPER_ADMIN_NAME="Super Admin"
 *   npx ts-node scripts/seed.ts
 */
import * as admin from "firebase-admin";

const DEFAULT_SUPER_ADMIN_EMAIL = "amitdaskishanganj@gmail.com";

async function main() {
  const email = (process.env.SUPER_ADMIN_EMAIL || DEFAULT_SUPER_ADMIN_EMAIL).trim().toLowerCase();
  const password = process.env.SUPER_ADMIN_PASSWORD;
  const fullName = process.env.SUPER_ADMIN_NAME || "Super Admin";
  const orgName = process.env.ORG_NAME || "Attendance Pro";

  if (!email || !password || password.length < 8) {
    console.error(
      "ERROR: Set SUPER_ADMIN_PASSWORD (min 8 chars) in environment variables (SUPER_ADMIN_EMAIL defaults to amitdaskishanganj@gmail.com)."
    );
    process.exit(1);
  }

  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    databaseURL:
      process.env.FIREBASE_DATABASE_URL ||
      "https://chat2-6bd92-default-rtdb.firebaseio.com",
  });

  const db = admin.database();
  const now = admin.database.ServerValue.TIMESTAMP;

  console.log("1. Seeding /attendance/organization node...");
  await db.ref("attendance/organization").update({
    profile: {
      name: orgName,
      legalName: orgName,
      supportEmail: email,
      supportPhone: "",
      timezone: "Asia/Kolkata",
      currency: "INR",
    },
    branding: {
      appTitle: "Attendance Pro",
      logoUrl: "",
      primaryColorHex: "#0F172A",
      accentColorHex: "#00E5FF",
      tagline: "Multi-Store Workforce Intelligence",
    },
    settings: {
      defaultGraceMinutes: 10,
      halfDayThresholdMinutes: 240,
      absentThresholdMinutes: 120,
      overtimeRequiresApproval: true,
      maxOfflineQueueHours: 12,
      gpsRetentionDays: 90,
    },
    policies: {
      leaveTypes: ["CASUAL", "SICK", "EARNED", "UNPAID"],
      defaultAnnualLeaveDays: { CASUAL: 12, SICK: 10, EARNED: 15 },
      payrollCycleDay: 1,
      allowMultipleBreaks: true,
    },
    updatedAt: now,
  });

  console.log("2. Creating / locating first Super Admin account...");
  let userRecord: admin.auth.UserRecord;
  try {
    userRecord = await admin.auth().getUserByEmail(email);
    console.log(`Existing Firebase Auth user found: ${userRecord.uid}`);
  } catch {
    userRecord = await admin.auth().createUser({
      email,
      password,
      displayName: fullName,
      disabled: false,
    });
    console.log(`Created Super Admin user: ${userRecord.uid}`);
  }

  const employeeId = `ADMIN-${userRecord.uid.slice(0, 6).toUpperCase()}`;
  await admin.auth().setCustomUserClaims(userRecord.uid, {
    role: "super_admin",
    employeeId,
    storeIds: [],
    disabled: false,
  });

  await db.ref(`attendance/users/${userRecord.uid}`).set({
    uid: userRecord.uid,
    employeeId,
    profile: {
      fullName,
      email,
      phone: "",
      photoUrl: "",
    },
    role: "super_admin",
    status: "ACTIVE",
    storeAssignments: {},
    createdAt: now,
    updatedAt: now,
  });

  await db.ref("attendance/auditLogs").push().set({
    actorUid: userRecord.uid,
    actorRole: "super_admin",
    action: "BOOTSTRAP_SUPER_ADMIN",
    target: `users/${userRecord.uid}`,
    afterSummary: `Initialized organization '${orgName}' and Super Admin '${email}'`,
    reason: "Initial seed bootstrap",
    serverTimestamp: now,
  });

  console.log("Bootstrap complete! You can now sign in with the Super Admin credentials in the Android app.");
  process.exit(0);
}

main().catch((err) => {
  console.error("Seed failed:", err);
  process.exit(1);
});
