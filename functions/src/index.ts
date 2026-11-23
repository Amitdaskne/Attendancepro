import * as admin from "firebase-admin";
import * as crypto from "crypto";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onValueCreated } from "firebase-functions/v2/database";
import ExcelJS from "exceljs";
import PDFDocument from "pdfkit";
import { assertStoreScope, requireCaller, UserRole } from "./lib/auth";
import {
  computeNetPay,
  computeSummaryFromEvents,
  EventRecord,
  haversineMeters,
  ShiftDef,
  validateStateTransition,
} from "./lib/calculator";
import {
  sendPushToUids,
  signChallengePayload,
  signCloudinaryUploadParams,
  writeAuditEntry,
} from "./lib/helpers";

if (!admin.apps.length) {
  admin.initializeApp({
    databaseURL:
      process.env.FIREBASE_DATABASE_URL ||
      "https://chat2-6bd92-default-rtdb.firebaseio.com",
    storageBucket:
      process.env.FIREBASE_STORAGE_BUCKET || "chat2-6bd92.firebasestorage.app",
  });
}

const db = () => admin.database();
const todayIso = () => new Date().toISOString().slice(0, 10);

async function loadShiftForEmployee(shiftId: string): Promise<ShiftDef> {
  if (shiftId) {
    const snap = await db().ref(`attendance/shifts/${shiftId}`).get();
    if (snap.exists()) {
      const s = snap.val();
      return {
        shiftId,
        startTime: s.startTime || "09:00",
        endTime: s.endTime || "18:00",
        graceMinutes: Number(s.graceMinutes ?? 10),
        minWorkingMinutes: Number(s.minWorkingMinutes ?? 480),
        halfDayMinutes: Number(s.halfDayMinutes ?? 240),
        overtimeThresholdMinutes: Number(s.overtimeThresholdMinutes ?? 510),
        overnight: Boolean(s.overnight),
      };
    }
  }
  return {
    shiftId: "default",
    startTime: "09:00",
    endTime: "18:00",
    graceMinutes: 10,
    minWorkingMinutes: 480,
    halfDayMinutes: 240,
    overtimeThresholdMinutes: 510,
    overnight: false,
  };
}

async function recomputeEmployeeSummary(
  storeId: string,
  employeeId: string,
  dateIso: string,
  dayClosed = false
) {
  const empSnap = await db().ref(`attendance/employees/${employeeId}`).get();
  const emp = empSnap.val() || {};
  const shift = await loadShiftForEmployee(emp.currentAssignment?.shiftId || "");

  const eventsSnap = await db()
    .ref("attendance/attendanceEvents")
    .orderByChild("employeeStoreDate")
    .equalTo(`${employeeId}|${storeId}|${dateIso}`)
    .get();

  const events: EventRecord[] = [];
  eventsSnap.forEach((child) => {
    const v = child.val();
    events.push({
      eventId: child.key || v.eventId,
      eventType: v.eventType,
      serverTimestamp: Number(v.serverTimestamp || Date.now()),
      source: v.source,
    });
  });

  const holidaySnap = await db().ref(`attendance/holidays/${storeId}`).get();
  let isHoliday = false;
  holidaySnap.forEach((c) => {
    if (c.child("date").val() === dateIso && !c.child("isOptional").val()) {
      isHoliday = true;
    }
  });

  const leaveSnap = await db()
    .ref("attendance/leaveRequests")
    .orderByChild("employeeId")
    .equalTo(employeeId)
    .get();
  let hasApprovedLeave = false;
  leaveSnap.forEach((c) => {
    const v = c.val();
    if (v.status === "APPROVED" && v.startDate <= dateIso && v.endDate >= dateIso) {
      hasApprovedLeave = true;
    }
  });

  const calc = computeSummaryFromEvents(dateIso, shift, events, {
    isHoliday,
    hasApprovedLeave,
    dayClosed,
  });

  const summary = {
    employeeId,
    employeeName: emp.profile?.fullName || employeeId,
    storeId,
    date: dateIso,
    shiftId: shift.shiftId,
    checkIn: calc.checkIn,
    checkOut: calc.checkOut,
    breakMinutes: calc.breakMinutes,
    workingMinutes: calc.workingMinutes,
    lateMinutes: calc.lateMinutes,
    earlyDepartureMinutes: calc.earlyDepartureMinutes,
    overtimeMinutes: calc.overtimeMinutes,
    status: calc.status,
    workState: calc.workState,
    source: events[events.length - 1]?.source || "SCANNER",
    calcVersion: 2,
    finalized: dayClosed && !calc.missingCheckoutException,
    missingCheckoutException: calc.missingCheckoutException,
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  };

  await db()
    .ref(`attendance/attendance/${storeId}/${employeeId}/${dateIso}/summary`)
    .set(summary);

  await db()
    .ref(`attendance/indexes/attendanceByDate/${dateIso}/${storeId}/${employeeId}`)
    .set(summary.status);

  return summary;
}

// =============================================================================
// 1. User Provisioning, Custom Claims & Store Transfers (§3, §4, §9)
// =============================================================================

