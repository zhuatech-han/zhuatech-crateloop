// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import jakarta.persistence.*;

/** 组织自有数量型器具池；数量字段只通过审核及流水服务变动。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pool")
public class Pool {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", length = 80, nullable = false)
  public String reference;

  @Column(name = "name", length = 120, nullable = false)
  public String name;

  @Column(name = "kind", length = 60, nullable = false)
  public String kind;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "enabled", nullable = false)
  public boolean enabled;

  @Column(name = "version", nullable = false)
  public long version;

  @Column(name = "total", nullable = false)
  public long total;

  @Column(name = "available", nullable = false)
  public long available;

  @Column(name = "reserved", nullable = false)
  public long reserved;

  @Column(name = "out_transit", nullable = false)
  public long outTransit;

  @Column(name = "held", nullable = false)
  public long held;

  @Column(name = "return_reserved", nullable = false)
  public long returnReserved;

  @Column(name = "return_transit", nullable = false)
  public long returnTransit;

  @Column(name = "inspection", nullable = false)
  public long inspection;

  @Column(name = "repair", nullable = false)
  public long repair;

  @Column(name = "lost", nullable = false)
  public long lost;

  @Column(name = "retired", nullable = false)
  public long retired;
}
