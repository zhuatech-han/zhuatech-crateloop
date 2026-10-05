// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库建立身份、岗位和器具类型，不伪造资产池或往来事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${crateloop.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 首次事务初始化注册权限、角色、导航和管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    String[][] permissions = {
      {"pool.read", "查看资产池"},
      {"catalog.write", "维护资产池和伙伴"},
      {"movement.read", "查看交接"},
      {"movement.write", "申请交接"},
      {"issue.approve", "独立批准借出"},
      {"dispatch", "发出与提走"},
      {"receive", "双边签收与接收"},
      {"shortage.review", "独立处理短缺"},
      {"quality.review", "独立归还质检"},
      {"adjustment.read", "查看数量提案"},
      {"adjustment.write", "提交新增维修报废提案"},
      {"adjustment.approve", "独立批准数量提案"},
      {"statement.read", "查看伙伴对账"},
      {"statement.write", "冻结和作废对账"},
      {"statement.confirm", "伙伴确认和异议"},
      {"dashboard", "数量统计"},
      {"export", "业务证据导出"},
      {"audit", "内部操作审计"},
      {"admin", "系统管理"}
    };
    Set<String> all = new HashSet<>();
    for (var row : permissions) {
      var p = new Permission();
      p.code = row[0];
      p.name = row[1];
      db.save(p);
      all.add(p.code);
    }
    var administrator = role("管理员", "ALL", all);
    role(
        "周转协调",
        "DEPARTMENT",
        Set.of(
            "pool.read",
            "catalog.write",
            "movement.read",
            "movement.write",
            "adjustment.read",
            "adjustment.write",
            "statement.read",
            "statement.write",
            "dashboard",
            "export",
            "audit"));
    role(
        "独立复核",
        "DEPARTMENT",
        Set.of(
            "pool.read",
            "movement.read",
            "issue.approve",
            "shortage.review",
            "quality.review",
            "adjustment.read",
            "adjustment.approve",
            "statement.read",
            "dashboard",
            "export",
            "audit"));
    role(
        "仓库交接",
        "DEPARTMENT",
        Set.of(
            "pool.read",
            "movement.read",
            "dispatch",
            "receive",
            "adjustment.read",
            "statement.read",
            "dashboard",
            "export",
            "audit"));
    role(
        "合作伙伴",
        "SELF",
        Set.of(
            "movement.read",
            "movement.write",
            "receive",
            "statement.read",
            "statement.confirm",
            "dashboard",
            "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.departmentId = d.id;
    a.roleId = administrator.id;
    a.enabled = true;
    a.passwordHash = encoder.encode(password);
    db.save(a);
    String[][] menus = {
      {"movements", "器具交接", "Handoffs", "movement.read"},
      {"pools", "资产池", "Pools", "pool.read"},
      {"partners", "合作伙伴", "Partners", "catalog.write"},
      {"adjustments", "数量提案", "Adjustments", "adjustment.read"},
      {"statements", "伙伴对账", "Statements", "statement.read"},
      {"dashboard", "数量统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "器具字典", "Packaging types", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "CrateLoop 周转器具", "maxMovements", "1000")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    String[][] kinds = {
      {"CRATE", "周转箱", "Crates"}, {"PALLET", "托盘", "Pallets"}, {"TOTE", "料箱", "Totes"}
    };
    for (var row : kinds) {
      var e = new DictionaryEntry();
      e.type = "packaging";
      e.code = row[0];
      e.name = row[1];
      e.nameEn = row[2];
      db.save(e);
    }
  }

  private AccessRole role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    return db.save(r);
  }
}