export const provisionUser = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const data = (request.data || {}) as Record<string, any>;

  const email = String(data.email || "").trim().toLowerCase();
  const password = String(data.password || "");
  const fullName = String(data.fullName || "").trim();
  const role = String(data.role || "employee") as UserRole;
  const storeIds: string[] = Array.isArray(data.storeIds)
    ? data.storeIds.map(String).filter(Boolean)
    : [];

  if (!email || password.length < 8 || !fullName) {
    throw new HttpsError("invalid-argument", "Email, fullName, and password (min 8 chars) required.");
  }

  if (caller.role === "store_admin" && (role === "super_admin" || role === "store_admin")) {
    throw new HttpsError("permission-denied", "Store Admins may only provision Managers and Employees.");
  }

  if (role !== "super_admin") {
    if (storeIds.length === 0) {
      throw new HttpsError("invalid-argument", "At least one storeId is required.");
    }
    for (const sId of storeIds) {
      await assertStoreScope(caller, sId);
    }
  }

  const userRecord = await admin.auth().createUser({
    email,
    password,
    displayName: fullName,
    disabled: false,
  });

  const employeeId = `EMP-${userRecord.uid.slice(0, 8).toUpperCase()}`;
  const storeAssignments: Record<string, boolean> = {};
  storeIds.forEach((s) => (storeAssignments[s] = true));

  await admin.auth().setCustomUserClaims(userRecord.uid, {
    role,
    employeeId,
    storeIds,
    disabled: false,
  });

  const now = admin.database.ServerValue.TIMESTAMP;
  await db().ref(`attendance/users/${userRecord.uid}`).set({
    uid: userRecord.uid,
    employeeId,
    profile: {
      fullName,
      email,
      phone: String(data.phone || ""),
      photoUrl: String(data.photoUrl || ""),
    },
    role,
    status: "ACTIVE",
    storeAssignments,
    createdAt: now,
    updatedAt: now,
  });

  const primaryStoreId = storeIds[0] || "";
  await db().ref(`attendance/employees/${employeeId}`).set({
    employeeId,
    uid: userRecord.uid,
    profile: {
      fullName,
      email,
      phone: String(data.phone || ""),
      photoUrl: String(data.photoUrl || ""),
      dob: "",
      gender: "",
      address: "",
      emergencyContact: { name: "", relation: "", phone: "" },
    },
    employment: {
      employeeCode: employeeId,
      joiningDate: todayIso(),
      departmentId: String(data.departmentId || ""),
      departmentName: String(data.departmentName || "Operations"),
      designation: String(data.designation || role),
      employmentStatus: "ACTIVE",
    },
    currentAssignment: {
      storeId: primaryStoreId,
      managerId: String(data.managerId || ""),
      shiftId: String(data.shiftId || ""),
      assignedAt: now,
    },
    salaryPrivate: {
      salaryType: data.salaryPrivate?.salaryType || "MONTHLY",
      baseSalary: Number(data.salaryPrivate?.baseSalary || 0),
      allowances: Number(data.salaryPrivate?.allowances || 0),
      overtimeHourlyRate: Number(data.salaryPrivate?.overtimeHourlyRate || 0),
      defaultDeductions: Number(data.salaryPrivate?.defaultDeductions || 0),
      currency: "INR",
      updatedAt: now,
    },
  });

  const orgPolicies = (await db().ref("attendance/organization/policies/defaultAnnualLeaveDays").get()).val() || {
    CASUAL: 12,
    SICK: 10,
    EARNED: 15,
  };
  await db().ref(`attendance/leaveBalances/${employeeId}`).set({
    employeeId,
    balances: orgPolicies,
    used: {},
    updatedAt: now,
  });

  for (const sId of storeIds) {
    await db().ref(`attendance/indexes/userByStore/${sId}/${userRecord.uid}`).set(true);
    await db().ref(`attendance/indexes/employeeByStore/${sId}/${employeeId}`).set(true);
  }

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "USER_PROVISIONED",
    target: `users/${userRecord.uid}`,
    storeId: primaryStoreId,
    afterSummary: `${fullName} (${role}) assigned to [${storeIds.join(",")}]`,
  });

  return { uid: userRecord.uid, employeeId, role };
});

export const setCustomClaims = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin"]);
  const data = (request.data || {}) as Record<string, any>;
  const targetUid = String(data.targetUid || "");
  const role = String(data.role || "employee") as UserRole;
  const storeIds: string[] = Array.isArray(data.storeIds) ? data.storeIds.map(String) : [];
  const disabled = Boolean(data.disabled);
  const reason = String(data.reason || "Role/status updated");

  if (!targetUid) throw new HttpsError("invalid-argument", "targetUid is required.");

  const userSnap = await db().ref(`attendance/users/${targetUid}`).get();
  const prev = userSnap.val() || {};
  const employeeId = prev.employeeId || `EMP-${targetUid.slice(0, 8).toUpperCase()}`;

  await admin.auth().updateUser(targetUid, { disabled });
  await admin.auth().setCustomUserClaims(targetUid, {
    role,
    employeeId,
    storeIds,
    disabled,
  });

  const storeAssignments: Record<string, boolean> = {};
  storeIds.forEach((s) => (storeAssignments[s] = true));

  await db().ref(`attendance/users/${targetUid}`).update({
    role,
    status: disabled ? "DISABLED" : "ACTIVE",
    storeAssignments,
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "ROLE_OR_STATUS_CHANGED",
    target: `users/${targetUid}`,
    beforeSummary: `role=${prev.role}, status=${prev.status}`,
    afterSummary: `role=${role}, status=${disabled ? "DISABLED" : "ACTIVE"}`,
    reason,
  });

  return { success: true };
});

export const transferEmployee = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const data = (request.data || {}) as Record<string, any>;
  const employeeId = String(data.employeeId || "");
  const fromStoreId = String(data.fromStoreId || "");
  const toStoreId = String(data.toStoreId || "");
  const transferDate = String(data.transferDate || todayIso());
  const reason = String(data.reason || "Store transfer");

  if (!employeeId || !toStoreId || fromStoreId === toStoreId) {
    throw new HttpsError("invalid-argument", "Valid employeeId and distinct destination storeId required.");
  }
  await assertStoreScope(caller, toStoreId);
  if (fromStoreId) await assertStoreScope(caller, fromStoreId);

  const empRef = db().ref(`attendance/employees/${employeeId}`);
  const empSnap = await empRef.get();
  if (!empSnap.exists()) throw new HttpsError("not-found", "Employee not found.");
  const emp = empSnap.val();

  const transferKey = empRef.child("transferHistory").push().key || `tr_${Date.now()}`;
  const now = admin.database.ServerValue.TIMESTAMP;

  // Historical attendance under /attendance/attendance/{fromStoreId}/{employeeId} is NEVER moved or deleted.
  await empRef.update({
    "currentAssignment/storeId": toStoreId,
    "currentAssignment/shiftId": String(data.shiftId || emp.currentAssignment?.shiftId || ""),
    "currentAssignment/managerId": String(data.managerId || ""),
    "currentAssignment/assignedAt": now,
    [`transferHistory/${transferKey}`]: {
      transferId: transferKey,
      fromStoreId,
      toStoreId,
      transferDate,
      reason,
      actorUid: caller.uid,
      timestamp: now,
    },
  });

  if (emp.uid) {
    await db().ref(`attendance/users/${emp.uid}/storeAssignments`).set({ [toStoreId]: true });
    await admin.auth().setCustomUserClaims(emp.uid, {
      role: "employee",
      employeeId,
      storeIds: [toStoreId],
      disabled: false,
    });
  }
  if (fromStoreId) {
    await db().ref(`attendance/indexes/employeeByStore/${fromStoreId}/${employeeId}`).remove();
  }
  await db().ref(`attendance/indexes/employeeByStore/${toStoreId}/${employeeId}`).set(true);

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "EMPLOYEE_TRANSFERRED",
    target: `employees/${employeeId}`,
    storeId: toStoreId,
    beforeSummary: `store=${fromStoreId}`,
    afterSummary: `store=${toStoreId}, date=${transferDate}`,
    reason,
  });

  return { success: true, transferId: transferKey };
});

