import * as admin from "firebase-admin";
import * as crypto from "crypto";

export async function writeAuditEntry(entry: {
  actorUid: string;
  actorRole: string;
  action: string;
  target: string;
  storeId?: string;
  beforeSummary?: string;
  afterSummary?: string;
  reason?: string;
}): Promise<string> {
  const ref = admin.database().ref("attendance/auditLogs").push();
  const logId = ref.key || `log_${Date.now()}`;
  await ref.set({
    logId,
    actorUid: entry.actorUid,
    actorRole: entry.actorRole,
    action: entry.action,
    target: entry.target,
    storeId: entry.storeId || "",
    beforeSummary: entry.beforeSummary || "",
    afterSummary: entry.afterSummary || "",
    reason: entry.reason || "",
    serverTimestamp: admin.database.ServerValue.TIMESTAMP,
  });
  return logId;
}

export async function sendPushToUids(
  uids: string[],
  notification: { title: string; body: string; category: string }
): Promise<number> {
  const db = admin.database();
  const tokens: string[] = [];

  for (const uid of Array.from(new Set(uids))) {
    const snap = await db.ref(`attendance/users/${uid}/fcmTokens`).get();
    if (!snap.exists()) continue;
    snap.forEach((child) => {
      const tok = child.child("token").val();
      if (typeof tok === "string" && tok.length > 10) tokens.push(tok);
    });
  }

  if (tokens.length === 0) return 0;

  const response = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {
      title: notification.title,
      body: notification.body,
    },
    data: {
      category: notification.category,
      title: notification.title,
      body: notification.body,
    },
  });
  return response.successCount;
}

export function getChallengeSigningSecret(): string {
  return (
    process.env.SCANNER_HMAC_SECRET ||
    process.env.CLOUDINARY_API_SECRET ||
    "attendance-pro-dev-fallback-rotate-in-prod"
  );
}

export function signChallengePayload(parts: string[]): string {
  return crypto
    .createHmac("sha256", getChallengeSigningSecret())
    .update(parts.join("|"))
    .digest("hex")
    .slice(0, 32);
}

export function signCloudinaryUploadParams(
  params: Record<string, string | number>,
  apiSecret: string
): string {
  const canonical = Object.keys(params)
    .sort()
    .map((k) => `${k}=${params[k]}`)
    .join("&");
  return crypto
    .createHash("sha1")
    .update(`${canonical}${apiSecret}`)
    .digest("hex");
}
