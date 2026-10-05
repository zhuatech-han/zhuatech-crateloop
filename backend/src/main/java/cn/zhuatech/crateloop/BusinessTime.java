// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 数据库存储与首次响应统一微秒精度，避免重复请求出现纳秒差异。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class BusinessTime {
  private BusinessTime() {}

  /** 读取UTC业务时刻并匹配MySQL timestamp(6)精度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Instant now(Clock clock) {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
