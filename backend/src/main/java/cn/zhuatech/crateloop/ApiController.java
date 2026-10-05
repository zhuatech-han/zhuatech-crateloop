// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 资产目录、往来单、数量提案及对账的权限入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final PoolService pools;
  final AdminService admin;
  final AccessService access;
  final Store db;

  public ApiController(PoolService pools, AdminService admin, AccessService access, Store db) {
    this.pools = pools;
    this.admin = admin;
    this.access = access;
    this.db = db;
  }

  /** 范围内表单目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return pools.options();
  }

  /** 白名单业务分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:pools|partners|movements|adjustments|statements}")
  public Object list(
      @PathVariable String type,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String kind,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return pools.list(type, search, status, kind, page, size, sort);
  }

  /** 流水和状态证据详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:pools|movements|adjustments|statements}/{id}")
  public Object detail(@PathVariable String type, @PathVariable Long id) {
    return pools.detail(type, id);
  }

  /** 零数量主数据创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{type:pools|partners}")
  public Object master(@PathVariable String type, @RequestBody PoolService.MasterInput v) {
    return pools.saveMaster(type, null, v);
  }

  /** 主数据版本编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/{type:pools|partners}/{id}")
  public Object master(
      @PathVariable String type, @PathVariable Long id, @RequestBody PoolService.MasterInput v) {
    return pools.saveMaster(type, id, v);
  }

  /** 未引用主数据删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/{type:pools|partners}/{id}")
  public Object delete(@PathVariable String type, @PathVariable Long id) {
    pools.deleteMaster(type, id);
    return Map.of("ok", true);
  }

  /** 往来草稿创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/movements")
  public Object movement(@RequestBody PoolService.MovementInput v) {
    return pools.saveMovement(null, v);
  }

  /** 往来草稿编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/movements/{id}")
  public Object movement(@PathVariable Long id, @RequestBody PoolService.MovementInput v) {
    return pools.saveMovement(id, v);
  }

  /** 往来状态命令，作废保留历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/movements/{id}/commands/{action}")
  public Object movement(
      @PathVariable Long id, @PathVariable String action, @RequestBody PoolService.Command v) {
    return pools.movementCommand(id, action, v);
  }

  /** 新增、维修或报废提案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/adjustments")
  public Object adjustment(@RequestBody PoolService.AdjustmentInput v) {
    return pools.saveAdjustment(null, v);
  }

  /** 未提交提案编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/adjustments/{id}")
  public Object adjustment(@PathVariable Long id, @RequestBody PoolService.AdjustmentInput v) {
    return pools.saveAdjustment(id, v);
  }

  /** 独立提案审核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/adjustments/{id}/commands/{action}")
  public Object adjustment(
      @PathVariable Long id, @PathVariable String action, @RequestBody PoolService.Command v) {
    return pools.adjustmentCommand(id, action, v);
  }

  /** 冻结当前伙伴余额及完整流水快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/statements")
  public Object statement(@RequestBody PoolService.StatementInput v) {
    return pools.saveStatement(v);
  }

  /** 双方确认、异议或作废。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/statements/{id}/commands/{action}")
  public Object statement(
      @PathVariable Long id, @PathVariable String action, @RequestBody PoolService.Command v) {
    return pools.statementCommand(id, action, v);
  }

  /** 不带宣传载荷的范围内业务证据导出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:pools|movements|adjustments|statements}/{id}/report.json")
  @Transactional(readOnly = true)
  public ResponseEntity<Object> export(@PathVariable String type, @PathVariable Long id) {
    access.require("export");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + "-" + id + ".json")
        .body(pools.detail(type, id));
  }

  /** 实际可见数量与单据统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return pools.dashboard();
  }

  /** 范围内操作审计，SELF仅本人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    pools.staff();
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .limit(500)
        .toList();
  }

  /** 管理目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object admin(@PathVariable String type) {
    return admin.list(type);
  }

  /** 管理资源创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object admin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object admin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除保护最后管理员与历史引用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