// =============================================================================
// 2. Scanner Challenge, Device Registry & Authoritative Punch Engine (§6, §7, §8)
// =============================================================================

export const registerDevice = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const data = (request.data || {}) as Record<string, any>;
  const name = String(data.name || "").trim();
  const storeId = String(data.storeId || "").trim();
  const permittedActions: string[] = Array.isArray(data.permittedActions)
    ? data.permittedActions.map(String)
    : ["CHECK_IN", "CHECK_OUT", "BREAK_START", "BREAK_END"];

  if (!name || !storeId) throw new HttpsError("invalid-argument", "Device name and storeId required.");
  await assertStoreScope(caller, storeId);

  const ref = db().ref("attendance/devices").push();
  const deviceId = ref.key || `dev_${Date.now()}`;
  const now = admin.database.ServerValue.TIMESTAMP;

  await ref.set({
    deviceId,
    name,
    storeId,
    permittedActions,
    status: "ACTIVE",
    registeredBy: caller.uid,
    lastSeenAt: now,
    createdAt: now,
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "DEVICE_REGISTERED",
    target: `devices/${deviceId}`,
    storeId,
    afterSummary: `${name} (${permittedActions.join(",")})`,
  });

  return { deviceId };
});

export const revokeDevice = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const data = (request.data || {}) as Record<string, any>;
  const deviceId = String(data.deviceId || "");
  const status = String(data.status || "REVOKED").toUpperCase();
  const reason = String(data.reason || "Device status changed");

  const snap = await db().ref(`attendance/devices/${deviceId}`).get();
  if (!snap.exists()) throw new HttpsError("not-found", "Device not found.");
  const dev = snap.val();
  await assertStoreScope(caller, dev.storeId);

  await db().ref(`attendance/devices/${deviceId}`).update({
    status,
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "DEVICE_STATUS_UPDATED",
    target: `devices/${deviceId}`,
    storeId: dev.storeId,
    beforeSummary: `status=${dev.status}`,
    afterSummary: `status=${status}`,
    reason,
  });

  return { success: true };
});

export const issueScannerChallenge = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const data = (request.data || {}) as Record<string, any>;
  const storeId = String(data.storeId || "");
  const deviceId = String(data.deviceId || "");
  const action = String(data.action || "ANY").toUpperCase();
  const format = String(data.format || "QR").toUpperCase();

  await assertStoreScope(caller, storeId);

  const [storeSnap, devSnap] = await Promise.all([
    db().ref(`attendance/stores/${storeId}`).get(),
    db().ref(`attendance/devices/${deviceId}`).get(),
  ]);

  if (!storeSnap.exists() || storeSnap.val().status !== "ACTIVE") {
    throw new HttpsError("failed-precondition", "Store is inactive or not found.");
  }
  if (!devSnap.exists() || devSnap.val().status !== "ACTIVE" || devSnap.val().storeId !== storeId) {
    throw new HttpsError("permission-denied", "Scanner device is not active or does not belong to this store.");
  }

  const ttlSeconds = Math.min(60, Math.max(15, Number(storeSnap.val().settings?.scannerTtlSeconds || 45)));
  const issuedAtMs = Date.now();
  const expiresAtMs = issuedAtMs + ttlSeconds * 1000;
  const challengeId = `ch_${crypto.randomBytes(8).toString("hex")}`;
  const nonce = crypto.randomBytes(12).toString("hex");
  const signature = signChallengePayload([challengeId, storeId, deviceId, nonce, String(expiresAtMs), action]);

  await db().ref(`attendance/scannerChallenges/${challengeId}`).set({
    challengeId,
    storeId,
    deviceId,
    action,
    format,
    nonce,
    signature,
    issuedAtMs,
    expiresAtMs,
    status: "ACTIVE",
    usedBy: {},
  });

  await db().ref(`attendance/devices/${deviceId}/lastSeenAt`).set(admin.database.ServerValue.TIMESTAMP);

  return { challengeId, storeId, deviceId, action, format, nonce, signature, issuedAtMs, expiresAtMs };
});

