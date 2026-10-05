// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 数量守恒、边界和原子失败的单元检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class BalancePolicyTest {
  @Test
  void emptyPoolStartsAtZero() {
    BalancePolicy.verify(new Pool());
  }

  @Test
  void externalAddsToTotal() {
    var p = new Pool();
    BalancePolicy.move(p, "EXTERNAL", "available", 100);
    assertEquals(100, p.total);
    assertEquals(100, p.available);
  }

  @Test
  void bucketTransfersConserve() {
    var p = new Pool();
    BalancePolicy.move(p, "EXTERNAL", "available", 20);
    for (var b : BalancePolicy.BUCKETS) {
      if (!b.equals("available")) {
        BalancePolicy.move(p, "available", b, 1);
        BalancePolicy.verify(p);
      }
    }
    assertEquals(11, p.available);
    assertEquals(20, p.total);
  }

  @Test
  void insufficientLeavesBothSidesUnchanged() {
    var p = new Pool();
    BalancePolicy.move(p, "EXTERNAL", "available", 5);
    assertThrows(Problem.class, () -> BalancePolicy.move(p, "available", "held", 6));
    assertEquals(5, p.available);
    assertEquals(0, p.held);
  }

  @Test
  void negativeAndTooLargeRejected() {
    var p = new Pool();
    assertThrows(Problem.class, () -> BalancePolicy.move(p, "EXTERNAL", "available", -1));
    assertThrows(Problem.class, () -> BalancePolicy.move(p, "EXTERNAL", "available", 1000001));
    assertEquals(0, p.total);
  }

  @Test
  void unknownAndSelfBucketsRejected() {
    var p = new Pool();
    assertThrows(Problem.class, () -> BalancePolicy.move(p, "EXTERNAL", "madeup", 1));
    assertThrows(Problem.class, () -> BalancePolicy.move(p, "held", "held", 1));
  }

  @Test
  void corruptedBalancesAreDetected() {
    var p = new Pool();
    p.total = 1;
    assertThrows(Problem.class, () -> BalancePolicy.verify(p));
    p.available = -1;
    assertThrows(Problem.class, () -> BalancePolicy.verify(p));
  }

  @Test
  void ceilingAndZeroPosting() {
    var p = new Pool();
    p.total = 100000000;
    p.available = p.total;
    assertThrows(Problem.class, () -> BalancePolicy.move(p, "EXTERNAL", "available", 1));
    assertEquals(100000000, p.total);
    BalancePolicy.move(p, "available", "held", 0);
    assertEquals(0, p.held);
  }
}
