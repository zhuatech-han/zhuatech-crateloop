// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import jakarta.persistence.*;

/** 新增、维修释放或可用报废提案；独立审核后一次过账。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "adjustment")
public class Adjustment {
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

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "approved_by")
  public Long approvedBy;

  @Column(name = "quantity", nullable = false)
  public long quantity;

  @Column(name = "good_quantity", nullable = false)
  public long goodQuantity;

  @Column(name = "retire_quantity", nullable = false)
  public long retireQuantity;

  @Column(name = "version", nullable = false)
  public long version;
}