export const validatePunch = onCall(async (request) => {
  const caller = requireCaller(request, ["employee", "manager", "store_admin", "super_admin"]);
  const data = (request.data || {}) as Record<string, any>;

  const employeeId = caller.employeeId;
  const eventType = String(data.eventType || "").toUpperCase();
  const idempotencyKey = String(data.idempotencyKey || "");
  const eventId = String(data.eventId || `evt_${crypto.randomBytes(8).toString("hex")}`);

  if (!idempotencyKey) throw new HttpsError("invalid-argument", "idempotencyKey is required.");

  // Idempotency replay protection
  const dupSnap = await db()
    .ref("attendance/attendanceEvents")
    .orderByChild("idempotencyKey")
    .equalTo(idempotencyKey)
    .get();
  if (dupSnap.exists()) {
    return { success: true, duplicate: true, message: "Punch already accepted (idempotent replay)." };
  }

  const empSnap = await db().ref(`attendance/employees/${employeeId}`).get();
  if (!empSnap.exists()) throw new HttpsError("not-found", "Employee profile not found.");
  const emp = empSnap.val();
  const assignedStoreId = String(emp.currentAssignment?.storeId || data.storeId || "");
  if (!assignedStoreId) throw new HttpsError("failed-precondition", "No store assigned.");

  const storeSnap = await db().ref(`attendance/stores/${assignedStoreId}`).get();
  if (!storeSnap.exists() || storeSnap.val().status !== "ACTIVE") {
    throw new HttpsError("failed-precondition", "Assigned store is inactive.");
  }
  const store = storeSnap.val();
  const gpsMode: string = store.settings?.gpsMode || "gps_and_scanner";
  const needsScanner = gpsMode === "gps_and_scanner" || gpsMode === "scanner_only";
  const needsGps = gpsMode === "required" || gpsMode === "gps_and_scanner";

  // 1. Validate rotating scanner challenge (§7)
  let verifiedDeviceId = "";
  let verifiedChallengeId = "";
  if (needsScanner || data.challengeId) {
    const challengeId = String(data.challengeId || "");
    const nonce = String(data.nonce || "");
    const deviceId = String(data.deviceId || "");
    const signature = String(data.signature || "");

    if (!challengeId || !nonce || !deviceId || !signature) {
      throw new HttpsError("invalid-argument", "Live dynamic scanner challenge is required. Static QR codes are rejected.");
    }

    const chSnap = await db().ref(`attendance/scannerChallenges/${challengeId}`).get();
    if (!chSnap.exists()) throw new HttpsError("permission-denied", "Scanner challenge invalid or unknown.");
    const ch = chSnap.val();

    if (ch.status !== "ACTIVE" || Date.now() > Number(ch.expiresAtMs)) {
      throw new HttpsError("deadline-exceeded", "Scanner code expired. Scan the freshly rotated code.");
    }
    if (ch.nonce !== nonce || ch.storeId !== assignedStoreId || ch.deviceId !== deviceId) {
      throw new HttpsError("permission-denied", "Scanner challenge store/device/nonce mismatch.");
    }
    if (ch.action !== "ANY" && ch.action !== eventType) {
      throw new HttpsError("permission-denied", `Scanner code is restricted to ${ch.action}.`);
    }
    const expectedSig = signChallengePayload([
      ch.challengeId,
      ch.storeId,
      ch.deviceId,
      ch.nonce,
      String(ch.expiresAtMs),
      ch.action,
    ]);
    if (expectedSig !== signature) {
      throw new HttpsError("permission-denied", "Scanner signature verification failed.");
    }

    const devSnap = await db().ref(`attendance/devices/${deviceId}`).get();
    if (!devSnap.exists() || devSnap.val().status !== "ACTIVE") {
      throw new HttpsError("permission-denied", "Scanner device revoked or inactive.");
    }

    // Prevent same employee replaying the same challenge nonce
    if (ch.usedBy && ch.usedBy[employeeId]) {
      throw new HttpsError("already-exists", "Challenge nonce already used by this employee.");
    }
    await db().ref(`attendance/scannerChallenges/${challengeId}/usedBy/${employeeId}`).set(Date.now());
    verifiedDeviceId = deviceId;
    verifiedChallengeId = challengeId;
  }

  // 2. Validate one-shot GPS geofence (§8)
  const gps = data.gps as
    | { latitude: number; longitude: number; accuracyMeters: number; isMocked: boolean }
    | undefined;
  let distanceMeters: number | null = null;
  let gpsFlaggedMock = false;

  if (needsGps && !gps) {
    throw new HttpsError("failed-precondition", "GPS coordinates required by store policy.");
  }
  if (gps) {
    gpsFlaggedMock = Boolean(gps.isMocked);
    distanceMeters = haversineMeters(
      Number(gps.latitude),
      Number(gps.longitude),
      Number(store.location?.latitude || 0),
      Number(store.location?.longitude || 0)
    );
    const radius = Number(store.location?.radiusMeters || 100);
    const maxAcc = Number(store.settings?.maxGpsAccuracyMeters || 80);

    if (needsGps && gpsFlaggedMock) {
      throw new HttpsError("permission-denied", "Mock GPS location detected and rejected.");
    }
    if (needsGps && distanceMeters > radius) {
      throw new HttpsError(
        "failed-precondition",
        `Outside store geofence (${Math.round(distanceMeters)}m vs ${radius}m limit).`
      );
    }
    if (needsGps && Number(gps.accuracyMeters || 0) > maxAcc) {
      throw new HttpsError(
        "failed-precondition",
        `GPS accuracy too low (±${Math.round(gps.accuracyMeters)}m; max ${maxAcc}m).`
      );
    }
  }

  // 3. Validate attendance state machine (§6)
  const dateIso = todayIso();
  const prevSummarySnap = await db()
    .ref(`attendance/attendance/${assignedStoreId}/${employeeId}/${dateIso}/summary`)
    .get();
  const currentState = prevSummarySnap.exists()
    ? String(prevSummarySnap.val().workState || "NOT_CHECKED_IN")
    : "NOT_CHECKED_IN";

  const transition = validateStateTransition(currentState, eventType);
  if (!transition.ok) {
    throw new HttpsError("failed-precondition", transition.error || "Invalid attendance state transition.");
  }

  // 4. Write immutable attendanceEvent with ServerValue.TIMESTAMP
  const isStaleOffline = Boolean(data.offlineQueued) && Boolean(data.staleFlag);
  const serverNowMs = Date.now();
  await db().ref(`attendance/attendanceEvents/${eventId}`).set({
    eventId,
    idempotencyKey,
    employeeId,
    storeId: assignedStoreId,
    date: dateIso,
    employeeStoreDate: `${employeeId}|${assignedStoreId}|${dateIso}`,
    eventType,
    serverTimestamp: serverNowMs,
    source: verifiedChallengeId ? "SCANNER" : data.offlineQueued ? "OFFLINE_SYNC" : "GPS_APP",
    deviceId: verifiedDeviceId,
    challengeId: verifiedChallengeId,
    gps: gps || null,
    gpsFlaggedMock,
    distanceMeters,
    verified: !isStaleOffline && !gpsFlaggedMock,
    staleFlag: isStaleOffline,
    actorUid: caller.uid,
    createdAt: admin.database.ServerValue.TIMESTAMP,
  });

  const summary = await recomputeEmployeeSummary(assignedStoreId, employeeId, dateIso, false);

  return {
    success: true,
    eventId,
    workState: summary.workState,
    status: summary.status,
    message: isStaleOffline
      ? "Offline punch synced and flagged STALE for admin review."
      : `${eventType.replace("_", " ")} verified at ${new Date(serverNowMs).toISOString().slice(11, 16)} UTC.`,
  };
});

// =============================================================================
// 3. Leave & Attendance Correction Approvals (§9 & §13)
// =============================================================================

export const approveLeave = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin", "manager"]);
  const { requestId, note = "" } = (request.data || {}) as { requestId: string; note?: string };
  const ref = db().ref(`attendance/leaveRequests/${requestId}`);
  const snap = await ref.get();
  if (!snap.exists()) throw new HttpsError("not-found", "Leave request not found.");
  const req = snap.val();
  await assertStoreScope(caller, req.storeId);

  if (req.status !== "PENDING") {
    throw new HttpsError("failed-precondition", "Leave request already processed.");
  }

  // Overlap & balance verification
  const balRef = db().ref(`attendance/leaveBalances/${req.employeeId}`);
  const balSnap = await balRef.get();
  const balances = balSnap.val()?.balances || {};
  const used = balSnap.val()?.used || {};
  const leaveType = String(req.leaveType || "CASUAL");
  const days = Number(req.totalDays || 1);

  if (leaveType !== "UNPAID") {
    const currentAvail = Number(balances[leaveType] ?? 0);
    if (currentAvail < days) {
      throw new HttpsError("failed-precondition", `Insufficient ${leaveType} balance (${currentAvail} available).`);
    }
    await balRef.update({
      [`balances/${leaveType}`]: Number((currentAvail - days).toFixed(2)),
      [`used/${leaveType}`]: Number((Number(used[leaveType] ?? 0) + days).toFixed(2)),
      updatedAt: admin.database.ServerValue.TIMESTAMP,
    });
  }

  await ref.update({
    status: "APPROVED",
    reviewerUid: caller.uid,
    reviewerNote: String(note),
    reviewedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "LEAVE_APPROVED",
    target: `leaveRequests/${requestId}`,
    storeId: req.storeId,
    afterSummary: `${req.employeeId} ${leaveType} (${days}d) ${req.startDate}..${req.endDate}`,
    reason: String(note),
  });

  return { success: true };
});

