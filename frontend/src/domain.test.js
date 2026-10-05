// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { actions, payload } from "./domain.js";
import { commandFields } from "./forms.js";
const me = {
  id: 5,
  partnerId: null,
  permissions: [
    "movement.write",
    "issue.approve",
    "dispatch",
    "receive",
    "shortage.review",
    "quality.review",
    "statement.write",
    "statement.confirm",
    "adjustment.write",
    "adjustment.approve",
  ],
};
test("Independent approval hides own request", () => {
  assert.deepEqual(
    actions("movements", { status: "SUBMITTED", createdBy: 5 }, me),
    ["cancel"],
  );
  assert.ok(
    actions("movements", { status: "SUBMITTED", createdBy: 1 }, me).includes(
      "approve",
    ),
  );
});
test("Bound ALL account cannot dispatch or QC", () => {
  assert.deepEqual(
    actions(
      "movements",
      { status: "RESERVED", kind: "ISSUE" },
      { ...me, partnerId: 1 },
    ),
    [],
  );
  assert.deepEqual(
    actions(
      "movements",
      { status: "QC_PENDING", receivedBy: 1 },
      { ...me, partnerId: 1 },
    ),
    [],
  );
});
test("Signer cannot receive own dispatch and shortage reviewer is independent", () => {
  assert.deepEqual(
    actions(
      "movements",
      { status: "DISPATCHED", kind: "ISSUE", dispatchedBy: 5 },
      me,
    ),
    [],
  );
  assert.deepEqual(
    actions(
      "movements",
      { status: "DISPUTED", createdBy: 1, dispatchedBy: 2, receivedBy: 5 },
      me,
    ),
    [],
  );
  assert.deepEqual(
    actions(
      "movements",
      { status: "DISPUTED", createdBy: 1, dispatchedBy: 2, receivedBy: 3 },
      me,
    ),
    ["resolve"],
  );
});
test("Statement confirmation and adjustment approval require another actor", () => {
  assert.deepEqual(
    actions("statements", { status: "OPEN", createdBy: 5 }, me),
    ["cancel"],
  );
  assert.deepEqual(
    actions("adjustments", { status: "SUBMITTED", createdBy: 5 }, me),
    ["cancel"],
  );
  assert.deepEqual(
    actions("statements", { status: "CONFIRMED", createdBy: 1 }, me),
    [],
  );
});
test("Payload retains fractions for rejection rather than silently rounding", () => {
  assert.deepEqual(
    payload(
      {
        quantity: "1.5",
        partnerId: "",
        enabled: false,
        permissions: ["receive"],
      },
      [
        ["quantity", "", "", "integer"],
        ["partnerId", "", "", "id"],
        ["enabled", "", "", "boolean"],
        ["permissions", "", "", "permissions"],
      ],
    ),
    {
      quantity: 1.5,
      partnerId: null,
      enabled: false,
      permissions: ["receive"],
    },
  );
});
test("Shortage and QC forms collect the correct classification", () => {
  assert.equal(
    commandFields("resolve", { kind: "RETURN" })[0][0],
    "backToPartner",
  );
  assert.equal(
    commandFields("resolve", { kind: "ISSUE" })[0][0],
    "recoveredQuantity",
  );
  assert.equal(commandFields("inspect", {}).length, 4);
});
