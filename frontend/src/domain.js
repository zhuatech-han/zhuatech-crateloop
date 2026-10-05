// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 展示状态、池桶和数量字段；服务端独立校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const states = {
  DRAFT: ["草稿", "Draft"],
  SUBMITTED: ["待审批", "Submitted"],
  RESERVED: ["已预留", "Reserved"],
  DISPATCHED: ["交接在途", "In transit"],
  DISPUTED: ["差异待处理", "Disputed"],
  QC_PENDING: ["待质检", "Awaiting inspection"],
  CLOSED: ["已完成", "Closed"],
  APPROVED: ["已批准", "Approved"],
  REJECTED: ["已驳回", "Rejected"],
  CANCELLED: ["已作废", "Cancelled"],
  OPEN: ["待伙伴确认", "Awaiting partner"],
  CONFIRMED: ["已确认", "Confirmed"],
};
export const buckets = {
  available: ["可用", "Available"],
  reserved: ["发出预留", "Issue reserved"],
  outTransit: ["发出在途", "Outbound"],
  held: ["伙伴保管", "Partner custody"],
  returnReserved: ["归还预留", "Return reserved"],
  returnTransit: ["归还在途", "Return transit"],
  inspection: ["待检", "Inspection"],
  repair: ["维修", "Repair"],
  lost: ["遗失", "Lost"],
  retired: ["报废", "Retired"],
};
export const kinds = {
  ISSUE: ["借出", "Issue"],
  RETURN: ["归还", "Return"],
  ADD: ["增加资产", "Add assets"],
  REPAIR: ["维修释放", "Repair release"],
  RETIRE: ["可用报废", "Retire available"],
};
/** 按状态、权限、伙伴绑定及双人规则选择可操作按钮。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(type, r, me) {
  if (!r || !me) return [];
  const staff = !me.partnerId;
  const different = (...ids) => ids.every((id) => id !== me.id);
  let a = [];
  if (type === "movements") {
    if (r.status === "DRAFT")
      a = [
        ["submit", "movement.write"],
        ["cancel", "movement.write"],
      ];
    if (r.status === "SUBMITTED")
      a = [
        ["cancel", "movement.write"],
        ...(staff && different(r.createdBy)
          ? [
              ["approve", "issue.approve"],
              ["reject", "issue.approve"],
            ]
          : []),
      ];
    if (r.status === "RESERVED")
      a = [
        ...(staff || r.kind === "RETURN" ? [["cancel", "movement.write"]] : []),
        ...(staff ? [["dispatch", "dispatch"]] : []),
      ];
    if (
      r.status === "DISPATCHED" &&
      (staff || r.kind === "ISSUE") &&
      different(r.dispatchedBy)
    )
      a = [["receive", "receive"]];
    if (
      r.status === "DISPUTED" &&
      staff &&
      different(r.createdBy, r.dispatchedBy, r.receivedBy)
    )
      a = [["resolve", "shortage.review"]];
    if (r.status === "QC_PENDING" && staff && different(r.receivedBy))
      a = [["inspect", "quality.review"]];
  } else if (type === "adjustments" && staff) {
    if (r.status === "DRAFT")
      a = [
        ["submit", "adjustment.write"],
        ["cancel", "adjustment.write"],
      ];
    if (r.status === "SUBMITTED")
      a = [
        ["cancel", "adjustment.write"],
        ...(different(r.createdBy)
          ? [
              ["approve", "adjustment.approve"],
              ["reject", "adjustment.approve"],
            ]
          : []),
      ];
  } else if (type === "statements") {
    if (r.status === "OPEN")
      a = [
        ...(different(r.createdBy)
          ? [
              ["confirm", "statement.confirm"],
              ["dispute", "statement.confirm"],
            ]
          : []),
        ...(staff ? [["cancel", "statement.write"]] : []),
      ];
    if (r.status === "DISPUTED" && staff) a = [["cancel", "statement.write"]];
  }
  return a.filter(([, p]) => me.permissions.includes(p)).map(([x]) => x);
}
/** 数量不作取整；非整数留给后端拒绝，空选择为null。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function payload(form, fields) {
  return Object.fromEntries(
    fields.map(([k, , , type]) => [
      k,
      type === "integer" || type === "id"
        ? form[k] === "" || form[k] == null
          ? null
          : Number(form[k])
        : type === "boolean"
          ? Boolean(form[k])
          : type === "permissions"
            ? [...(form[k] || [])]
            : (form[k] ?? ""),
    ]),
  );
}