export const rejectLeave = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin", "manager"]);
  const { requestId, note = "" } = (request.data || {}) as { requestId: string; note?: string };
  const ref = db().ref(`attendance/leaveRequests/${requestId}`);
  const snap = await ref.get();
  if (!snap.exists()) throw new HttpsError("not-found", "Leave request not found.");
  const req = snap.val();
  await assertStoreScope(caller, req.storeId);

  await ref.update({
    status: "REJECTED",
    reviewerUid: caller.uid,
    reviewerNote: String(note),
    reviewedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "LEAVE_REJECTED",
    target: `leaveRequests/${requestId}`,
    storeId: req.storeId,
    reason: String(note),
  });

  return { success: true };
});

export const approveCorrection = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin", "manager"]);
  const { requestId, note = "" } = (request.data || {}) as { requestId: string; note?: string };
  const ref = db().ref(`attendance/attendanceCorrections/${requestId}`);
  const snap = await ref.get();
  if (!snap.exists()) throw new HttpsError("not-found", "Correction request not found.");
  const req = snap.val();
  await assertStoreScope(caller, req.storeId);

  if (req.status !== "PENDING") {
    throw new HttpsError("failed-precondition", "Correction request already processed.");
  }

  // Append a separate immutable ADMIN_CORRECTION event (NEVER overwrite originals)
  const eventId = `corr_${crypto.randomBytes(8).toString("hex")}`;
  const ts = Number(req.requestedTimestampMs || Date.now());
  await db().ref(`attendance/attendanceEvents/${eventId}`).set({
    eventId,
    idempotencyKey: `correction_${requestId}`,
    employeeId: req.employeeId,
    storeId: req.storeId,
    date: req.date,
    employeeStoreDate: `${req.employeeId}|${req.storeId}|${req.date}`,
    eventType: "ADMIN_CORRECTION",
    serverTimestamp: ts,
    source: "ADMIN_CORRECTION",
    verified: true,
    note: `${req.correctionType}: ${note || req.reason}`,
    actorUid: caller.uid,
    createdAt: admin.database.ServerValue.TIMESTAMP,
  });

  await ref.update({
    status: "APPROVED",
    reviewerUid: caller.uid,
    reviewerNote: String(note),
    resultingEventId: eventId,
    reviewedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await recomputeEmployeeSummary(req.storeId, req.employeeId, req.date, false);

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "CORRECTION_APPROVED",
    target: `attendanceCorrections/${requestId}`,
    storeId: req.storeId,
    afterSummary: `Appended ADMIN_CORRECTION event ${eventId} for ${req.employeeId} on ${req.date}`,
    reason: String(note),
  });

  return { success: true, eventId };
});

export const rejectCorrection = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin", "manager"]);
  const { requestId, note = "" } = (request.data || {}) as { requestId: string; note?: string };
  const ref = db().ref(`attendance/attendanceCorrections/${requestId}`);
  const snap = await ref.get();
  if (!snap.exists()) throw new HttpsError("not-found", "Correction request not found.");
  const req = snap.val();
  await assertStoreScope(caller, req.storeId);

  await ref.update({
    status: "REJECTED",
    reviewerUid: caller.uid,
    reviewerNote: String(note),
    reviewedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "CORRECTION_REJECTED",
    target: `attendanceCorrections/${requestId}`,
    storeId: req.storeId,
    reason: String(note),
  });

  return { success: true };
});

// =============================================================================
// 4. Daily Attendance Calculation (Scheduled + On-Demand) (§9)
// =============================================================================

async function runDailyAttendancePass(targetStoreId: string | null, dateIso: string) {
  const empSnap = await db().ref("attendance/employees").get();
  let processed = 0;
  const storeStats: Record<
    string,
    { total: number; present: number; late: number; absent: number; exception: number; otMin: number }
  > = {};

  const employees: any[] = [];
  empSnap.forEach((c) => {
    const e = c.val();
    if (!e?.currentAssignment?.storeId) return;
    if (targetStoreId && e.currentAssignment.storeId !== targetStoreId) return;
    employees.push(e);
  });

  for (const emp of employees) {
    const sId = emp.currentAssignment.storeId;
    const summary = await recomputeEmployeeSummary(sId, emp.employeeId, dateIso, true);
    processed++;

    if (!storeStats[sId]) {
      storeStats[sId] = { total: 0, present: 0, late: 0, absent: 0, exception: 0, otMin: 0 };
    }
    storeStats[sId].total++;
    if (summary.status === "PRESENT" || summary.status === "LATE") storeStats[sId].present++;
    if (summary.lateMinutes > 0) storeStats[sId].late++;
    if (summary.status === "ABSENT") storeStats[sId].absent++;
    if (summary.missingCheckoutException) storeStats[sId].exception++;
    storeStats[sId].otMin += summary.overtimeMinutes;
  }

  for (const [sId, st] of Object.entries(storeStats)) {
    await db().ref(`attendance/statistics/${sId}/${dateIso}`).set({
      storeId: sId,
      date: dateIso,
      totalEmployees: st.total,
      presentCount: st.present,
      lateCount: st.late,
      absentCount: st.absent,
      exceptionCount: st.exception,
      totalOvertimeMinutes: st.otMin,
      updatedAt: admin.database.ServerValue.TIMESTAMP,
    });
  }
  return processed;
}

export const calculateDailyAttendance = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const { storeId = null, date = todayIso() } = (request.data || {}) as {
    storeId?: string | null;
    date?: string;
  };
  if (storeId) await assertStoreScope(caller, storeId);
  const count = await runDailyAttendancePass(storeId, date);
  return { success: true, processed: count, message: `Recalculated ${count} employee summaries for ${date}.` };
});

export const scheduledDailyAttendance = onSchedule("55 23 * * *", async () => {
  await runDailyAttendancePass(null, todayIso());
});

