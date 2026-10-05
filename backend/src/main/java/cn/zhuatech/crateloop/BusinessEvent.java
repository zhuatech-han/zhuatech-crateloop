// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import jakarta.persistence.*;
import java.time.*;

/**
 * 不可修改的周转器具流转证据与数量快照。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "business_event")
public class BusinessEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "object_type", nullable = false, length = 30)
  public String objectType;

  @Column(name = "object_id", nullable = false)
  public Long objectId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "action", nullable = false, length = 60)
  public String action;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "snapshot", nullable = false, columnDefinition = "longtext")
  public String snapshot;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
