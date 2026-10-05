// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import java.util.*;

/** 整数器具数量的守恒与桶间迁移；失败不保留部分赋值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class BalancePolicy {
  private BalancePolicy() {}

  public static final List<String> BUCKETS =
      List.of(
          "available",
          "reserved",
          "outTransit",
          "held",
          "returnReserved",
          "returnTransit",
          "inspection",
          "repair",
          "lost",
          "retired");

  /** 在相同资产池中迁移整数数量，外部新增唯一允许增加总量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void move(Pool pool, String from, String to, long quantity) {
    if (quantity < 0
        || quantity > 1000000
        || from.equals(to)
        || !BUCKETS.contains(to)
        || (!from.equals("EXTERNAL") && !BUCKETS.contains(from)))
      throw new Problem(400, "INVALID_QUANTITY");
    verify(pool);
    if (quantity == 0) return;
    long nextTotal = from.equals("EXTERNAL") ? Math.addExact(pool.total, quantity) : pool.total;
    if (nextTotal > 100000000) throw new Problem(409, "POOL_LIMIT");
    long current = from.equals("EXTERNAL") ? quantity : value(pool, from);
    if (current < quantity) throw new Problem(409, "INSUFFICIENT_QUANTITY");
    long destination = Math.addExact(value(pool, to), quantity);
    if (!from.equals("EXTERNAL")) assign(pool, from, current - quantity);
    assign(pool, to, destination);
    pool.total = nextTotal;
    verify(pool);
  }

  /** 保证资产总量等于全部状态桶之和，每桶不得为负。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void verify(Pool pool) {
    long sum = 0;
    for (String bucket : BUCKETS) {
      long n = value(pool, bucket);
      if (n < 0) throw new Problem(409, "BALANCE_INVARIANT");
      sum = Math.addExact(sum, n);
    }
    if (sum != pool.total) throw new Problem(409, "BALANCE_INVARIANT");
  }

  /** 返回代码定义的桶数量，客户端不能指定任意字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long value(Pool p, String b) {
    return switch (b) {
      case "available" -> p.available;
      case "reserved" -> p.reserved;
      case "outTransit" -> p.outTransit;
      case "held" -> p.held;
      case "returnReserved" -> p.returnReserved;
      case "returnTransit" -> p.returnTransit;
      case "inspection" -> p.inspection;
      case "repair" -> p.repair;
      case "lost" -> p.lost;
      case "retired" -> p.retired;
      default -> throw new Problem(400, "INVALID_BUCKET");
    };
  }

  private static void assign(Pool p, String b, long n) {
    switch (b) {
      case "available" -> p.available = n;
      case "reserved" -> p.reserved = n;
      case "outTransit" -> p.outTransit = n;
      case "held" -> p.held = n;
      case "returnReserved" -> p.returnReserved = n;
      case "returnTransit" -> p.returnTransit = n;
      case "inspection" -> p.inspection = n;
      case "repair" -> p.repair = n;
      case "lost" -> p.lost = n;
      case "retired" -> p.retired = n;
      default -> throw new Problem(400, "INVALID_BUCKET");
    }
  }
}
