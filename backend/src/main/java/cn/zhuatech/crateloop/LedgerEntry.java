// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import jakarta.persistence.*;
import java.time.Instant;

/** 不可更改的器具桶间调动流水；业务事务与数量变动同步提交。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "pool_id", nullable = false)
  public Long poolId;

  @Column(name = "partner_id")
  public Long partnerId;

  @Column(name = "object_type", length = 30, nullable = false)
  public String objectType;

  @Column(name = "object_id", nullable = false)
  public Long objectId;

  @Column(name = "reference", length = 80, nullable = false)
  public String reference;

  @Column(name = "from_bucket", length = 30, nullable = false)
  public String fromBucket;

  @Column(name = "to_bucket", length = 30, nullable = false)
  public String toBucket;

  @Column(name = "quantity", nullable = false)
  public long quantity;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
