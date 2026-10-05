// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import jakarta.persistence.*;

/** 单池单伙伴的借出或归还交接；保留签收、短缺和质检证据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "movement")
public class Movement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", length = 80, nullable = false)
  public String reference;

  @Column(name = "kind", length = 20, nullable = false)
  public String kind;

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

  @Column(name = "approved_by")
  public Long approvedBy;

  @Column(name = "dispatched_by")
  public Long dispatchedBy;

  @Column(name = "received_by")
  public Long receivedBy;

  @Column(name = "resolved_by")
  public Long resolvedBy;

  @Column(name = "qc_by")
  public Long qcBy;

  @Column(name = "quantity", nullable = false)
  public long quantity;

  @Column(name = "receipt_quantity", nullable = false)
  public long receiptQuantity;

  @Column(name = "recovered_quantity", nullable = false)
  public long recoveredQuantity;

  @Column(name = "lost_quantity", nullable = false)
  public long lostQuantity;

  @Column(name = "back_to_partner", nullable = false)
  public long backToPartner;

  @Column(name = "good_quantity", nullable = false)
  public long goodQuantity;

  @Column(name = "repair_quantity", nullable = false)
  public long repairQuantity;

  @Column(name = "retire_quantity", nullable = false)
  public long retireQuantity;

  @Column(name = "version", nullable = false)
  public long version;
}
