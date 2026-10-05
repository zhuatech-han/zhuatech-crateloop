// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 数量与身份表单输入；资产桶不接受直接覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const fields = {
  pools: [
    ["reference", "资产池编号", "Pool reference"],
    ["name", "资产池名称", "Pool name"],
    ["kind", "器具类型", "Packaging type", "select", "packagingTypes"],
    ["departmentId", "负责部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  partners: [
    ["reference", "伙伴编号", "Partner reference"],
    ["name", "伙伴名称", "Partner name"],
    ["departmentId", "负责部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  movements: [
    ["reference", "交接单号", "Handoff reference"],
    ["kind", "流转方向", "Direction", "select", "movementKinds"],
    ["poolId", "资产池", "Asset pool", "id", "pools"],
    ["partnerId", "往来伙伴", "Partner", "id", "partners"],
    ["quantity", "申请数量", "Requested quantity", "integer"],
  ],
  adjustments: [
    ["reference", "提案编号", "Proposal reference"],
    ["kind", "提案类型", "Proposal type", "select", "adjustmentKinds"],
    ["poolId", "资产池", "Asset pool", "id", "pools"],
    ["quantity", "提案数量", "Quantity", "integer"],
    ["goodQuantity", "维修后可用量", "Repair released", "integer"],
    ["retireQuantity", "维修后报废量", "Repair retired", "integer"],
  ],
  statements: [
    ["reference", "对账单编号", "Statement reference"],
    ["poolId", "资产池", "Asset pool", "id", "pools"],
    ["partnerId", "往来伙伴", "Partner", "id", "partners"],
  ],
  users: [
    ["username", "登录名", "Username"],
    ["displayName", "姓名", "Name"],
    [
      "password",
      "新密码（编辑时留空保留）",
      "New password (optional when editing)",
      "password",
    ],
    ["roleId", "角色", "Role", "id", "roles"],
    ["departmentId", "部门", "Department", "id", "departments"],
    [
      "partnerId",
      "绑定伙伴（内部人员留空）",
      "Partner binding (staff leave blank)",
      "id",
      "partners",
    ],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  roles: [
    ["name", "角色名称", "Role name"],
    ["scope", "数据范围", "Data scope", "select", "scope"],
    ["permissions", "接口权限", "API permissions", "permissions"],
  ],
  departments: [["name", "部门名称", "Department name"]],
  menus: [
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
    [
      "permissionCode",
      "所需权限",
      "Required permission",
      "select",
      "permissions",
    ],
    ["position", "排序", "Order", "integer"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  permissions: [["name", "权限说明", "Permission description"]],
  dictionaries: [
    ["type", "字典类型", "Dictionary type"],
    ["code", "编码", "Code"],
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
  ],
  settings: [["value", "参数值", "Value"]],
};

/** 按交接方向收集数量分类与必填证据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function commandFields(action, record) {
  const note = [
    "note",
    "凭据编号／核实说明",
    "Evidence reference / note",
    "textarea",
  ];
  if (action === "receive")
    return [["quantity", "实际接收量", "Actually received", "integer"], note];
  if (action === "resolve")
    return [
      record.kind === "ISSUE"
        ? [
            "recoveredQuantity",
            "已补签收量",
            "Recovered and acknowledged",
            "integer",
          ]
        : [
            "backToPartner",
            "仍在伙伴处数量",
            "Remaining with partner",
            "integer",
          ],
      ["lostQuantity", "核实遗失量", "Verified lost", "integer"],
      note,
    ];
  if (action === "inspect")
    return [
      ["goodQuantity", "验收可用量", "Accepted good", "integer"],
      ["repairQuantity", "转维修量", "To repair", "integer"],
      ["retireQuantity", "报废量", "Retired", "integer"],
      note,
    ];
  return [note];
}