// =============================================================================
// 5. Payroll Calculation, Approval, Lock/Unlock & PDF Salary Slips (§14)
// =============================================================================

export const calculatePayroll = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const { storeId, month } = (request.data || {}) as { storeId: string; month: string };
  if (!storeId || !/^\d{4}-\d{2}$/.test(month || "")) {
    throw new HttpsError("invalid-argument", "Valid storeId and YYYY-MM month required.");
  }
  await assertStoreScope(caller, storeId);

  const periodId = `${storeId}_${month}`;
  const existing = await db().ref(`attendance/payroll/${periodId}`).get();
  if (existing.exists() && existing.val().status === "LOCKED") {
    throw new HttpsError("failed-precondition", "Payroll period is LOCKED. Super Admin unlock required.");
  }

  const empSnap = await db().ref("attendance/employees").get();
  const lines: Record<string, any> = {};
  let totalNetPay = 0;
  let totalExceptions = 0;

  const employees: any[] = [];
  empSnap.forEach((c) => {
    const e = c.val();
    if (e?.currentAssignment?.storeId === storeId) employees.push(e);
  });

  for (const emp of employees) {
    const empId = emp.employeeId;
    const sal = emp.salaryPrivate || {};
    const attSnap = await db().ref(`attendance/attendance/${storeId}/${empId}`).get();

    let presentDays = 0;
    let workingMinutes = 0;
    let overtimeMinutes = 0;
    let exceptionCount = 0;
    let unpaidLeaveDays = 0;
    let paidLeaveDays = 0;

    attSnap.forEach((dateNode) => {
      const dateKey = dateNode.key || "";
      if (!dateKey.startsWith(month)) return;
      const sum = dateNode.child("summary").val();
      if (!sum) return;
      if (sum.status === "PRESENT" || sum.status === "LATE") presentDays += 1;
      else if (sum.status === "HALF_DAY") presentDays += 0.5;
      else if (sum.status === "ON_LEAVE") paidLeaveDays += 1;
      else if (sum.status === "ABSENT") unpaidLeaveDays += 1;
      if (sum.missingCheckoutException || sum.status === "EXCEPTION") exceptionCount += 1;
      workingMinutes += Number(sum.workingMinutes || 0);
      overtimeMinutes += Number(sum.overtimeMinutes || 0);
    });

    const salaryType = String(sal.salaryType || "MONTHLY");
    const baseRate = Number(sal.baseSalary || 0);
    const workingHours = Number((workingMinutes / 60).toFixed(2));
    const overtimeHours = Number((overtimeMinutes / 60).toFixed(2));

    let baseAmount = baseRate;
    let unpaidLeaveDeduction = 0;
    if (salaryType === "PER_DAY") {
      baseAmount = Number((baseRate * (presentDays + paidLeaveDays)).toFixed(2));
    } else if (salaryType === "HOURLY") {
      baseAmount = Number((baseRate * workingHours).toFixed(2));
    } else {
      const perDay = baseRate / 30;
      unpaidLeaveDeduction = Number((perDay * unpaidLeaveDays).toFixed(2));
    }

    const allowances = Number(sal.allowances || 0);
    const overtimePay = Number((overtimeHours * Number(sal.overtimeHourlyRate || 0)).toFixed(2));
    const bonus = 0;
    const deductions = Number(sal.defaultDeductions || 0);

    const netPay = computeNetPay({
      baseAmount,
      allowances,
      overtimePay,
      bonus,
      deductions,
      unpaidLeaveDeduction,
    });

    lines[empId] = {
      employeeId: empId,
      employeeName: emp.profile?.fullName || empId,
      storeId,
      salaryType,
      presentDays,
      paidLeaveDays,
      unpaidLeaveDays,
      workingHours,
      overtimeHours,
      exceptionCount,
      baseAmount,
      allowances,
      overtimePay,
      bonus,
      deductions,
      unpaidLeaveDeduction,
      netPay,
      slipUrl: "",
    };
    totalNetPay += netPay;
    totalExceptions += exceptionCount;
  }

  await db().ref(`attendance/payroll/${periodId}`).set({
    periodId,
    storeId,
    month,
    status: "CALCULATED",
    totalNetPay: Number(totalNetPay.toFixed(2)),
    employeeCount: Object.keys(lines).length,
    exceptionCount: totalExceptions,
    lines,
    calculatedBy: caller.uid,
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "PAYROLL_CALCULATED",
    target: `payroll/${periodId}`,
    storeId,
    afterSummary: `${Object.keys(lines).length} employees, net=${totalNetPay.toFixed(2)}, exceptions=${totalExceptions}`,
  });

  return { periodId, totalNetPay };
});

export const approvePayroll = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const { periodId, note = "" } = (request.data || {}) as { periodId: string; note?: string };
  const ref = db().ref(`attendance/payroll/${periodId}`);
  const snap = await ref.get();
  if (!snap.exists()) throw new HttpsError("not-found", "Payroll period not found.");
  const period = snap.val();
  await assertStoreScope(caller, period.storeId);

  if (period.status === "LOCKED") throw new HttpsError("failed-precondition", "Payroll already locked.");

  await ref.update({
    status: "APPROVED",
    approvedBy: caller.uid,
    approvalNote: String(note),
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "PAYROLL_APPROVED",
    target: `payroll/${periodId}`,
    storeId: period.storeId,
    reason: String(note),
  });
  return { success: true };
});

