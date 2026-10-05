// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP/JPA事务、伙伴边界和真实数量流转检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PoolIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("crateloop.admin-password", () -> password);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate sql;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, ops, review, warehouse, receiver, partner, other, boundAll, outside;
  long partnerId, otherPartner, poolId, dep;
  String suffix;
  Map<String, Long> roles = new HashMap<>();

  String key() {
    return UUID.randomUUID().toString();
  }

  MockHttpSession login(String name) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", name, "password", password))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  MvcResult request(MockHttpSession who, String method, String path, Object body) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    b.session(who).with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object body) throws Exception {
    var r = request(who, method, path, body);
    assertEquals(
        200, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession who, String method, String path, Object body, int status, String code)
      throws Exception {
    var r = request(who, method, path, body);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }

  void user(String name, String role, long department, Long binding) throws Exception {
    var b =
        new HashMap<String, Object>(
            Map.of(
                "username",
                name + suffix,
                "displayName",
                "TEST " + name,
                "password",
                password,
                "roleId",
                roles.get(role),
                "departmentId",
                department,
                "enabled",
                true));
    if (binding != null) b.put("partnerId", binding);
    ok(admin, "POST", "/admin/users", b);
  }

  Map<String, Object> master(String name, long department) {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "reference",
            "TEST " + key(),
            "name",
            "TEST " + name,
            "kind",
            "CRATE",
            "departmentId",
            department,
            "enabled",
            true));
  }

  @BeforeAll
  void setup() throws Exception {
    suffix = key().substring(0, 8);
    admin = login("admin");
    for (var r : ok(admin, "GET", "/admin/roles", null))
      roles.put(r.path("name").asString(), r.path("id").asLong());
    partnerId = ok(admin, "POST", "/partners", master("partner", 1)).path("id").asLong();
    otherPartner = ok(admin, "POST", "/partners", master("other", 1)).path("id").asLong();
    dep =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST outside")).path("id").asLong();
    user("ops", "周转协调", 1, null);
    user("review", "独立复核", 1, null);
    user("warehouse", "仓库交接", 1, null);
    user("receiver", "仓库交接", 1, null);
    user("partner", "合作伙伴", 1, partnerId);
    user("other", "合作伙伴", 1, otherPartner);
    user("bound", "管理员", 1, otherPartner);
    user("outside", "周转协调", dep, null);
    ops = login("ops" + suffix);
    review = login("review" + suffix);
    warehouse = login("warehouse" + suffix);
    receiver = login("receiver" + suffix);
    partner = login("partner" + suffix);
    other = login("other" + suffix);
    boundAll = login("bound" + suffix);
    outside = login("outside" + suffix);
  }

  @BeforeEach
  void fresh() throws Exception {
    poolId = ok(ops, "POST", "/pools", master("pool", 1)).path("id").asLong();
    long a = adjust("ADD", 100, 100, 0);
    command(ops, "adjustments", a, "submit", Map.of());
    command(review, "adjustments", a, "approve", Map.of());
  }

  JsonNode pool() throws Exception {
    return ok(ops, "GET", "/pools/" + poolId, null).path("pool");
  }

  void counts(long available, long held, long repair, long lost, long retired) throws Exception {
    var p = pool();
    assertEquals(100, p.path("total").asLong());
    assertEquals(available, p.path("available").asLong());
    assertEquals(held, p.path("held").asLong());
    assertEquals(repair, p.path("repair").asLong());
    assertEquals(lost, p.path("lost").asLong());
    assertEquals(retired, p.path("retired").asLong());
  }

  Map<String, Object> moveInput(String kind, long n, long partner) {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "reference",
            "TEST handoff " + key(),
            "kind",
            kind,
            "poolId",
            poolId,
            "partnerId",
            partner,
            "quantity",
            n));
  }

  long move(String kind, long n) throws Exception {
    return ok(ops, "POST", "/movements", moveInput(kind, n, partnerId))
        .path("record")
        .path("id")
        .asLong();
  }

  long adjust(String kind, long n, long good, long retired) throws Exception {
    return ok(
            ops,
            "POST",
            "/adjustments",
            Map.of(
                "requestKey",
                key(),
                "reference",
                "TEST proposal " + key(),
                "kind",
                kind,
                "poolId",
                poolId,
                "quantity",
                n,
                "goodQuantity",
                good,
                "retireQuantity",
                retired))
        .path("record")
        .path("id")
        .asLong();
  }

  Map<String, Object> cmd(String type, long id) throws Exception {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "version",
            ok(admin, "GET", "/" + type + "/" + id, null).path("record").path("version").asLong(),
            "note",
            "TEST physical evidence"));
  }

  JsonNode command(
      MockHttpSession who, String type, long id, String action, Map<String, Object> extra)
      throws Exception {
    var v = cmd(type, id);
    v.putAll(extra);
    return ok(who, "POST", "/" + type + "/" + id + "/commands/" + action, v);
  }

  long issued(long n) throws Exception {
    long id = move("ISSUE", n);
    command(ops, "movements", id, "submit", Map.of());
    command(review, "movements", id, "approve", Map.of());
    command(warehouse, "movements", id, "dispatch", Map.of());
    command(partner, "movements", id, "receive", Map.of("quantity", n));
    return id;
  }

  Map<String, Object> statementInput() {
    return Map.of(
        "requestKey",
        key(),
        "reference",
        "TEST statement " + key(),
        "poolId",
        poolId,
        "partnerId",
        partnerId);
  }

  @Test
  void completeIssueReturnRepairConserves() throws Exception {
    issued(30);
    long r = move("RETURN", 20);
    command(partner, "movements", r, "submit", Map.of());
    command(warehouse, "movements", r, "dispatch", Map.of());
    command(receiver, "movements", r, "receive", Map.of("quantity", 20));
    command(
        review,
        "movements",
        r,
        "inspect",
        Map.of("goodQuantity", 12, "repairQuantity", 5, "retireQuantity", 3));
    long a = adjust("REPAIR", 5, 4, 1);
    command(ops, "adjustments", a, "submit", Map.of());
    command(review, "adjustments", a, "approve", Map.of());
    counts(86, 10, 0, 0, 4);
  }

  @Test
  void issueShortagePreservesRecoveredAndLost() throws Exception {
    long id = move("ISSUE", 10);
    command(ops, "movements", id, "submit", Map.of());
    command(review, "movements", id, "approve", Map.of());
    command(warehouse, "movements", id, "dispatch", Map.of());
    command(partner, "movements", id, "receive", Map.of("quantity", 7));
    assertEquals(3, pool().path("outTransit").asLong());
    command(review, "movements", id, "resolve", Map.of("recoveredQuantity", 2, "lostQuantity", 1));
    counts(90, 9, 0, 1, 0);
  }

  @Test
  void returnShortageRetainsPendingInspection() throws Exception {
    issued(20);
    long id = move("RETURN", 10);
    command(partner, "movements", id, "submit", Map.of());
    command(warehouse, "movements", id, "dispatch", Map.of());
    command(receiver, "movements", id, "receive", Map.of("quantity", 7));
    assertEquals(7, pool().path("inspection").asLong());
    fail(
        review,
        "POST",
        "/movements/" + id + "/commands/inspect",
        cmd("movements", id),
        409,
        "INVALID_STATE");
    command(review, "movements", id, "resolve", Map.of("backToPartner", 2, "lostQuantity", 1));
    command(
        review,
        "movements",
        id,
        "inspect",
        Map.of("goodQuantity", 5, "repairQuantity", 2, "retireQuantity", 0));
    counts(85, 12, 2, 1, 0);
  }

  @Test
  void zeroReceiptCanResolveAllShortage() throws Exception {
    issued(5);
    long id = move("RETURN", 5);
    command(partner, "movements", id, "submit", Map.of());
    command(warehouse, "movements", id, "dispatch", Map.of());
    command(receiver, "movements", id, "receive", Map.of("quantity", 0));
    var r =
        command(review, "movements", id, "resolve", Map.of("backToPartner", 3, "lostQuantity", 2));
    assertEquals("CLOSED", r.path("record").path("status").asString());
    counts(95, 3, 0, 2, 0);
  }

  @Test
  void cancelledReservationsRestoreExactly() throws Exception {
    long i = move("ISSUE", 15);
    command(ops, "movements", i, "submit", Map.of());
    command(review, "movements", i, "approve", Map.of());
    command(ops, "movements", i, "cancel", Map.of());
    counts(100, 0, 0, 0, 0);
    issued(10);
    long r = move("RETURN", 6);
    command(partner, "movements", r, "submit", Map.of());
    assertEquals(6, pool().path("returnReserved").asLong());
    command(partner, "movements", r, "cancel", Map.of());
    counts(90, 10, 0, 0, 0);
  }

  @Test
  void independentReviewAndSignaturesEnforced() throws Exception {
    long a =
        ok(
                admin,
                "POST",
                "/adjustments",
                Map.of(
                    "requestKey",
                    key(),
                    "reference",
                    "TEST own " + key(),
                    "kind",
                    "ADD",
                    "poolId",
                    poolId,
                    "quantity",
                    1))
            .path("record")
            .path("id")
            .asLong();
    command(admin, "adjustments", a, "submit", Map.of());
    fail(
        admin,
        "POST",
        "/adjustments/" + a + "/commands/approve",
        cmd("adjustments", a),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
    long i = move("ISSUE", 1);
    command(ops, "movements", i, "submit", Map.of());
    command(review, "movements", i, "approve", Map.of());
    command(warehouse, "movements", i, "dispatch", Map.of());
    var v = cmd("movements", i);
    v.put("quantity", 1);
    fail(
        warehouse,
        "POST",
        "/movements/" + i + "/commands/receive",
        v,
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void retryDoesNotDoublePostAndKeyCannotChange() throws Exception {
    long i = move("ISSUE", 10);
    command(ops, "movements", i, "submit", Map.of());
    var v = cmd("movements", i);
    ok(review, "POST", "/movements/" + i + "/commands/approve", v);
    ok(review, "POST", "/movements/" + i + "/commands/approve", v);
    assertEquals(10, pool().path("reserved").asLong());
    v.put("note", "TEST changed");
    fail(review, "POST", "/movements/" + i + "/commands/approve", v, 409, "REQUEST_KEY_REUSED");
  }

  @Test
  void staleVersionsAndInvalidKeysRejected() throws Exception {
    long i = move("ISSUE", 1);
    var v = cmd("movements", i);
    command(ops, "movements", i, "submit", Map.of());
    fail(ops, "POST", "/movements/" + i + "/commands/cancel", v, 409, "STALE_VERSION");
    v = cmd("movements", i);
    v.put("requestKey", "bad");
    fail(ops, "POST", "/movements/" + i + "/commands/cancel", v, 400, "INVALID_REQUEST_KEY");
  }

  @Test
  void concurrentApprovalCannotOverAllocate() throws Exception {
    long a = move("ISSUE", 70), b = move("ISSUE", 70);
    command(ops, "movements", a, "submit", Map.of());
    command(ops, "movements", b, "submit", Map.of());
    var ca = cmd("movements", a);
    var cb = cmd("movements", b);
    try (var ex = Executors.newFixedThreadPool(2)) {
      var f =
          ex.submit(
              () ->
                  request(review, "POST", "/movements/" + a + "/commands/approve", ca)
                      .getResponse()
                      .getStatus());
      var g =
          ex.submit(
              () ->
                  request(review, "POST", "/movements/" + b + "/commands/approve", cb)
                      .getResponse()
                      .getStatus());
      var values = new ArrayList<>(List.of(f.get(), g.get()));
      Collections.sort(values);
      assertEquals(List.of(200, 409), values);
    }
    assertEquals(70, pool().path("reserved").asLong());
    assertEquals(30, pool().path("available").asLong());
  }

  @Test
  void returnsCannotExceedRemainingCustody() throws Exception {
    issued(10);
    long a = move("RETURN", 6), b = move("RETURN", 5);
    command(partner, "movements", a, "submit", Map.of());
    fail(
        partner,
        "POST",
        "/movements/" + b + "/commands/submit",
        cmd("movements", b),
        409,
        "INSUFFICIENT_QUANTITY");
    assertEquals(4, pool().path("held").asLong());
  }

  @Test
  void fractionalAndNegativeQuantitiesRejected() throws Exception {
    var v = moveInput("ISSUE", 1, partnerId);
    v.put("quantity", 1.5);
    fail(ops, "POST", "/movements", v, 400, "INVALID_INPUT");
    v.put("quantity", -1);
    fail(ops, "POST", "/movements", v, 400, "INVALID_QUANTITY");
    v.put("quantity", 1000001);
    fail(ops, "POST", "/movements", v, 400, "INVALID_QUANTITY");
  }

  @Test
  void badClassificationRollsBackAllBuckets() throws Exception {
    issued(8);
    long i = move("RETURN", 8);
    command(partner, "movements", i, "submit", Map.of());
    command(warehouse, "movements", i, "dispatch", Map.of());
    command(receiver, "movements", i, "receive", Map.of("quantity", 8));
    var v = cmd("movements", i);
    v.putAll(Map.of("goodQuantity", 7, "repairQuantity", 2, "retireQuantity", 0));
    fail(review, "POST", "/movements/" + i + "/commands/inspect", v, 400, "QUANTITY_MISMATCH");
    assertEquals(8, pool().path("inspection").asLong());
    assertEquals(92, pool().path("available").asLong());
  }

  @Test
  void boundAllHasNoPoolOrOtherPartyData() throws Exception {
    long i = issued(2);
    fail(boundAll, "GET", "/pools/" + poolId, null, 403, "STAFF_ONLY");
    fail(boundAll, "GET", "/movements/" + i, null, 403, "OUT_OF_SCOPE");
    fail(boundAll, "GET", "/admin/users", null, 403, "STAFF_ONLY");
    fail(boundAll, "GET", "/audit", null, 403, "STAFF_ONLY");
    var options = ok(boundAll, "GET", "/options", null);
    for (var p : options.path("pools")) {
      assertFalse(p.has("total"));
      assertFalse(p.has("available"));
    }
    assertEquals(1, options.path("partners").size());
    var d = ok(boundAll, "GET", "/dashboard", null);
    assertFalse(d.has("total"));
    assertFalse(d.has("totals"));
    fail(boundAll, "GET", "/pools/" + poolId + "/report.json", null, 403, "STAFF_ONLY");
  }

  @Test
  void departmentsAndPermissionsAreEnforced() throws Exception {
    long i = move("ISSUE", 1);
    fail(outside, "GET", "/movements/" + i, null, 403, "OUT_OF_SCOPE");
    fail(partner, "POST", "/pools", master("illegal", 1), 403, "STAFF_ONLY");
    fail(ops, "GET", "/admin/users", null, 403, "FORBIDDEN");
    assertEquals(401, mvc.perform(get("/api/movements")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/movements").session(ops).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void statementFreezesAndPartnerConfirms() throws Exception {
    issued(6);
    long s = ok(ops, "POST", "/statements", statementInput()).path("record").path("id").asLong();
    var r = command(partner, "statements", s, "confirm", Map.of());
    assertEquals("CONFIRMED", r.path("record").path("status").asString());
    assertEquals(6, r.path("record").path("held").asLong());
    fail(
        ops,
        "POST",
        "/statements/" + s + "/commands/cancel",
        cmd("statements", s),
        409,
        "INVALID_STATE");
  }

  @Test
  void statementsRejectOutstandingHandoffsAndNewLedger() throws Exception {
    long i = move("ISSUE", 3);
    command(ops, "movements", i, "submit", Map.of());
    command(review, "movements", i, "approve", Map.of());
    fail(ops, "POST", "/statements", statementInput(), 409, "PENDING_HANDOFF");
    command(warehouse, "movements", i, "dispatch", Map.of());
    command(partner, "movements", i, "receive", Map.of("quantity", 3));
    long s = ok(ops, "POST", "/statements", statementInput()).path("record").path("id").asLong();
    issued(1);
    fail(
        partner,
        "POST",
        "/statements/" + s + "/commands/confirm",
        cmd("statements", s),
        409,
        "STALE_STATEMENT");
    command(ops, "statements", s, "cancel", Map.of());
  }

  @Test
  void disputedStatementRetainsSnapshotAndCanCancel() throws Exception {
    issued(1);
    long s = ok(ops, "POST", "/statements", statementInput()).path("record").path("id").asLong();
    fail(ops, "POST", "/statements", statementInput(), 409, "OPEN_STATEMENT");
    var snap =
        ok(partner, "GET", "/statements/" + s, null).path("record").path("snapshot").asString();
    command(partner, "statements", s, "dispute", Map.of());
    var result = command(ops, "statements", s, "cancel", Map.of());
    assertEquals(snap, result.path("record").path("snapshot").asString());
  }

  @Test
  void disabledPoolsStillAcceptReturns() throws Exception {
    issued(2);
    var p = pool();
    var v = master("disabled", 1);
    v.put("reference", p.path("reference").asString());
    v.put("version", p.path("version").asLong());
    v.put("enabled", false);
    ok(ops, "PUT", "/pools/" + poolId, v);
    fail(ops, "POST", "/movements", moveInput("ISSUE", 1, partnerId), 409, "DISABLED_RESOURCE");
    long r = move("RETURN", 2);
    command(partner, "movements", r, "submit", Map.of());
  }

  @Test
  void immutableMasterAndReferencedDeletes() throws Exception {
    var p = pool();
    var v = master("rename", 1);
    v.put("version", p.path("version").asLong());
    fail(ops, "PUT", "/pools/" + poolId, v, 409, "IMMUTABLE_IDENTITY");
    fail(ops, "DELETE", "/pools/" + poolId, null, 409, "CONFLICT");
  }

  @Test
  void filteredPagesAndExportPreserveScope() throws Exception {
    long id = issued(4);
    var r =
        ok(
            partner,
            "GET",
            "/movements?search=" + id + "&size=1&page=0&sort=reference&kind=ISSUE",
            null);
    assertTrue(r.path("items").size() <= 1);
    var e = request(partner, "GET", "/movements/" + id + "/report.json", null);
    assertEquals(200, e.getResponse().getStatus());
    assertEquals("no-store", e.getResponse().getHeader("Cache-Control"));
    assertFalse(e.getResponse().getContentAsString().contains("zhuatech2"));
    fail(other, "GET", "/movements/" + id + "/report.json", null, 403, "OUT_OF_SCOPE");
    fail(ops, "GET", "/movements?size=101", null, 400, "INVALID_PAGE");
  }

  @Test
  void onlyDraftEditableAndLedgerHasNoEditEndpoint() throws Exception {
    long id = issued(2);
    var v = moveInput("ISSUE", 3, partnerId);
    v.put(
        "version",
        ok(ops, "GET", "/movements/" + id, null).path("record").path("version").asLong());
    fail(ops, "PUT", "/movements/" + id, v, 409, "INVALID_STATE");
    assertNotEquals(
        200, request(admin, "PUT", "/ledger/1", Map.of("quantity", 1)).getResponse().getStatus());
  }

  @Test
  void lastUnboundAdministratorCannotBeDisabled() throws Exception {
    var a = ok(admin, "GET", "/admin/users", null).get(0);
    var v =
        new HashMap<String, Object>(
            Map.of(
                "username",
                a.path("username").asString(),
                "displayName",
                "TEST administrator",
                "roleId",
                a.path("roleId").asLong(),
                "departmentId",
                1,
                "enabled",
                false));
    fail(admin, "PUT", "/admin/users/" + a.path("id").asLong(), v, 409, "LAST_ADMIN");
    assertEquals(200, request(admin, "GET", "/admin/users", null).getResponse().getStatus());
  }

  @Test
  void corruptedPartnerSubledgerBlocksPosting() throws Exception {
    sql.update("update pool set available=available-1,held=1 where id=?", poolId);
    try {
      fail(ops, "POST", "/movements", moveInput("ISSUE", 1, partnerId), 409, "BALANCE_INVARIANT");
    } finally {
      sql.update("update pool set available=available+1,held=0 where id=?", poolId);
    }
    counts(100, 0, 0, 0, 0);
  }

  @Test
  void selfScopeOnlySeesOwnCreatedRows() throws Exception {
    long role =
        ok(
                admin,
                "POST",
                "/admin/roles",
                Map.of(
                    "name",
                    "TEST self " + key(),
                    "scope",
                    "SELF",
                    "permissions",
                    List.of(
                        "pool.read",
                        "catalog.write",
                        "movement.read",
                        "movement.write",
                        "dashboard",
                        "export",
                        "audit")))
            .path("id")
            .asLong();
    String name = "self" + key().substring(0, 8);
    ok(
        admin,
        "POST",
        "/admin/users",
        Map.of(
            "username",
            name,
            "displayName",
            "TEST self",
            "password",
            password,
            "roleId",
            role,
            "departmentId",
            1,
            "enabled",
            true));
    var who = login(name);
    fail(who, "GET", "/pools/" + poolId, null, 403, "OUT_OF_SCOPE");
    assertEquals(0, ok(who, "GET", "/pools", null).path("total").asInt());
    long own = ok(who, "POST", "/pools", master("own", 1)).path("id").asLong();
    assertEquals(1, ok(who, "GET", "/pools", null).path("total").asInt());
    assertEquals(own, ok(who, "GET", "/pools/" + own, null).path("pool").path("id").asLong());
  }

  @Test
  void permissionChangeImmediatelyAffectsExistingSession() throws Exception {
    long role =
        ok(
                admin,
                "POST",
                "/admin/roles",
                Map.of(
                    "name",
                    "TEST revocation " + key(),
                    "scope",
                    "DEPARTMENT",
                    "permissions",
                    List.of("pool.read")))
            .path("id")
            .asLong();
    String name = "revoke" + key().substring(0, 8);
    ok(
        admin,
        "POST",
        "/admin/users",
        Map.of(
            "username",
            name,
            "displayName",
            "TEST revoke",
            "password",
            password,
            "roleId",
            role,
            "departmentId",
            1,
            "enabled",
            true));
    var who = login(name);
    ok(who, "GET", "/pools/" + poolId, null);
    ok(
        admin,
        "PUT",
        "/admin/roles/" + role,
        Map.of("name", "TEST revoked " + key(), "scope", "DEPARTMENT", "permissions", List.of()));
    fail(who, "GET", "/pools/" + poolId, null, 403, "FORBIDDEN");
  }

  @Test
  void passwordChangeInvalidatesAllPreviousSessions() throws Exception {
    String name = "passwd" + key().substring(0, 8);
    ok(
        admin,
        "POST",
        "/admin/users",
        Map.of(
            "username",
            name,
            "displayName",
            "TEST password",
            "password",
            password,
            "roleId",
            roles.get("周转协调"),
            "departmentId",
            1,
            "enabled",
            true));
    var a = login(name);
    var b = login(name);
    fail(
        a,
        "POST",
        "/auth/password",
        Map.of("oldPassword", "incorrect", "newPassword", password),
        400,
        "OLD_PASSWORD_INVALID");
    ok(a, "POST", "/auth/password", Map.of("oldPassword", password, "newPassword", "Aa9" + key()));
    fail(b, "GET", "/auth/me", null, 401, "UNAUTHENTICATED");
  }

  @Test
  void unusedMasterDeletionAndManualRetirementHaveRealEffects() throws Exception {
    long p = ok(ops, "POST", "/partners", master("unused", 1)).path("id").asLong();
    ok(ops, "DELETE", "/partners/" + p, null);
    long a = adjust("RETIRE", 3, 0, 3);
    command(ops, "adjustments", a, "submit", Map.of());
    command(review, "adjustments", a, "approve", Map.of());
    counts(97, 0, 0, 0, 3);
    long b = adjust("REPAIR", 1, 1, 0);
    command(ops, "adjustments", b, "submit", Map.of());
    fail(
        review,
        "POST",
        "/adjustments/" + b + "/commands/approve",
        cmd("adjustments", b),
        409,
        "INSUFFICIENT_QUANTITY");
    counts(97, 0, 0, 0, 3);
  }
}
