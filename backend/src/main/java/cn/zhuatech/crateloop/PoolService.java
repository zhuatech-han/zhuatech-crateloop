// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 资产池、伙伴子账、实物交接与对账的事务边界；所有数量变更追加流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PoolService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper mapper;

  public PoolService(Store db, AccessService access, Clock clock, ObjectMapper mapper) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.mapper = mapper;
  }

  /** 主数据只接收身份信息，禁止客户端直接写入资产量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record MasterInput(
      String requestKey,
      Long version,
      String reference,
      String name,
      String kind,
      Long departmentId,
      Boolean enabled) {}

  /** 一张交接单仅对应一个池、一位伙伴与整数数量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record MovementInput(
      String requestKey,
      Long version,
      String reference,
      String kind,
      Long poolId,
      Long partnerId,
      Long quantity) {}

  /** 数量提案冻结新增、维修释放及报废分类，审核时检查实际余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record AdjustmentInput(
      String requestKey,
      Long version,
      String reference,
      String kind,
      Long poolId,
      Long quantity,
      Long goodQuantity,
      Long retireQuantity) {}

  /** 当前余额对账；不接受任意历史金额或数量覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record StatementInput(String requestKey, String reference, Long poolId, Long partnerId) {}

  /** 版本与UUID绑定全命令载荷；证据说明为人工核实编号及文字。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      String requestKey,
      Long version,
      String note,
      Long quantity,
      Long recoveredQuantity,
      Long lostQuantity,
      Long backToPartner,
      Long goodQuantity,
      Long repairQuantity,
      Long retireQuantity) {}

  /** 与管理服务同序持锁并刷新身份，避免等待锁期间授权变化失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void lock() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.current();
  }

  /** 已绑定伙伴优先于ALL，禁止内部管理、资产新增与库存操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void staff() {
    if (access.current().partnerId != null) throw new Problem(403, "STAFF_ONLY");
  }

  private boolean visible(Long department, Long partner, Long creator) {
    var a = access.current();
    if (a.partnerId != null)
      return Objects.equals(a.partnerId, partner) && Objects.equals(a.departmentId, department);
    return access.visible(department)
        && (!access.role().scope.equals("SELF") || Objects.equals(a.id, creator));
  }

  private Pool pool(Long id) {
    var p = db.get(Pool.class, id);
    if (!visible(p.departmentId, null, p.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
    return p;
  }

  private Partner partner(Long id) {
    var p = db.get(Partner.class, id);
    if (!visible(p.departmentId, p.id, p.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
    return p;
  }

  private Movement movement(Long id) {
    var m = db.get(Movement.class, id);
    if (!visible(m.departmentId, m.partnerId, m.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
    return m;
  }

  private Adjustment adjustment(Long id) {
    staff();
    var a = db.get(Adjustment.class, id);
    if (!visible(a.departmentId, null, a.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
    return a;
  }

  private BalanceStatement statement(Long id) {
    var s = db.get(BalanceStatement.class, id);
    if (!visible(s.departmentId, s.partnerId, s.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
    return s;
  }

  private Object poolSummary(Pool p) {
    return Map.of(
        "id",
        p.id,
        "reference",
        p.reference,
        "name",
        p.name,
        "kind",
        p.kind,
        "departmentId",
        p.departmentId,
        "enabled",
        p.enabled);
  }

  /** 伙伴表单可见同部门池的基本信息，不包含总池量或他方余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    var a = access.current();
    boolean staff = a.partnerId == null;
    return Map.of(
        "partners",
        db.all(Partner.class).stream()
            .filter(p -> visible(p.departmentId, p.id, p.createdBy))
            .toList(),
        "pools",
        db.all(Pool.class).stream()
            .filter(
                p ->
                    staff
                        ? visible(p.departmentId, null, p.createdBy)
                        : Objects.equals(p.departmentId, a.departmentId))
            .map(this::poolSummary)
            .toList(),
        "departments",
        staff
            ? db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList()
            : List.of(),
        "packagingTypes",
        db.all(DictionaryEntry.class).stream().filter(e -> e.type.equals("packaging")).toList(),
        "companyName",
        setting("companyName"),
        "timezone",
        setting("timezone"));
  }

  /** 主数据新增及版本化修改；部门、引用编号与器具类型不漂移。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveMaster(String type, Long id, MasterInput v) {
    lock();
    staff();
    access.require("catalog.write");
    String fp = fingerprint(type + "_SAVE", id, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return type.equals("pools") ? pool(prior) : partner(prior);
    var d = db.get(Department.class, v.departmentId);
    access.department(d.id);
    String ref = text(v.reference, 80);
    Object result;
    if (type.equals("pools")) {
      var p = id == null ? new Pool() : pool(id);
      if (id != null) {
        version(p.version, v.version);
        immutable(p.departmentId, d.id);
        immutable(p.reference, ref);
        immutable(p.kind, v.kind);
      }
      if (db.query(
              DictionaryEntry.class,
              "from DictionaryEntry where type=?1 and code=?2",
              "packaging",
              v.kind)
          .isEmpty()) throw new Problem(400, "INVALID_KIND");
      p.reference = ref;
      p.name = text(v.name, 120);
      p.kind = v.kind;
      p.departmentId = d.id;
      p.enabled = Boolean.TRUE.equals(v.enabled);
      if (id == null) {
        p.createdBy = access.current().id;
        capacity(Pool.class);
        db.save(p);
      }
      p.version++;
      result = p;
      BalancePolicy.verify(p);
    } else if (type.equals("partners")) {
      var p = id == null ? new Partner() : partner(id);
      if (id != null) {
        version(p.version, v.version);
        immutable(p.departmentId, d.id);
        immutable(p.reference, ref);
      }
      p.reference = ref;
      p.name = text(v.name, 120);
      p.departmentId = d.id;
      p.enabled = Boolean.TRUE.equals(v.enabled);
      if (id == null) {
        p.createdBy = access.current().id;
        capacity(Partner.class);
        db.save(p);
      }
      p.version++;
      result = p;
    } else throw new Problem(404, "NOT_FOUND");
    Long resultId = type.equals("pools") ? ((Pool) result).id : ((Partner) result).id;
    event(type, resultId, "SAVE", "", result, d.id);
    remember(v.requestKey, fp, resultId);
    return result;
  }

  /** 删除未引用主数据，历史引用由数据库外键保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteMaster(String type, Long id) {
    lock();
    staff();
    access.require("catalog.write");
    Long department;
    Object row;
    if (type.equals("pools")) {
      var p = pool(id);
      department = p.departmentId;
      row = p;
    } else if (type.equals("partners")) {
      var p = partner(id);
      department = p.departmentId;
      row = p;
    } else throw new Problem(404, "NOT_FOUND");
    db.delete(row);
    access.audit(type + "_DELETE", id, department);
  }

  /** 当前范围列表按白名单筛选排序与分页，不返回绑定伙伴的全池余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(
      String type, String search, String status, String kind, int page, int size, String sort) {
    List<?> rows;
    switch (type) {
      case "pools" -> {
        staff();
        access.require("pool.read");
        rows =
            db.all(Pool.class).stream()
                .filter(p -> visible(p.departmentId, null, p.createdBy))
                .filter(p -> contains(search, p.reference, p.name))
                .filter(p -> kind.isBlank() || p.kind.equals(kind))
                .filter(p -> status.isBlank() || Boolean.toString(p.enabled).equals(status))
                .toList();
      }
      case "partners" -> {
        staff();
        access.require("catalog.write");
        rows =
            db.all(Partner.class).stream()
                .filter(p -> visible(p.departmentId, p.id, p.createdBy))
                .filter(p -> contains(search, p.reference, p.name))
                .filter(p -> status.isBlank() || Boolean.toString(p.enabled).equals(status))
                .toList();
      }
      case "movements" -> {
        access.require("movement.read");
        rows =
            visibleMovements().stream()
                .filter(
                    m ->
                        contains(
                            search,
                            m.reference,
                            db.get(Pool.class, m.poolId).reference,
                            db.get(Partner.class, m.partnerId).name))
                .filter(m -> status.isBlank() || m.status.equals(status))
                .filter(m -> kind.isBlank() || m.kind.equals(kind))
                .toList();
      }
      case "adjustments" -> {
        staff();
        access.require("adjustment.read");
        rows =
            db.all(Adjustment.class).stream()
                .filter(a -> visible(a.departmentId, null, a.createdBy))
                .filter(a -> contains(search, a.reference))
                .filter(a -> status.isBlank() || a.status.equals(status))
                .filter(a -> kind.isBlank() || a.kind.equals(kind))
                .toList();
      }
      case "statements" -> {
        access.require("statement.read");
        rows =
            db.all(BalanceStatement.class).stream()
                .filter(s -> visible(s.departmentId, s.partnerId, s.createdBy))
                .filter(s -> contains(search, s.reference, db.get(Partner.class, s.partnerId).name))
                .filter(s -> status.isBlank() || s.status.equals(status))
                .toList();
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    return page(rows, page, size, sort);
  }

  /** 完整证据详情；伙伴仅获得自己单据、基本器具信息及对应流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(String type, Long id) {
    Object object;
    Long poolId = null, partnerId = null;
    String eventType;
    switch (type) {
      case "pools" -> {
        staff();
        access.require("pool.read");
        var p = pool(id);
        return Map.of(
            "pool",
            p,
            "balances",
            db.query(PartnerBalance.class, "from PartnerBalance where poolId=?1", id),
            "ledger",
            db.query(LedgerEntry.class, "from LedgerEntry where poolId=?1 order by id", id));
      }
      case "movements" -> {
        access.require("movement.read");
        var m = movement(id);
        object = m;
        poolId = m.poolId;
        partnerId = m.partnerId;
        eventType = "MOVEMENT";
      }
      case "adjustments" -> {
        staff();
        access.require("adjustment.read");
        var a = adjustment(id);
        object = a;
        poolId = a.poolId;
        eventType = "ADJUSTMENT";
      }
      case "statements" -> {
        access.require("statement.read");
        var s = statement(id);
        object = s;
        poolId = s.poolId;
        partnerId = s.partnerId;
        eventType = "STATEMENT";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("record", object);
    result.put("pool", poolSummary(db.get(Pool.class, poolId)));
    if (partnerId != null) {
      result.put("partner", db.get(Partner.class, partnerId));
      result.put("balance", readBalance(poolId, partnerId));
    }
    result.put(
        "events",
        db.query(
            BusinessEvent.class,
            "from BusinessEvent where objectType=?1 and objectId=?2 order by id",
            eventType,
            id));
    result.put(
        "ledger",
        db.query(
            LedgerEntry.class,
            "from LedgerEntry where objectType=?1 and objectId=?2 order by id",
            eventType,
            id));
    return result;
  }

  /** 草稿不占器具；借出只允许启用池和伙伴，归还允许收回停用资产。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveMovement(Long id, MovementInput v) {
    lock();
    access.require("movement.write");
    String fp = fingerprint("MOVEMENT_SAVE", id, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return detail("movements", prior);
    var m = id == null ? new Movement() : movement(id);
    if (id != null) {
      version(m.version, v.version);
      state(m.status, "DRAFT");
    }
    var p = db.get(Pool.class, v.poolId);
    var c = partner(v.partnerId);
    if (!Objects.equals(p.departmentId, c.departmentId)) throw new Problem(400, "INVALID_PARTNER");
    if (access.current().partnerId == null) pool(p.id);
    else if (!Objects.equals(access.current().departmentId, p.departmentId))
      throw new Problem(403, "OUT_OF_SCOPE");
    String kind = choice(v.kind, "ISSUE", "RETURN");
    if (id != null) immutable(m.kind, kind);
    if (kind.equals("ISSUE") && (!p.enabled || !c.enabled))
      throw new Problem(409, "DISABLED_RESOURCE");
    m.kind = kind;
    m.poolId = p.id;
    m.partnerId = c.id;
    m.departmentId = p.departmentId;
    m.reference = text(v.reference, 80);
    m.quantity = qty(v.quantity, 1);
    m.status = "DRAFT";
    if (id == null) {
      m.createdBy = access.current().id;
      capacity(Movement.class);
      db.save(m);
    }
    m.version++;
    event("MOVEMENT", m.id, "SAVE", "", m, m.departmentId);
    remember(v.requestKey, fp, m.id);
    assertSubledgers(p);
    return detail("movements", m.id);
  }

  /** 审核、预留、双边交接、短缺处理和质检一次事务执行，重试不重复过账。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object movementCommand(Long id, String action, Command v) {
    lock();
    String permission =
        switch (action) {
          case "submit", "cancel" -> "movement.write";
          case "approve", "reject" -> "issue.approve";
          case "dispatch" -> "dispatch";
          case "receive" -> "receive";
          case "resolve" -> "shortage.review";
          case "inspect" -> "quality.review";
          default -> throw new Problem(400, "INVALID_ACTION");
        };
    access.require(permission);
    var m = movement(id);
    String fp = fingerprint("MOVEMENT_" + action, id, v);
    if (retry(v.requestKey, fp) != null) return detail("movements", id);
    version(m.version, v.version);
    var p = db.get(Pool.class, m.poolId);
    var balance = balance(p.id, m.partnerId);
    String note = proof(v.note);
    switch (action) {
      case "submit" -> {
        state(m.status, "DRAFT");
        if (m.kind.equals("ISSUE")) {
          var partner = db.get(Partner.class, m.partnerId);
          if (!p.enabled || !partner.enabled) throw new Problem(409, "DISABLED_RESOURCE");
          m.status = "SUBMITTED";
        } else {
          if (balance.held < m.quantity) throw new Problem(409, "INSUFFICIENT_QUANTITY");
          transfer(p, m, "held", "returnReserved", m.quantity);
          balance.held -= m.quantity;
          balance.returnReserved += m.quantity;
          m.status = "RESERVED";
        }
      }
      case "approve" -> {
        staff();
        state(m.kind, "ISSUE");
        state(m.status, "SUBMITTED");
        independent(m.createdBy);
        if (!p.enabled || !db.get(Partner.class, m.partnerId).enabled)
          throw new Problem(409, "DISABLED_RESOURCE");
        transfer(p, m, "available", "reserved", m.quantity);
        m.approvedBy = access.current().id;
        m.status = "RESERVED";
      }
      case "reject" -> {
        staff();
        state(m.kind, "ISSUE");
        state(m.status, "SUBMITTED");
        independent(m.createdBy);
        m.status = "REJECTED";
        m.approvedBy = access.current().id;
      }
      case "cancel" -> {
        if (!Set.of("DRAFT", "SUBMITTED", "RESERVED").contains(m.status))
          throw new Problem(409, "INVALID_STATE");
        if (m.status.equals("RESERVED")) {
          if (m.kind.equals("ISSUE")) {
            staff();
            transfer(p, m, "reserved", "available", m.quantity);
          } else {
            transfer(p, m, "returnReserved", "held", m.quantity);
            balance.returnReserved -= m.quantity;
            balance.held += m.quantity;
          }
        }
        m.status = "CANCELLED";
      }
      case "dispatch" -> {
        staff();
        state(m.status, "RESERVED");
        if (m.kind.equals("ISSUE")) transfer(p, m, "reserved", "outTransit", m.quantity);
        else {
          transfer(p, m, "returnReserved", "returnTransit", m.quantity);
          balance.returnReserved -= m.quantity;
        }
        m.dispatchedBy = access.current().id;
        m.status = "DISPATCHED";
      }
      case "receive" -> {
        state(m.status, "DISPATCHED");
        independent(m.dispatchedBy);
        long n = qty(v.quantity, 0);
        if (n > m.quantity) throw new Problem(400, "INVALID_QUANTITY");
        if (m.kind.equals("ISSUE")) {
          transfer(p, m, "outTransit", "held", n);
          balance.held += n;
          m.status = n == m.quantity ? "CLOSED" : "DISPUTED";
        } else {
          staff();
          transfer(p, m, "returnTransit", "inspection", n);
          m.status = n == m.quantity ? "QC_PENDING" : "DISPUTED";
        }
        m.receiptQuantity = n;
        m.receivedBy = access.current().id;
      }
      case "resolve" -> {
        staff();
        state(m.status, "DISPUTED");
        independent(m.dispatchedBy);
        independent(m.receivedBy);
        independent(m.createdBy);
        long shortage = m.quantity - m.receiptQuantity;
        long lost = qty(v.lostQuantity, 0);
        if (m.kind.equals("ISSUE")) {
          long recovered = qty(v.recoveredQuantity, 0);
          if (recovered + lost != shortage) throw new Problem(400, "QUANTITY_MISMATCH");
          transfer(p, m, "outTransit", "held", recovered);
          balance.held += recovered;
          transfer(p, m, "outTransit", "lost", lost);
          m.recoveredQuantity = recovered;
          m.status = "CLOSED";
        } else {
          long back = qty(v.backToPartner, 0);
          if (back + lost != shortage) throw new Problem(400, "QUANTITY_MISMATCH");
          transfer(p, m, "returnTransit", "held", back);
          balance.held += back;
          transfer(p, m, "returnTransit", "lost", lost);
          m.backToPartner = back;
          m.status = m.receiptQuantity == 0 ? "CLOSED" : "QC_PENDING";
        }
        m.lostQuantity = lost;
        m.resolvedBy = access.current().id;
      }
      case "inspect" -> {
        staff();
        state(m.kind, "RETURN");
        state(m.status, "QC_PENDING");
        independent(m.receivedBy);
        long good = qty(v.goodQuantity, 0),
            repair = qty(v.repairQuantity, 0),
            retired = qty(v.retireQuantity, 0);
        if (good + repair + retired != m.receiptQuantity)
          throw new Problem(400, "QUANTITY_MISMATCH");
        transfer(p, m, "inspection", "available", good);
        transfer(p, m, "inspection", "repair", repair);
        transfer(p, m, "inspection", "retired", retired);
        m.goodQuantity = good;
        m.repairQuantity = repair;
        m.retireQuantity = retired;
        m.qcBy = access.current().id;
        m.status = "CLOSED";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    m.version++;
    assertSubledgers(p);
    event("MOVEMENT", id, action, note, m, m.departmentId);
    remember(v.requestKey, fp, id);
    return detail("movements", id);
  }

  /** 新增、维修与可用报废提案；草稿不修改余额，维修分类总量须匹配。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveAdjustment(Long id, AdjustmentInput v) {
    lock();
    staff();
    access.require("adjustment.write");
    String fp = fingerprint("ADJUSTMENT_SAVE", id, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return detail("adjustments", prior);
    var a = id == null ? new Adjustment() : adjustment(id);
    if (id != null) {
      version(a.version, v.version);
      state(a.status, "DRAFT");
    }
    var p = pool(v.poolId);
    a.kind = choice(v.kind, "ADD", "REPAIR", "RETIRE");
    a.quantity = qty(v.quantity, 1);
    a.goodQuantity =
        a.kind.equals("REPAIR") ? qty(v.goodQuantity, 0) : a.kind.equals("ADD") ? a.quantity : 0;
    a.retireQuantity =
        a.kind.equals("REPAIR")
            ? qty(v.retireQuantity, 0)
            : a.kind.equals("RETIRE") ? a.quantity : 0;
    if (a.goodQuantity + a.retireQuantity != a.quantity)
      throw new Problem(400, "QUANTITY_MISMATCH");
    a.reference = text(v.reference, 80);
    a.poolId = p.id;
    a.departmentId = p.departmentId;
    a.status = "DRAFT";
    if (id == null) {
      a.createdBy = access.current().id;
      capacity(Adjustment.class);
      db.save(a);
    }
    a.version++;
    event("ADJUSTMENT", a.id, "SAVE", "", a, a.departmentId);
    remember(v.requestKey, fp, a.id);
    return detail("adjustments", a.id);
  }

  /** 独立审核数量提案，余额与总量在审批时重新检查并追加流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object adjustmentCommand(Long id, String action, Command v) {
    lock();
    staff();
    access.require(
        Set.of("approve", "reject").contains(action) ? "adjustment.approve" : "adjustment.write");
    var a = adjustment(id);
    String fp = fingerprint("ADJUSTMENT_" + action, id, v);
    if (retry(v.requestKey, fp) != null) return detail("adjustments", id);
    version(a.version, v.version);
    String note = proof(v.note);
    var p = db.get(Pool.class, a.poolId);
    switch (action) {
      case "submit" -> {
        state(a.status, "DRAFT");
        a.status = "SUBMITTED";
      }
      case "cancel" -> {
        if (!Set.of("DRAFT", "SUBMITTED").contains(a.status))
          throw new Problem(409, "INVALID_STATE");
        a.status = "CANCELLED";
      }
      case "approve", "reject" -> {
        state(a.status, "SUBMITTED");
        independent(a.createdBy);
        if (action.equals("approve")) {
          if (a.kind.equals("ADD")) {
            if (!p.enabled) throw new Problem(409, "DISABLED_RESOURCE");
            transfer(p, null, "ADJUSTMENT", a.id, a.reference, "EXTERNAL", "available", a.quantity);
          } else if (a.kind.equals("RETIRE"))
            transfer(p, null, "ADJUSTMENT", a.id, a.reference, "available", "retired", a.quantity);
          else {
            if (p.repair < a.quantity) throw new Problem(409, "INSUFFICIENT_QUANTITY");
            transfer(
                p, null, "ADJUSTMENT", a.id, a.reference, "repair", "available", a.goodQuantity);
            transfer(
                p, null, "ADJUSTMENT", a.id, a.reference, "repair", "retired", a.retireQuantity);
          }
          a.status = "APPROVED";
        } else a.status = "REJECTED";
        a.approvedBy = access.current().id;
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    a.version++;
    assertSubledgers(p);
    event("ADJUSTMENT", id, action, note, a, a.departmentId);
    remember(v.requestKey, fp, id);
    return detail("adjustments", id);
  }

  /** 无未完成交接时冻结伙伴当前子账与其流水，多个开放对账被拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveStatement(StatementInput v) {
    lock();
    staff();
    access.require("statement.write");
    String fp = fingerprint("STATEMENT_SAVE", null, v);
    Long prior = retry(v.requestKey, fp);
    if (prior != null) return detail("statements", prior);
    var p = pool(v.poolId);
    var partner = partner(v.partnerId);
    if (!Objects.equals(p.departmentId, partner.departmentId))
      throw new Problem(400, "INVALID_PARTNER");
    settled(p.id, partner.id);
    if (db
        .query(
            BalanceStatement.class,
            "from BalanceStatement where poolId=?1 and partnerId=?2",
            p.id,
            partner.id)
        .stream()
        .anyMatch(s -> Set.of("OPEN", "DISPUTED").contains(s.status)))
      throw new Problem(409, "OPEN_STATEMENT");
    var s = new BalanceStatement();
    s.reference = text(v.reference, 80);
    s.status = "OPEN";
    s.poolId = p.id;
    s.partnerId = partner.id;
    s.departmentId = p.departmentId;
    s.createdBy = access.current().id;
    s.createdAt = BusinessTime.now(clock);
    var b = readBalance(p.id, partner.id);
    s.held = ((Number) b.get("held")).longValue();
    s.cutoff = cutoff(p.id, partner.id);
    s.snapshot =
        mapper.writeValueAsString(
            Map.of(
                "pool",
                poolSummary(p),
                "partner",
                partner,
                "held",
                s.held,
                "cutoff",
                s.cutoff,
                "ledger",
                partnerLedger(p.id, partner.id)));
    s.version = 1;
    capacity(BalanceStatement.class);
    db.save(s);
    event("STATEMENT", s.id, "FREEZE", "", s, s.departmentId);
    remember(v.requestKey, fp, s.id);
    return detail("statements", s.id);
  }

  /** 伙伴确认或异议；确认须与当前流水一致并由不同于制单人的账号完成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object statementCommand(Long id, String action, Command v) {
    lock();
    access.require(action.equals("cancel") ? "statement.write" : "statement.confirm");
    var s = statement(id);
    String fp = fingerprint("STATEMENT_" + action, id, v);
    if (retry(v.requestKey, fp) != null) return detail("statements", id);
    version(s.version, v.version);
    String note = proof(v.note);
    switch (action) {
      case "confirm" -> {
        state(s.status, "OPEN");
        independent(s.createdBy);
        settled(s.poolId, s.partnerId);
        if (cutoff(s.poolId, s.partnerId) != s.cutoff) throw new Problem(409, "STALE_STATEMENT");
        s.status = "CONFIRMED";
        s.confirmedBy = access.current().id;
      }
      case "dispute" -> {
        state(s.status, "OPEN");
        independent(s.createdBy);
        s.status = "DISPUTED";
        s.confirmedBy = access.current().id;
      }
      case "cancel" -> {
        staff();
        if (!Set.of("OPEN", "DISPUTED").contains(s.status)) throw new Problem(409, "INVALID_STATE");
        s.status = "CANCELLED";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    s.version++;
    event("STATEMENT", id, action, note, s, s.departmentId);
    remember(v.requestKey, fp, id);
    return detail("statements", id);
  }

  /** 指标来自真实可见单据和余额；绑定伙伴不获得总池或其他伙伴统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var a = access.current();
    var result = new LinkedHashMap<String, Object>();
    var rows =
        access.role().permissions.contains("movement.read")
            ? visibleMovements()
            : List.<Movement>of();
    Map<String, Long> states = new TreeMap<>();
    rows.forEach(m -> states.merge(m.status, 1L, Long::sum));
    result.put("movements", rows.size());
    result.put("states", states);
    result.put("disputed", rows.stream().filter(m -> m.status.equals("DISPUTED")).count());
    if (a.partnerId != null) {
      var balances =
          db.all(PartnerBalance.class).stream()
              .filter(
                  b ->
                      Objects.equals(b.partnerId, a.partnerId)
                          && Objects.equals(
                              db.get(Pool.class, b.poolId).departmentId, a.departmentId))
              .toList();
      result.put("balances", balances);
      result.put("held", balances.stream().mapToLong(b -> b.held).sum());
      result.put("returnReserved", balances.stream().mapToLong(b -> b.returnReserved).sum());
    } else {
      var pools =
          access.role().permissions.contains("pool.read")
              ? db.all(Pool.class).stream()
                  .filter(p -> visible(p.departmentId, null, p.createdBy))
                  .toList()
              : List.<Pool>of();
      Map<String, Long> totals = new LinkedHashMap<>();
      for (String b : BalancePolicy.BUCKETS)
        totals.put(b, pools.stream().mapToLong(p -> BalancePolicy.value(p, b)).sum());
      result.put("totals", totals);
      result.put("total", pools.stream().mapToLong(p -> p.total).sum());
      result.put("pools", pools.size());
    }
    return result;
  }

  /** 每次过账末尾检查伙伴子账和业务在途/待检记录，与资产池相互印证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void assertSubledgers(Pool p) {
    BalancePolicy.verify(p);
    var balances = db.query(PartnerBalance.class, "from PartnerBalance where poolId=?1", p.id);
    long held = 0, returnReserved = 0;
    for (var b : balances) {
      if (b.held < 0 || b.returnReserved < 0) throw new Problem(409, "BALANCE_INVARIANT");
      held += b.held;
      returnReserved += b.returnReserved;
    }
    long reserved = 0,
        outTransit = 0,
        returnTransit = 0,
        inspection = 0,
        documentReturnReserved = 0;
    for (var m : db.query(Movement.class, "from Movement where poolId=?1", p.id)) {
      if (m.kind.equals("ISSUE")) {
        if (m.status.equals("RESERVED")) reserved += m.quantity;
        if (m.status.equals("DISPATCHED")) outTransit += m.quantity;
        if (m.status.equals("DISPUTED")) outTransit += m.quantity - m.receiptQuantity;
      } else {
        if (m.status.equals("RESERVED")) documentReturnReserved += m.quantity;
        if (m.status.equals("DISPATCHED")) returnTransit += m.quantity;
        if (m.status.equals("DISPUTED")) returnTransit += m.quantity - m.receiptQuantity;
        if (Set.of("DISPUTED", "QC_PENDING").contains(m.status)) inspection += m.receiptQuantity;
      }
    }
    if (held != p.held
        || returnReserved != p.returnReserved
        || returnReserved != documentReturnReserved
        || reserved != p.reserved
        || outTransit != p.outTransit
        || returnTransit != p.returnTransit
        || inspection != p.inspection) throw new Problem(409, "BALANCE_INVARIANT");
  }

  private List<Movement> visibleMovements() {
    return db.all(Movement.class).stream()
        .filter(m -> visible(m.departmentId, m.partnerId, m.createdBy))
        .toList();
  }

  private void settled(Long poolId, Long partnerId) {
    boolean pending =
        db
            .query(
                Movement.class, "from Movement where poolId=?1 and partnerId=?2", poolId, partnerId)
            .stream()
            .anyMatch(
                m ->
                    !Set.of("DRAFT", "SUBMITTED", "REJECTED", "CANCELLED", "CLOSED")
                        .contains(m.status));
    if (pending) throw new Problem(409, "PENDING_HANDOFF");
  }

  private List<LedgerEntry> partnerLedger(Long poolId, Long partnerId) {
    return db.query(
        LedgerEntry.class,
        "from LedgerEntry where poolId=?1 and partnerId=?2 order by id",
        poolId,
        partnerId);
  }

  private long cutoff(Long poolId, Long partnerId) {
    return partnerLedger(poolId, partnerId).stream().mapToLong(e -> e.id).max().orElse(0);
  }

  private Map<String, Object> readBalance(Long poolId, Long partnerId) {
    var rows =
        db.query(
            PartnerBalance.class,
            "from PartnerBalance where poolId=?1 and partnerId=?2",
            poolId,
            partnerId);
    return rows.isEmpty()
        ? Map.of("poolId", poolId, "partnerId", partnerId, "held", 0L, "returnReserved", 0L)
        : Map.of(
            "poolId",
            poolId,
            "partnerId",
            partnerId,
            "held",
            rows.getFirst().held,
            "returnReserved",
            rows.getFirst().returnReserved);
  }

  private PartnerBalance balance(Long poolId, Long partnerId) {
    var rows =
        db.query(
            PartnerBalance.class,
            "from PartnerBalance where poolId=?1 and partnerId=?2",
            poolId,
            partnerId);
    if (!rows.isEmpty()) return rows.getFirst();
    var b = new PartnerBalance();
    b.poolId = poolId;
    b.partnerId = partnerId;
    return db.save(b);
  }

  private void transfer(Pool p, Movement m, String from, String to, long n) {
    transfer(p, m.partnerId, "MOVEMENT", m.id, m.reference, from, to, n);
  }

  private void transfer(
      Pool p,
      Long partnerId,
      String type,
      Long objectId,
      String ref,
      String from,
      String to,
      long n) {
    BalancePolicy.move(p, from, to, n);
    if (n == 0) return;
    var e = new LedgerEntry();
    e.poolId = p.id;
    e.partnerId = partnerId;
    e.objectType = type;
    e.objectId = objectId;
    e.reference = ref;
    e.fromBucket = from;
    e.toBucket = to;
    e.quantity = n;
    e.actorId = access.current().id;
    e.createdAt = BusinessTime.now(clock);
    db.save(e);
    p.version++;
  }

  private String setting(String code) {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value;
  }

  private void capacity(Class<?> cls) {
    if (db.all(cls).size() >= Integer.parseInt(setting("maxMovements")))
      throw new Problem(409, "CAPACITY_LIMIT");
  }

  private long qty(Long n, int min) {
    if (n == null || n < min || n > 1000000) throw new Problem(400, "INVALID_QUANTITY");
    return n;
  }

  private String text(String s, int max) {
    return AdminService.text(s, max);
  }

  private String choice(String s, String... values) {
    if (s == null || !Arrays.asList(values).contains(s)) throw new Problem(400, "INVALID_KIND");
    return s;
  }

  private String proof(String s) {
    return text(s, 1000);
  }

  private void state(String actual, String required) {
    if (!required.equals(actual)) throw new Problem(409, "INVALID_STATE");
  }

  private void independent(Long who) {
    if (Objects.equals(who, access.current().id))
      throw new Problem(409, "INDEPENDENT_REVIEW_REQUIRED");
  }

  private void immutable(Object old, Object next) {
    if (!Objects.equals(old, next)) throw new Problem(409, "IMMUTABLE_IDENTITY");
  }

  private void version(long current, Long supplied) {
    if (supplied == null || current != supplied) throw new Problem(409, "STALE_VERSION");
  }

  private boolean contains(String search, String... values) {
    if (search.length() > 200) throw new Problem(400, "INVALID_INPUT");
    return Arrays.stream(values)
        .anyMatch(v -> v.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)));
  }

  private Object page(List<?> rows, int page, int size, String sort) {
    if (page < 0
        || page > 100000
        || size < 1
        || size > 100
        || !Set.of("newest", "reference").contains(sort)) throw new Problem(400, "INVALID_PAGE");
    var sorted =
        rows.stream()
            .sorted(
                (a, b) ->
                    sort.equals("reference")
                        ? reference(a).compareTo(reference(b))
                        : Long.compare(id(b), id(a)))
            .toList();
    int start = Math.min(sorted.size(), page * size);
    return Map.of(
        "items",
        sorted.subList(start, Math.min(sorted.size(), start + size)),
        "total",
        sorted.size(),
        "page",
        page,
        "size",
        size);
  }

  private String reference(Object a) {
    return switch (a) {
      case Pool p -> p.reference;
      case Partner p -> p.reference;
      case Movement m -> m.reference;
      case Adjustment x -> x.reference;
      case BalanceStatement s -> s.reference;
      default -> throw new IllegalStateException();
    };
  }

  private Long id(Object a) {
    return switch (a) {
      case Pool p -> p.id;
      case Partner p -> p.id;
      case Movement m -> m.id;
      case Adjustment x -> x.id;
      case BalanceStatement s -> s.id;
      default -> throw new IllegalStateException();
    };
  }

  private String fingerprint(String action, Long id, Object body) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (access.current().id
                              + "|"
                              + action
                              + "|"
                              + id
                              + "|"
                              + mapper.writeValueAsString(body))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Long retry(String key, String fp) {
    if (key == null
        || !key.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var rows =
        db.query(
            CommandRecord.class,
            "from CommandRecord where requestKey=?1",
            key.toLowerCase(Locale.ROOT));
    if (rows.isEmpty()) return null;
    if (!rows.getFirst().fingerprint.equals(fp)) throw new Problem(409, "REQUEST_KEY_REUSED");
    return rows.getFirst().resultId;
  }

  private void remember(String key, String fp, Long result) {
    var c = new CommandRecord();
    c.requestKey = key.toLowerCase(Locale.ROOT);
    c.fingerprint = fp;
    c.resultId = result;
    db.save(c);
  }

  private void event(
      String type, Long id, String action, String note, Object snapshot, Long department) {
    var e = new BusinessEvent();
    e.objectType = type;
    e.objectId = id;
    e.action = action;
    e.actorId = access.current().id;
    e.note = note;
    e.snapshot = mapper.writeValueAsString(snapshot);
    e.createdAt = BusinessTime.now(clock);
    db.save(e);
    access.audit(type + "_" + action, id, department);
  }
}
