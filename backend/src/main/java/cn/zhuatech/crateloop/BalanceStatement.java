// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import jakarta.persistence.*;
import java.time.Instant;

/** 冻结伙伴当前保管余额及其流水截止点，后续变动阻止旧快照确认。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "balance_statement")
public class BalanceStatement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", length = 80, nullable = false)
  public String reference;

  @Column(name = "status", length = 30, nullable = false)
  public String status;

  @Column(name = "pool_id", nullable = false)
  public Long poolId;

  @Column(name = "partner_id", nullable = false)
  public Long partnerId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "confirmed_by")
  public Long confirmedBy;

  @Column(name = "held", nullable = false)
  public long held;

  @Column(name = "cutoff", nullable = false)
  public long cutoff;

  @Column(name = "snapshot", columnDefinition = "longtext", nullable = false)
  public String snapshot;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "version", nullable = false)
  public long version;
}
