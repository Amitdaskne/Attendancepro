import * as admin from "firebase-admin";
import { CallableRequest, HttpsError } from "firebase-functions/v2/https";

export type UserRole = "super_admin" | "store_admin" | "manager" | "employee";

export interface CallerContext {
  uid: string;
  role: UserRole;
  employeeId: string;
  storeIds: string[];
}

export function requireCaller(
  request: CallableRequest<unknown>,
  allowedRoles?: UserRole[]
): CallerContext {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const token = request.auth.token;
  if (token.disabled === true) {
    throw new HttpsError("permission-denied", "Account is disabled.");
  }

  const role = token.role as UserRole | undefined;
  if (!role || !["super_admin", "store_admin", "manager", "employee"].includes(role)) {
    throw new HttpsError("permission-denied", "Verified role claim missing.");
  }

  if (allowedRoles && !allowedRoles.includes(role)) {
    throw new HttpsError(
      "permission-denied",
      `Role '${role}' is not permitted to perform this operation.`
    );
  }

  const rawStores = token.storeIds;
  const storeIds: string[] = Array.isArray(rawStores)
    ? rawStores.map(String)
    : rawStores && typeof rawStores === "object"
      ? Object.keys(rawStores as Record<string, unknown>).filter(
          (k) => (rawStores as Record<string, unknown>)[k] === true
        )
      : [];

  return {
    uid: request.auth.uid,
    role,
    employeeId: String(token.employeeId || request.auth.uid),
    storeIds,
  };
}

export async function assertStoreScope(caller: CallerContext, storeId: string): Promise<void> {
  if (caller.role === "super_admin") return;
  if (caller.storeIds.includes(storeId)) return;

  // Fallback check against RTDB storeAssignments
  const snap = await admin
    .database()
    .ref(`attendance/users/${caller.uid}/storeAssignments/${storeId}`)
    .get();
  if (snap.val() === true) return;

  throw new HttpsError(
    "permission-denied",
    `Caller does not have access to store '${storeId}'.`
  );
}
