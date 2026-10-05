// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import jakarta.persistence.*;

/** 合作伙伴及部门归属，绑定账号只可读自己的保管与往来。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "partner")
public class Partner {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", length = 80, nullable = false)
  public String reference;

  @Column(name = "name", length = 120, nullable = false)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "enabled", nullable = false)
  public boolean enabled;

  @Column(name = "version", nullable = false)
  public long version;
}
