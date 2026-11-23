const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const rulesPath = path.resolve(__dirname, "../../database.rules.json");
const rulesDoc = JSON.parse(fs.readFileSync(rulesPath, "utf8"));

test("RTDB rules are scoped strictly under /attendance/ only (§10)", () => {
  const rootKeys = Object.keys(rulesDoc.rules);
  assert.deepEqual(rootKeys, ["attendance"]);
  assert.equal(rulesDoc.rules.attendance[".read"], false);
  assert.equal(rulesDoc.rules.attendance[".write"], false);
});

test("Client writes are strictly blocked on immutable backend-owned nodes", () => {
  const att = rulesDoc.rules.attendance;
  assert.equal(att.attendanceEvents[".write"], false);
  assert.equal(att.attendance.$storeId.$employeeId[".write"], false);
  assert.equal(att.scannerChallenges[".write"], false);
  assert.equal(att.scannerChallenges[".read"], false);
  assert.equal(att.auditLogs[".write"], false);
  assert.equal(att.payroll[".write"], false);
  assert.equal(att.salarySlips.$employeeId[".write"], false);
  assert.equal(att.devices[".write"], false);
  assert.equal(att.statistics.$storeId[".write"], false);
  assert.equal(att.leaveBalances.$employeeId[".write"], false);
});

test("salaryPrivate is readable only by super_admin or self and never client-writable", () => {
  const salaryRule = rulesDoc.rules.attendance.employees.$employeeId.salaryPrivate;
  assert.equal(salaryRule[".write"], false);
  assert.match(salaryRule[".read"], /super_admin/);
  assert.match(salaryRule[".read"], /auth\.token\.employeeId === \$employeeId/);
  assert.doesNotMatch(salaryRule[".read"], /store_admin|manager/);
});

test("Store-scoped nodes enforce storeAssignments and block disabled accounts", () => {
  const storeRead = rulesDoc.rules.attendance.stores.$storeId[".read"];
  assert.match(storeRead, /auth\.token\.disabled !== true/);
  assert.match(storeRead, /storeAssignments/);
});
