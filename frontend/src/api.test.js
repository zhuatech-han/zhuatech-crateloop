// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { api, resetCsrf } from "./api.js";
const response = (value, status = 200) => ({
  ok: status === 200,
  status,
  json: async () => value,
});
test("write requests carry CSRF without storage of session credentials", async () => {
  resetCsrf();
  const calls = [];
  globalThis.fetch = async (path, init) => {
    calls.push([path, init]);
    return response(
      path.endsWith("csrf")
        ? { header: "X-CSRF-TOKEN", token: "test-csrf" }
        : { ok: true },
    );
  };
  await api("/plans", "POST", { title: "测试" });
  assert.equal(calls[1][1].headers["X-CSRF-TOKEN"], "test-csrf");
  assert.equal(calls[1][1].headers.Authorization, undefined);
});
test("expired session resets CSRF bootstrap", async () => {
  resetCsrf();
  let bootstraps = 0;
  globalThis.fetch = async (path) => {
    if (path.endsWith("csrf")) {
      bootstraps++;
      return response({ header: "X-CSRF-TOKEN", token: "test-csrf" });
    }
    return response({ code: "UNAUTHENTICATED" }, 401);
  };
  await assert.rejects(api("/auth/me"), /UNAUTHENTICATED/);
  await assert.rejects(api("/auth/me"), /UNAUTHENTICATED/);
  assert.equal(bootstraps, 2);
});
test("business errors remain actionable without returning response data", async () => {
  resetCsrf();
  globalThis.fetch = async (path) =>
    response(
      path.endsWith("csrf")
        ? { header: "X-CSRF-TOKEN", token: "test-csrf" }
        : { code: "STALE_VERSION" },
      path.endsWith("csrf") ? 200 : 409,
    );
  await assert.rejects(
    api("/plans/1/commands/submit", "POST", { version: 0 }),
    /STALE_VERSION/,
  );
});