export const lockPayroll = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const { periodId } = (request.data || {}) as { periodId: string };
  const ref = db().ref(`attendance/payroll/${periodId}`);
  const snap = await ref.get();
  if (!snap.exists()) throw new HttpsError("not-found", "Payroll period not found.");
  const period = snap.val();
  await assertStoreScope(caller, period.storeId);

  const lines = period.lines || {};
  const notifyUids: string[] = [];

  for (const [empId, line] of Object.entries<any>(lines)) {
    // Generate PDF buffer for salary slip
    const pdfBuffer = await buildSimplePdfBuffer(
      `Salary Slip — ${period.month}`,
      [
        `Employee: ${line.employeeName} (${empId})`,
        `Store: ${period.storeId}`,
        `Base: ${line.baseAmount}`,
        `Allowances: ${line.allowances}`,
        `Overtime Pay: ${line.overtimePay}`,
        `Bonus: ${line.bonus}`,
        `Deductions: ${line.deductions}`,
        `Unpaid Leave Deduction: ${line.unpaidLeaveDeduction}`,
        `NET PAY: ${line.netPay}`,
      ]
    );

    const filePath = `salary_slips/${period.storeId}/${period.month}/${empId}.pdf`;
    const bucket = admin.storage().bucket();
    const file = bucket.file(filePath);
    await file.save(pdfBuffer, { contentType: "application/pdf" });
    const [signedUrl] = await file.getSignedUrl({
      action: "read",
      expires: Date.now() + 30 * 24 * 60 * 60 * 1000,
    });

    await db().ref(`attendance/salarySlips/${empId}/${periodId}`).set({
      periodId,
      employeeId: empId,
      storeId: period.storeId,
      month: period.month,
      currency: "INR",
      baseAmount: line.baseAmount,
      allowances: line.allowances,
      overtimePay: line.overtimePay,
      bonus: line.bonus,
      deductions: line.deductions,
      unpaidLeaveDeduction: line.unpaidLeaveDeduction,
      netPay: line.netPay,
      pdfUrl: signedUrl,
      generatedAt: admin.database.ServerValue.TIMESTAMP,
    });

    const empUid = (await db().ref(`attendance/employees/${empId}/uid`).get()).val();
    if (empUid) notifyUids.push(String(empUid));
  }

  await ref.update({
    status: "LOCKED",
    lockedBy: caller.uid,
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await sendPushToUids(notifyUids, {
    title: `Salary Slip Ready (${period.month})`,
    body: "Your monthly salary slip has been generated and is available in Attendance Pro.",
    category: "payroll",
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "PAYROLL_LOCKED",
    target: `payroll/${periodId}`,
    storeId: period.storeId,
    afterSummary: `Issued ${Object.keys(lines).length} salary slips`,
  });

  return { success: true };
});

export const unlockPayroll = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin"]);
  const { periodId, reason = "" } = (request.data || {}) as { periodId: string; reason?: string };
  if (!reason.trim()) throw new HttpsError("invalid-argument", "Audit reason is mandatory to unlock payroll.");

  const ref = db().ref(`attendance/payroll/${periodId}`);
  const snap = await ref.get();
  if (!snap.exists()) throw new HttpsError("not-found", "Payroll period not found.");
  const period = snap.val();

  await ref.update({
    status: "CALCULATED",
    unlockedBy: caller.uid,
    unlockReason: reason.trim(),
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "PAYROLL_UNLOCKED_PRIVILEGED",
    target: `payroll/${periodId}`,
    storeId: period.storeId,
    beforeSummary: "status=LOCKED",
    afterSummary: "status=CALCULATED",
    reason: reason.trim(),
  });
  return { success: true };
});

// =============================================================================
// 6. Server-Side Report Exports (CSV, XLSX, PDF) (§15)
// =============================================================================

export const exportReport = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin", "manager"]);
  const data = (request.data || {}) as Record<string, any>;
  const reportType = String(data.reportType || "DAILY").toUpperCase();
  const format = String(data.format || "CSV").toUpperCase();
  const storeId: string | null = data.storeId ? String(data.storeId) : null;
  const startDate = String(data.startDate || todayIso());
  const endDate = String(data.endDate || todayIso());
  const statusFilter = data.status ? String(data.status).toUpperCase() : null;

  if (storeId) {
    await assertStoreScope(caller, storeId);
  } else if (caller.role !== "super_admin") {
    throw new HttpsError("permission-denied", "Store scope required for non-Super Admin reports.");
  }

  const attSnap = await db().ref("attendance/attendance").get();
  const rows: Record<string, any>[] = [];

  attSnap.forEach((storeNode) => {
    const sId = storeNode.key || "";
    if (storeId && sId !== storeId) return;
    if (caller.role !== "super_admin" && !caller.storeIds.includes(sId)) return;

    storeNode.forEach((empNode) => {
      empNode.forEach((dateNode) => {
        const dKey = dateNode.key || "";
        if (dKey < startDate || dKey > endDate) return;
        const sum = dateNode.child("summary").val();
        if (!sum) return;
        if (statusFilter && String(sum.status).toUpperCase() !== statusFilter) return;
        if (reportType === "LATE" && Number(sum.lateMinutes || 0) <= 0) return;
        if (reportType === "EARLY_DEPARTURE" && Number(sum.earlyDepartureMinutes || 0) <= 0) return;
        if (reportType === "OVERTIME" && Number(sum.overtimeMinutes || 0) <= 0) return;
        if (reportType === "ABSENCE" && sum.status !== "ABSENT") return;

        rows.push({
          date: sum.date,
          storeId: sum.storeId,
          employeeId: sum.employeeId,
          employeeName: sum.employeeName || sum.employeeId,
          status: sum.status,
          workingMinutes: sum.workingMinutes || 0,
          breakMinutes: sum.breakMinutes || 0,
          lateMinutes: sum.lateMinutes || 0,
          earlyDepartureMinutes: sum.earlyDepartureMinutes || 0,
          overtimeMinutes: sum.overtimeMinutes || 0,
          source: sum.source || "",
        });
      });
    });
  });

  const reportId = `rpt_${Date.now()}`;
  const bucket = admin.storage().bucket();
  let fileBuffer: Buffer;
  let ext = "csv";
  let contentType = "text/csv";

  if (format === "XLSX") {
    ext = "xlsx";
    contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    const wb = new ExcelJS.Workbook();
    const ws = wb.addWorksheet(reportType);
    ws.columns = [
      { header: "Date", key: "date", width: 14 },
      { header: "Store ID", key: "storeId", width: 16 },
      { header: "Employee ID", key: "employeeId", width: 16 },
      { header: "Name", key: "employeeName", width: 22 },
      { header: "Status", key: "status", width: 14 },
      { header: "Net Work (m)", key: "workingMinutes", width: 14 },
      { header: "Break (m)", key: "breakMinutes", width: 12 },
      { header: "Late (m)", key: "lateMinutes", width: 12 },
      { header: "Early Dep (m)", key: "earlyDepartureMinutes", width: 14 },
      { header: "OT (m)", key: "overtimeMinutes", width: 12 },
      { header: "Source", key: "source", width: 14 },
    ];
    rows.forEach((r) => ws.addRow(r));
    fileBuffer = Buffer.from(await wb.xlsx.writeBuffer());
  } else if (format === "PDF") {
    ext = "pdf";
    contentType = "application/pdf";
    const lines = rows.map(
      (r) =>
        `${r.date} | ${r.storeId} | ${r.employeeName} | ${r.status} | Work:${r.workingMinutes}m | Late:${r.lateMinutes}m | OT:${r.overtimeMinutes}m`
    );
    fileBuffer = await buildSimplePdfBuffer(
      `Attendance Pro Report — ${reportType} (${startDate} to ${endDate})`,
      lines.length > 0 ? lines : ["No matching records for selected filter."]
    );
  } else {
    const header =
      "date,storeId,employeeId,employeeName,status,workingMinutes,breakMinutes,lateMinutes,earlyDepartureMinutes,overtimeMinutes,source";
    const csvRows = rows.map((r) =>
      [
        r.date,
        r.storeId,
        r.employeeId,
        `"${String(r.employeeName).replace(/"/g, '""')}"`,
        r.status,
        r.workingMinutes,
        r.breakMinutes,
        r.lateMinutes,
        r.earlyDepartureMinutes,
        r.overtimeMinutes,
        r.source,
      ].join(",")
    );
    fileBuffer = Buffer.from([header, ...csvRows].join("\n"), "utf8");
  }

  const file = bucket.file(`reports/${caller.uid}/${reportId}.${ext}`);
  await file.save(fileBuffer, { contentType });
  const [downloadUrl] = await file.getSignedUrl({
    action: "read",
    expires: Date.now() + 24 * 60 * 60 * 1000,
  });

  return {
    reportId,
    format,
    rowCount: rows.length,
    downloadUrl,
    generatedAt: Date.now(),
  };
});

