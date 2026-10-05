// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.crateloop;

import jakarta.persistence.*;
import java.time.*;

/** UUID业务命令的精确重试凭据；哈希绑定操作人和完整载荷。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "command_record")
public class CommandRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "request_key", nullable = false, length = 36)
  public String requestKey;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;

  @Column(name = "result_id", nullable = false)
  public Long resultId;
}
