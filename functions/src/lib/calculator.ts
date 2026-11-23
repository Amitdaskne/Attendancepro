export interface ShiftDef {
  shiftId: string;
  startTime: string; // HH:mm
  endTime: string;   // HH:mm
  graceMinutes: number;
  minWorkingMinutes: number;
  halfDayMinutes: number;
  overtimeThresholdMinutes: number;
  overnight: boolean;
}

export interface EventRecord {
  eventId: string;
  eventType: string;
  serverTimestamp: number;
  source?: string;
}

export interface SummaryCalc {
  checkIn: number | null;
  checkOut: number | null;
  breakMinutes: number;
  workingMinutes: number;
  lateMinutes: number;
  earlyDepartureMinutes: number;
  overtimeMinutes: number;
  status: string;
  workState: string;
  missingCheckoutException: boolean;
}

export function haversineMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const R = 6371000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) *
      Math.cos(toRad(lat2)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function validateStateTransition(
  currentState: string,
  eventType: string
): { ok: boolean; nextState: string; error?: string } {
  switch (eventType) {
    case "CHECK_IN":
      if (currentState === "NOT_CHECKED_IN") return { ok: true, nextState: "WORKING" };
      return { ok: false, nextState: currentState, error: "Duplicate check-in rejected." };
    case "BREAK_START":
      if (currentState === "WORKING") return { ok: true, nextState: "ON_BREAK" };
      return { ok: false, nextState: currentState, error: "Cannot start break unless working." };
    case "BREAK_END":
      if (currentState === "ON_BREAK") return { ok: true, nextState: "WORKING" };
      return { ok: false, nextState: currentState, error: "No active break to end." };
    case "CHECK_OUT":
      if (currentState === "WORKING") return { ok: true, nextState: "COMPLETED" };
      if (currentState === "ON_BREAK") {
        return { ok: false, nextState: currentState, error: "End active break before checking out." };
      }
      if (currentState === "NOT_CHECKED_IN") {
        return { ok: false, nextState: currentState, error: "Cannot check out before check-in." };
      }
      return { ok: false, nextState: currentState, error: "Already checked out." };
    case "ADMIN_CORRECTION":
    case "ADMIN_OVERRIDE":
      return { ok: true, nextState: "COMPLETED" };
    default:
      return { ok: false, nextState: currentState, error: `Unknown event type: ${eventType}` };
  }
}

export function parseShiftBoundaryEpoch(dateIso: string, hhmm: string): number {
  const [hStr, mStr] = (hhmm || "09:00").split(":");
  const h = Number(hStr) || 9;
  const m = Number(mStr) || 0;
  const d = new Date(`${dateIso}T00:00:00.000Z`);
  d.setUTCHours(h, m, 0, 0);
  return d.getTime();
}

export function computeSummaryFromEvents(
  dateIso: string,
  shift: ShiftDef,
  events: EventRecord[],
  opts: {
    isHoliday?: boolean;
    isWeeklyOff?: boolean;
    hasApprovedLeave?: boolean;
    dayClosed?: boolean;
  } = {}
): SummaryCalc {
  const sorted = [...events].sort((a, b) => a.serverTimestamp - b.serverTimestamp);
  let checkIn: number | null = null;
  let checkOut: number | null = null;
  let activeBreakStart: number | null = null;
  let totalBreakMs = 0;
  let workState = "NOT_CHECKED_IN";

  for (const ev of sorted) {
    if (ev.eventType === "CHECK_IN") {
      if (checkIn === null) {
        checkIn = ev.serverTimestamp;
        workState = "WORKING";
      }
    } else if (ev.eventType === "BREAK_START") {
      if (workState === "WORKING" && activeBreakStart === null) {
        activeBreakStart = ev.serverTimestamp;
        workState = "ON_BREAK";
      }
    } else if (ev.eventType === "BREAK_END") {
      if (workState === "ON_BREAK" && activeBreakStart !== null && ev.serverTimestamp >= activeBreakStart) {
        totalBreakMs += ev.serverTimestamp - activeBreakStart;
        activeBreakStart = null;
        workState = "WORKING";
      }
    } else if (ev.eventType === "CHECK_OUT") {
      if (checkIn !== null && ev.serverTimestamp >= checkIn) {
        if (activeBreakStart !== null && ev.serverTimestamp >= activeBreakStart) {
          totalBreakMs += ev.serverTimestamp - activeBreakStart;
          activeBreakStart = null;
        }
        checkOut = ev.serverTimestamp;
        workState = "COMPLETED";
      }
    } else if (ev.eventType === "ADMIN_CORRECTION" || ev.eventType === "ADMIN_OVERRIDE") {
      if (checkIn === null) {
        checkIn = ev.serverTimestamp;
        workState = "WORKING";
      } else {
        checkOut = ev.serverTimestamp;
        workState = "COMPLETED";
      }
    }
  }

  const shiftStartMs = parseShiftBoundaryEpoch(dateIso, shift.startTime);
  let shiftEndMs = parseShiftBoundaryEpoch(dateIso, shift.endTime);
  if (shift.overnight && shiftEndMs <= shiftStartMs) {
    shiftEndMs += 24 * 60 * 60 * 1000;
  }

  const breakMinutes = Math.floor(totalBreakMs / 60000);
  const missingCheckoutException = checkIn !== null && checkOut === null && Boolean(opts.dayClosed);
  if (missingCheckoutException) {
    workState = "EXCEPTION";
  }

  const grossMinutes =
    checkIn !== null && checkOut !== null && checkOut >= checkIn
      ? Math.floor((checkOut - checkIn) / 60000)
      : 0;
  const workingMinutes = Math.max(0, grossMinutes - breakMinutes);

  const lateMinutes =
    checkIn !== null
      ? Math.max(0, Math.floor((checkIn - (shiftStartMs + shift.graceMinutes * 60000)) / 60000))
      : 0;

  const earlyDepartureMinutes =
    checkOut !== null ? Math.max(0, Math.floor((shiftEndMs - checkOut) / 60000)) : 0;

  const overtimeMinutes =
    checkOut !== null && shift.overtimeThresholdMinutes > 0
      ? Math.max(0, workingMinutes - shift.overtimeThresholdMinutes)
      : 0;

  let status = "ABSENT";
  if (missingCheckoutException) status = "EXCEPTION";
  else if (checkIn === null && opts.hasApprovedLeave) status = "ON_LEAVE";
  else if (checkIn === null && opts.isHoliday) status = "HOLIDAY";
  else if (checkIn === null && opts.isWeeklyOff) status = "WEEKLY_OFF";
  else if (checkIn === null) status = "ABSENT";
  else if (checkOut === null) status = lateMinutes > 0 ? "LATE_WORKING" : "WORKING";
  else if (workingMinutes >= shift.minWorkingMinutes) status = lateMinutes > 0 ? "LATE" : "PRESENT";
  else if (workingMinutes >= shift.halfDayMinutes) status = "HALF_DAY";
  else status = "ABSENT";

  return {
    checkIn,
    checkOut,
    breakMinutes,
    workingMinutes,
    lateMinutes,
    earlyDepartureMinutes,
    overtimeMinutes,
    status,
    workState,
    missingCheckoutException,
  };
}

export function computeNetPay(params: {
  baseAmount: number;
  allowances: number;
  overtimePay: number;
  bonus: number;
  deductions: number;
  unpaidLeaveDeduction: number;
}): number {
  return Math.max(
    0,
    Number(
      (
        params.baseAmount +
        params.allowances +
        params.overtimePay +
        params.bonus -
        params.deductions -
        params.unpaidLeaveDeduction
      ).toFixed(2)
    )
  );
}