// =============================================================================
// 7. Notifications, Announcements, Audit & Signed Cloudinary Uploads (§0, §9, §16)
// =============================================================================

export const sendPunchNotification = onValueCreated(
  "/attendance/attendanceEvents/{eventId}",
  async (event) => {
    const val = event.data.val();
    if (!val?.employeeId) return;
    const empUid = (await db().ref(`attendance/employees/${val.employeeId}/uid`).get()).val();
    if (!empUid) return;
    await sendPushToUids([String(empUid)], {
      title: `Attendance ${String(val.eventType).replace("_", " ")} Recorded`,
      body: `Verified punch recorded for ${val.date} via ${val.source}.`,
      category: "attendance",
    });
  }
);

export const sendLeaveNotification = onValueCreated(
  "/attendance/leaveRequests/{requestId}",
  async (event) => {
    const val = event.data.val();
    if (!val?.storeId) return;
    const usersSnap = await db().ref("attendance/users").get();
    const reviewerUids: string[] = [];
    usersSnap.forEach((u) => {
      const user = u.val();
      if (
        user.status === "ACTIVE" &&
        (user.role === "super_admin" ||
          ((user.role === "store_admin" || user.role === "manager") &&
            user.storeAssignments?.[val.storeId] === true))
      ) {
        reviewerUids.push(u.key!);
      }
    });
    await sendPushToUids(reviewerUids, {
      title: `New Leave Request: ${val.employeeName}`,
      body: `${val.leaveType} (${val.startDate} to ${val.endDate}) awaits review.`,
      category: "leave",
    });
  }
);

export const sendAnnouncement = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin"]);
  const data = (request.data || {}) as Record<string, any>;
  const title = String(data.title || "").trim();
  const body = String(data.body || "").trim();
  const targetScope = String(data.targetScope || "ALL").toUpperCase();
  const targetIds: string[] = Array.isArray(data.targetIds) ? data.targetIds.map(String) : [];

  if (!title || !body) throw new HttpsError("invalid-argument", "Title and body are required.");
  if (targetScope === "ALL" && caller.role !== "super_admin") {
    throw new HttpsError("permission-denied", "Only Super Admin can broadcast to ALL stores.");
  }

  const ref = db().ref("attendance/announcements").push();
  const announcementId = ref.key || `ann_${Date.now()}`;
  await ref.set({
    announcementId,
    title,
    body,
    targetScope,
    targetIds,
    createdBy: caller.uid,
    createdAt: admin.database.ServerValue.TIMESTAMP,
  });

  const usersSnap = await db().ref("attendance/users").get();
  const recipientUids: string[] = [];
  usersSnap.forEach((u) => {
    const user = u.val();
    if (user.status !== "ACTIVE") return;
    if (targetScope === "ALL") {
      recipientUids.push(u.key!);
    } else if (targetScope === "STORES") {
      const assigned = user.storeAssignments || {};
      if (targetIds.some((id) => assigned[id] === true)) recipientUids.push(u.key!);
    }
  });

  const sentCount = await sendPushToUids(recipientUids, {
    title,
    body,
    category: "announcement",
  });

  await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: "ANNOUNCEMENT_SENT",
    target: `announcements/${announcementId}`,
    afterSummary: `${title} (${targetScope}, delivered=${sentCount})`,
  });

  return { announcementId, delivered: sentCount };
});

export const writeAuditLog = onCall(async (request) => {
  const caller = requireCaller(request, ["super_admin", "store_admin", "manager"]);
  const data = (request.data || {}) as Record<string, any>;
  const logId = await writeAuditEntry({
    actorUid: caller.uid,
    actorRole: caller.role,
    action: String(data.action || "ADMIN_ACTION"),
    target: String(data.target || ""),
    storeId: String(data.storeId || ""),
    beforeSummary: String(data.beforeSummary || ""),
    afterSummary: String(data.afterSummary || ""),
    reason: String(data.reason || ""),
  });
  return { logId };
});

export const signCloudinaryUpload = onCall(async (request) => {
  const caller = requireCaller(request);
  const data = (request.data || {}) as Record<string, any>;
  const folder = String(data.folder || `attendance_pro/private/${caller.employeeId}`);

  const apiKey = process.env.CLOUDINARY_API_KEY;
  const apiSecret = process.env.CLOUDINARY_API_SECRET;
  const cloudName = process.env.CLOUDINARY_CLOUD_NAME || "dlsiskxua";

  if (!apiKey || !apiSecret) {
    throw new HttpsError(
      "failed-precondition",
      "CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET must be configured in Cloud Functions environment."
    );
  }

  const timestamp = Math.floor(Date.now() / 1000);
  const signature = signCloudinaryUploadParams(
    { folder, timestamp, type: "authenticated" },
    apiSecret
  );

  return { cloudName, apiKey, timestamp, folder, signature };
});

function buildSimplePdfBuffer(title: string, lines: string[]): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({ margin: 40 });
    const chunks: Buffer[] = [];
    doc.on("data", (chunk: Buffer) => chunks.push(chunk));
    doc.on("end", () => resolve(Buffer.concat(chunks)));
    doc.on("error", reject);

    doc.fontSize(16).text(title, { underline: true });
    doc.moveDown();
    doc.fontSize(10);
    for (const line of lines) {
      doc.text(line);
      doc.moveDown(0.3);
    }
    doc.end();
  });
}
