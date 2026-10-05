// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import jakarta.persistence.*;

/** 池与伙伴的保管和归还预留子账；不接收客户端直接写入。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "partner_balance")
public class PartnerBalance {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "pool_id", nullable = false)
  public Long poolId;

  @Column(name = "partner_id", nullable = false)
  public Long partnerId;

  @Column(name = "held", nullable = false)
  public long held;

  @Column(name = "return_reserved", nullable = false)
  public long returnReserved;
}
